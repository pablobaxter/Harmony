@file:JvmName("Harmony")

package com.frybits.harmony

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonWriter
import com.frybits.harmony.core._InternalHarmonyLog
import com.frybits.harmony.core.harmonyFileObserver
import com.frybits.harmony.core.putHarmony
import com.frybits.harmony.core.readHarmony
import com.frybits.harmony.core.withFileLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/*
 *  Copyright 2020 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Harmony is a process-safe, thread-safe [SharedPreferences] implementation.
 *
 * For the most part, documentation from [SharedPreferences] is also true for Harmony, except the warning about multiple processes not being supported.
 * It's totally supported here.
 *
 * Parts of this code loosely replicates code in SharedPreferencesImpl.
 * Source code here: https://android.googlesource.com/platform/frameworks/base.git/+/master/core/java/android/app/SharedPreferencesImpl.java
 */
private class HarmonyImpl internal constructor(
    context: Context,
    private val prefsName: String
) : SharedPreferences {

    // Folder containing all harmony preference files
    // NOTE: This folder can only be observed on if it exists before observer is started
    private val harmonyPrefsFolder = File(context.harmonyPrefsFolder(), prefsName)

    // Data file
    private val harmonyPrefsFile = File(harmonyPrefsFolder, PREFS_DATA)

    // Transaction folder
    private val harmonyTransactionsFolder = File(context.harmonyPrefsFolder(), PREFS_TRANSACTIONS)

    // Lock file to prevent multiple processes from writing and reading to the data file
    private val harmonyPrefsLockFile = File(harmonyPrefsFolder, PREFS_DATA_LOCK)

    // Backup file
    private val harmonyPrefsBackupFile = File(harmonyPrefsFolder, PREFS_BACKUP)

    // Lock file to prevent manipulation of backup file while it is restored
    private val harmonyPrefsBackupLockFile = File(harmonyPrefsFolder, PREFS_BACKUP_LOCK)

    // Single thread dispatcher, to serialize any calls to read/write the prefs
    private val harmonySingleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // Scope for all coroutines launched from this Harmony object
    private val harmonyCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(LOG_TAG))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val mapReentrantReadWriteLock = ReentrantReadWriteLock()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFolder) { event, path ->
            if (event == FileObserver.CLOSE_WRITE) { // We only really care if this file has been closed to writing
                val isPrefsData = path?.endsWith(PREFS_DATA) ?: false
                if (isPrefsData) { // Ignore everything but the prefs data file
                    harmonyCoroutineScope.launch(Dispatchers.IO) {
                        loadFromDisk(true)
                    }
                }
            }
        }

    // In-memory map. Read and modified only under a reentrant lock
    private var harmonyMap: HashMap<String, Any?> = hashMapOf()

    // Pref change listener map
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        // This deferred job wil block any reads of the preferences until it is complete
        isLoadedDeferred = harmonyCoroutineScope.async(Dispatchers.IO) {
            commitToDisk() // Commit to disk any pending transactions
            loadFromDisk(false)

            // Start the file observer on the the prefs folder for this Harmony object
            harmonyFileObserver.startWatching()
        }
    }

    override fun getInt(key: String, defValue: Int): Int {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Int? ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Long? ?: defValue
    }

    // Due to the backing nature of this data, we store all numbers as longs. Float raw bits are stored when saved, and used for reading it back
    override fun getFloat(key: String, defValue: Float): Float {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Float? ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Boolean? ?: defValue
    }

    override fun getString(key: String, defValue: String?): String? {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as String? ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): Set<String>? {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        @Suppress("UNCHECKED_CAST")
        return obj as Set<String>? ?: defValues
    }

    override fun contains(key: String): Boolean {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return mapReentrantReadWriteLock.read { harmonyMap.containsKey(key) }
    }

    override fun getAll(): MutableMap<String, *> {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return mapReentrantReadWriteLock.read { harmonyMap.toMutableMap() }
    }

    override fun edit(): SharedPreferences.Editor {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return HarmonyEditor()
    }

    // This listener will also listen for changes that occur to the Harmony preference with the same name from other processes.
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mapReentrantReadWriteLock.write {
            listenerMap[listener] = CONTENT
        }
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mapReentrantReadWriteLock.write {
            listenerMap.remove(listener)
        }
    }

    private fun readHarmonyMapFromStream(prefsReader: Reader): Pair<String?, Map<String, Any?>> {
        return try {
            _InternalHarmonyLog.v(LOG_TAG, "File exists! Reading json...")
            JsonReader(prefsReader).readHarmony()
        } catch (e: IllegalStateException) {
            _InternalHarmonyLog.e(LOG_TAG, "IllegalStateException while reading data file", e)
            null to emptyMap()
        } catch (e: JSONException) {
            _InternalHarmonyLog.e(LOG_TAG, "JSONException while reading data file", e)
            null to emptyMap()
        }
    }

    private fun refreshMemory(name: String?, map: Map<String, Any?>, shouldNotifyListeners: Boolean) {
        if (prefsName == name) {
            var notifyListeners = false
            var keysModified: ArrayList<String>? = null
            var listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>? = null

            mapReentrantReadWriteLock.write {
                notifyListeners = shouldNotifyListeners && listenerMap.isNotEmpty()
                keysModified = if (notifyListeners) arrayListOf() else null
                listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                val oldMap = harmonyMap
                @Suppress("UNCHECKED_CAST")
                harmonyMap = (map as? HashMap<String, Any?>) ?: hashMapOf()
                if (harmonyMap.isNotEmpty()) {
                    harmonyMap.forEach { (k, v) ->
                        if (!oldMap.containsKey(k) || oldMap[k] != v) {
                            keysModified?.add(k)
                        }
                        oldMap.remove(k)
                    }
                    keysModified?.addAll(oldMap.keys)
                }
            }

            // The variable 'shouldNotifyListeners' is only true if this read is due to a file update
            if (notifyListeners) {
                requireNotNull(keysModified)
                harmonyCoroutineScope.launch(Dispatchers.Main) {
                    keysModified?.asReversed()?.forEach { key ->
                        listeners?.forEach { listener ->
                            listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                        }
                    }
                }
            }
        }
    }

    // Load from disk logic
    private fun loadFromDisk(shouldNotifyListeners: Boolean) {
        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
        }

        if (!harmonyPrefsLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony lock file does not exist! Creating...")
            harmonyPrefsLockFile.createNewFile()
        }

        if (!harmonyPrefsBackupLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony backup lock file does not exist! Creating...")
            harmonyPrefsBackupLockFile.createNewFile()
        }

        val (name: String?, map: Map<String, Any?>) = harmonyPrefsLockFile.withFileLock(true) {
            // This backup mechanism was inspired by the SharedPreferencesImpl source code
            // Check for backup file
            if (harmonyPrefsBackupFile.exists()) {
                _InternalHarmonyLog.d(LOG_TAG, "Backup exists!")
                // Exclusively lock the backup file
                harmonyPrefsBackupLockFile.withFileLock { // Because the data lock is reentrant, we need to do an exclusive lock on backup file here
                    if (harmonyPrefsBackupFile.exists()) { // Check again if file exists
                        harmonyPrefsFile.delete()
                        harmonyPrefsBackupFile.renameTo(harmonyPrefsFile)
                    }
                }
            }

            val pfd = ParcelFileDescriptor.open(harmonyPrefsFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY)
            return@withFileLock ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use {
                if (harmonyPrefsFile.length() > 0) {
                    return@use readHarmonyMapFromStream(it)
                } else {
                    _InternalHarmonyLog.v(LOG_TAG, "File doesn't exist!")
                    return@use null to emptyMap<String, Any?>()
                }
            }
        } ?: null to emptyMap()

        refreshMemory(name, map, shouldNotifyListeners)
    }

    // Write the transactions to a file
    private fun commitTransactionToDisk(transactionInFlight: HarmonyTransaction) {

        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
        }

        if (!harmonyPrefsLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony lock file does not exist! Creating...")
            harmonyPrefsLockFile.createNewFile()
        }

        if (!harmonyTransactionsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony transaction folder does not exist! Creating...")
            harmonyTransactionsFolder.mkdirs()
        }

        // The file lock is shared, as we're writing to unique files in the file system, not the same one.
        // We only expect this lock to pause the thread if the main preference file is being written to
        harmonyPrefsLockFile.withFileLock(true) {

            var fileCreated = false
            var transactionFile: File? = null
            var count = 0

            // Keep trying to create a new file
            while (!fileCreated && count++ < MAX_TRANSACTION_FILE_CREATE_RETRIES) {
                transactionFile = File(harmonyTransactionsFolder, UUID.randomUUID().toString())
                fileCreated = transactionFile.createNewFile()
            }

            // Transaction file could not be created
            if (!fileCreated || transactionFile == null) return@withFileLock

            try {
                transactionFile.outputStream().buffered().use { outputStream ->
                    transactionInFlight.commitTransactionToOutputStream(outputStream)
                    outputStream.flush()
                }
            } catch (e: IOException) {
                _InternalHarmonyLog.w(LOG_TAG, "Unable to write transaction", e)
            }
        }
    }

    // Function to commit all transactions to the prefs file
    private fun commitToDisk(): Boolean {
        var committedToDisk = false

        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
        }

        if (!harmonyPrefsLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony lock file does not exist! Creating...")
            @Suppress("BlockingMethodInNonBlockingContext")
            harmonyPrefsLockFile.createNewFile()
        }

        if (!harmonyTransactionsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony transaction folder does not exist! Creating...")
            harmonyTransactionsFolder.mkdirs()
        }

        // Lock the file for writes across all processes. This is an exclusive lock
        harmonyPrefsLockFile.withFileLock {
            // Sorted sequence of transactions, ordered by the commit time set in the Editor
            val transactionArray = harmonyTransactionsFolder.listFiles()?.let { files ->
                if (files.isEmpty()) {
                    committedToDisk = true
                    return@withFileLock
                } // This is a shortcut, in case all transactions were already committed.
                return@let files.map { f ->
                    harmonyCoroutineScope.async(Dispatchers.IO) {
                        return@async try {
                            f.inputStream().buffered().use { inputStream ->
                                HarmonyTransaction(inputStream)
                            }
                        } catch (e: IOException) {
                            _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction file", e)
                            null
                        }
                    }
                }
            } ?: run {
                committedToDisk = true
                return@withFileLock
            }

            val currentPrefsJob = harmonyCoroutineScope.async(Dispatchers.IO) {
                // Copy the current prefs file to memory
                val pfdReader =
                    if (harmonyPrefsBackupFile.exists()) { // Use the backup if it exists
                        ParcelFileDescriptor.open(
                            harmonyPrefsBackupFile,
                            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    } else { // Backup file should not typically exist. We should read prefs directly from source
                        ParcelFileDescriptor.open(
                            harmonyPrefsFile,
                            ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY
                        )
                    }

                // Get the current preferences
                val (_, currentPrefs) = ParcelFileDescriptor.AutoCloseInputStream(pfdReader)
                    .bufferedReader().use {
                    if (harmonyPrefsFile.length() > 0) {
                        return@use readHarmonyMapFromStream(it)
                    } else {
                        _InternalHarmonyLog.v(LOG_TAG, "File doesn't exist!")
                        return@use null to emptyMap<String, Any?>()
                    }
                }

                // Then we delete the file and create a backup
                if (!harmonyPrefsBackupFile.exists()) { // Back up file doesn't need to be locked, as the data lock already restricts concurrent modifications
                    // No backup file exists. Let's create one
                    if (!harmonyPrefsFile.renameTo(harmonyPrefsBackupFile)) {
                        return@async null // Couldn't create a backup!
                    }
                } else {
                    harmonyPrefsFile.delete()
                }

                return@async (currentPrefs as? HashMap<String, Any?>) ?: hashMapOf()
            }

            // Commit all the transactions to the current prefs source
            val mapToCommitToFile = runBlocking {
                @Suppress("UselessCallOnCollection")
                transactionArray.mapNotNull { it.await() }
                    .sortedBy { it.memoryCommitTime }
                    .forEach {
                        val currPrefs = currentPrefsJob.await() ?: return@runBlocking null
                        it.commitTransaction(currPrefs, null)
                    }
                return@runBlocking currentPrefsJob.await()
            } ?: return@withFileLock

            // Writing the map to the file
            val pfdWriter = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY
            )

            val prefsOutputStream = ParcelFileDescriptor.AutoCloseOutputStream(pfdWriter)
            try {
                try {
                    _InternalHarmonyLog.v(LOG_TAG, "Begin writing data to file...")
                    JsonWriter(prefsOutputStream.bufferedWriter())
                        .putHarmony(prefsName, mapToCommitToFile)
                        .flush()
                    _InternalHarmonyLog.v(LOG_TAG, "Finish writing data to file!")

                    // Write all changes to the physical storage
                    pfdWriter.fileDescriptor.sync()
                } finally {
                    _InternalHarmonyLog.d(LOG_TAG, "Releasing file lock and closing output stream")
                    prefsOutputStream.close()
                }

                // Delete committed transactions. These should not change with the file lock
                harmonyTransactionsFolder.listFiles()?.forEach { it.delete() }

                // We wrote to file. We can delete the backup
                harmonyPrefsBackupFile.delete()

                committedToDisk = true
                return@withFileLock
            } catch (e: IOException) {
                _InternalHarmonyLog.e(LOG_TAG, "commitToDisk got exception:", e)
            }

            // We should reach this if there was a failure writing to the prefs file.
            if (harmonyPrefsFile.exists()) {
                if (!harmonyPrefsFile.delete()) {
                    _InternalHarmonyLog.w(LOG_TAG, "Couldn't cleanup partially-written preference")
                }
            }
            return@withFileLock
        }

        return committedToDisk
    }

    private inner class HarmonyEditor : SharedPreferences.Editor {

        // Container for our current changes
        private var harmonyTransaction = HarmonyTransaction()

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, value)
                return this
            }
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, value)
                return this
            }
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, value)
                return this
            }
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, value)
                return this
            }
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, value)
                return this
            }
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.update(key, values?.toHashSet())
                return this
            }
        }

        override fun clear(): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.clear()
                return this
            }
        }

        override fun remove(key: String): SharedPreferences.Editor {
            synchronized(this) {
                harmonyTransaction.delete(key)
                return this
            }
        }

        override fun apply() {
            val transaction = commitToMemory()
            harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) { // Apply and commits should be run sequentially in the order received
                commitTransactionToDisk(transaction)
                launch(Dispatchers.IO) {
                    commitToDisk()
                }
            }
        }

        override fun commit(): Boolean {
            val transaction = commitToMemory()
            return runBlocking(harmonySingleThreadDispatcher) { // Apply and commits should be run sequentially in the order received
                commitTransactionToDisk(transaction)
                return@runBlocking withContext(Dispatchers.IO) {
                    return@withContext commitToDisk()
                }
            }
        }

        // Credit for most of this code goes to whomever wrote this "apply()" for the current (as of 5/20/2020) SharedPreferencesImpl source
        private fun commitToMemory(): HarmonyTransaction {
            mapReentrantReadWriteLock.write {
                var keysModified: ArrayList<String>? = null
                var listeners: HashSet<SharedPreferences.OnSharedPreferenceChangeListener>? = null
                val listenersNotEmpty = listenerMap.size > 0

                if (listenersNotEmpty) {
                    keysModified = arrayListOf()
                    listeners = HashSet(listenerMap.keys)
                }

                val transactionInFlight = synchronized(this@HarmonyEditor) {
                    val transaction = harmonyTransaction
                    transaction.memoryCommitTime = SystemClock.elapsedRealtime() // The current time this "apply()" was called
                    harmonyTransaction = HarmonyTransaction()
                    transaction.commitTransaction(harmonyMap, keysModified)
                    return@synchronized transaction
                }

                if (!keysModified.isNullOrEmpty() && !listeners.isNullOrEmpty())
                harmonyCoroutineScope.launch(Dispatchers.Main) {
                    keysModified.asReversed().forEach { key ->
                        listeners.forEach { listener ->
                            listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                        }
                    }
                }
                return transactionInFlight
            }
        }
    }
}

private class HarmonyTransaction {
    private sealed class Operation(val data: Any?) {
        class Update(data: Any): Operation(data)
        object Delete: Operation(null)
    }

    private val transactionMap: HashMap<String, Operation> = hashMapOf()
    private var cleared: Boolean
    var memoryCommitTime = 0L

    constructor() {
        cleared = false
    }

    constructor(inputStream: InputStream): this() {
        val dataInputStream = DataInputStream(inputStream)
        cleared = dataInputStream.readBoolean()
        memoryCommitTime = dataInputStream.readLong()
        var byte = dataInputStream.read()
        var operation: Operation?
        var key: String
        var data: Any?
        while (byte != -1) {
            key = dataInputStream.readUTF()
            data = when (dataInputStream.readByte()) {
                0.toByte() -> dataInputStream.readInt()
                1.toByte() -> dataInputStream.readLong()
                2.toByte() -> dataInputStream.readFloat()
                3.toByte() -> dataInputStream.readBoolean()
                4.toByte() -> dataInputStream.readUTF()
                5.toByte() -> {
                    val count = dataInputStream.readInt()
                    val set = hashSetOf<String>()
                    repeat(count) {
                        set.add(dataInputStream.readUTF())
                    }
                    set
                }
                else -> null
            }
            operation = when (byte) {
                0 -> data?.let { Operation.Update(data) }
                1 -> Operation.Delete
                else -> null
            }
            operation?.let { op ->
                transactionMap[key] = op
            }
            byte = dataInputStream.read()
        }
    }

    fun update(key: String, value: Any?) {
        transactionMap[key] = value?.let { Operation.Update(it) } ?: Operation.Delete
    }

    fun delete(key: String) {
        transactionMap[key] = Operation.Delete
    }

    fun clear() {
        cleared = true
    }

    fun commitTransaction(dataMap: HashMap<String, Any?>, modifiedKeys: ArrayList<String>?) {
        if (cleared) {
            if (dataMap.isNotEmpty()) {
                dataMap.clear()
            }
        }

        transactionMap.forEach { (k, v) ->
            if (v == Operation.Delete) {
                if (!dataMap.containsKey(k)) {
                    return@forEach
                }
                dataMap.remove(k)
            } else {
                if (dataMap.containsKey(k)) {
                    val existingVal = dataMap[k]
                    if (existingVal != null && existingVal == v.data) return@forEach
                }
                dataMap[k] = v.data
            }
            modifiedKeys?.add(k)
        }
    }

    fun commitTransactionToOutputStream(outputStream: OutputStream) {
        val dataOutputStream = DataOutputStream(outputStream)
        dataOutputStream.writeBoolean(cleared)
        dataOutputStream.writeLong(memoryCommitTime)
        transactionMap.forEach { (k, v) ->
            dataOutputStream.writeByte( // Write the transaction type
                when (v) {
                    is Operation.Update -> {
                        0
                    }
                    is Operation.Delete -> {
                        1
                    }
                }
            )
            dataOutputStream.writeUTF(k) // Write the key
            when (val d = v.data) { // Write the data
                is Int -> {
                    dataOutputStream.writeByte(0)
                    dataOutputStream.writeInt(d)
                }
                is Long -> {
                    dataOutputStream.writeByte(1)
                    dataOutputStream.writeLong(d)
                }
                is Float -> {
                    dataOutputStream.writeByte(2)
                    dataOutputStream.writeFloat(d)
                }
                is Boolean -> {
                    dataOutputStream.writeByte(3)
                    dataOutputStream.writeBoolean(d)
                }
                is String -> {
                    dataOutputStream.writeByte(4)
                    dataOutputStream.writeUTF(d)
                }
                is Set<*> -> {
                    dataOutputStream.writeByte(5)
                    dataOutputStream.writeInt(d.size)
                    @Suppress("UNCHECKED_CAST")
                    val set = d as? Set<String>
                    set?.forEach { s ->
                        dataOutputStream.writeUTF(s)
                    }
                }
                null -> dataOutputStream.writeByte(6)
            }
        }
    }
}

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"

private fun Context.harmonyPrefsFolder() = File(filesDir, HARMONY_PREFS_FOLDER)

private val posixRegex = "[^-_.A-Za-z0-9]".toRegex()
private const val LOG_TAG = "Harmony"

private const val PREFS_DATA = "prefs.data"
private const val PREFS_TRANSACTIONS = "prefs_transactions"
private const val PREFS_DATA_LOCK = "prefs.data.lock"
private const val PREFS_BACKUP = "prefs.backup"
private const val PREFS_BACKUP_LOCK = "prefs.backup.lock"
private const val MAX_TRANSACTION_FILE_CREATE_RETRIES = 100

// Empty singleton to support WeakHashmap
private object CONTENT

private object SingletonLockObj

private val SINGLETON_MAP = hashMapOf<String, HarmonyImpl>()

/**
 * Main entry to get Harmony Preferences
 *
 * This creates and holds a single instance of a Harmony object in memory for each unique string provided.
 * This method is thread-safe. This method is also process-safe, meaning that if the same name is given to this method
 * from different processes, the same content is returned, and changes in from one process will be reflected in the other.
 *
 * @receiver Any valid context
 * @param name The desired preference file
 *
 * @return A [SharedPreferences] object backed by Harmony
 */
@JvmName("getSharedPreferences")
fun Context.getHarmonySharedPreferences(name: String): SharedPreferences {
    return SINGLETON_MAP[name] ?: synchronized(SingletonLockObj) {
        SINGLETON_MAP.getOrPut(name) {
            HarmonyImpl(applicationContext, name)
        }
    }
}
