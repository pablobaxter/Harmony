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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
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
import kotlin.coroutines.resume

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
    private val prefsName: String,
    private val transactionMaxSize: Long = 128 * KILOBYTE // TODO make this adjustable by user
) : SharedPreferences {

    // Folder containing all harmony preference files
    // NOTE: This folder can only be observed on if it exists before observer is started
    private val harmonyPrefsFolder = File(context.harmonyPrefsFolder(), prefsName)

    // Master file file
    private val harmonyMasterFile = File(harmonyPrefsFolder, PREFS_DATA)

    // Lock file to prevent multiple processes from writing and reading to the data file
    private val harmonyMasterLockFile = File(harmonyPrefsFolder, PREFS_DATA_LOCK)

    // Transaction file
    private val harmonyTransactionsFile = File(harmonyPrefsFolder, PREFS_TRANSACTIONS)

    // Backup file
    private val harmonyMasterBackupFile = File(harmonyPrefsFolder, PREFS_BACKUP)

    // Single thread dispatcher, to serialize any calls to read/write the prefs
    private val harmonySingleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // Scope for all coroutines launched from this Harmony object
    private val harmonyCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(LOG_TAG))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val mapReentrantReadWriteLock = ReentrantReadWriteLock()

    // List of all transaction jobs in flight. If master file is changed, all transaction jobs are cancelled and cleared.
    private val jobQueue = arrayListOf<Job>()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFolder) { event, path ->
            if (path.isNullOrBlank()) return@harmonyFileObserver
            if (event == FileObserver.CLOSE_WRITE) { // We only really care if this file has been closed to writing
                if (path.endsWith(PREFS_TRANSACTIONS)) {
                    jobQueue.add(harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        if (!isActive) return@launch
                        handleTransactionUpdate()
                    })
                } else if (path.endsWith(PREFS_DATA)) {
                    jobQueue.removeAll {
                        it.cancel()
                        return@removeAll true
                    }
                    harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        handleMasterUpdate()
                    }
                }
            }
        }

    // In-memory map. Read and modified only under a reentrant lock
    private var harmonyMap: HashMap<String, Any?> = hashMapOf()

    // Current snapshot of master that all transactions will apply to
    private var masterSnapshot: HashMap<String, Any?> = hashMapOf()

    // Transactions in-flight but not yet written to the file
    private val transactionSet = hashSetOf<HarmonyTransaction>()

    // Pref change listener map
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
        _InternalHarmonyLog.d(LOG_TAG, "Initializing new harmony prefs: $prefsName")
        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        // This deferred job wil block any reads of the preferences until it is complete
        isLoadedDeferred = harmonyCoroutineScope.async(harmonySingleThreadDispatcher) {
            initialLoad()
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

    private fun checkForRequiredFiles() {
        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
        }

        if (!harmonyMasterLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony master lock file does not exist! Creating...")
            harmonyMasterLockFile.createNewFile()
        }
    }

    private fun readHarmonyMapFromStream(prefsReader: Reader): Pair<String?, Map<String, Any?>> {
        return try {
            _InternalHarmonyLog.d(LOG_TAG, "File exists! Reading json...")
            JsonReader(prefsReader).readHarmony()
        } catch (e: IllegalStateException) {
            _InternalHarmonyLog.e(LOG_TAG, "IllegalStateException while reading data file", e)
            null to emptyMap()
        } catch (e: JSONException) {
            _InternalHarmonyLog.e(LOG_TAG, "JSONException while reading data file", e)
            null to emptyMap()
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "IOException occurred while reading json", e)
            null to emptyMap()
        }
    }

    private fun initialLoad() {
        checkForRequiredFiles()
        _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - Attempting to get master file lock...")
        harmonyMasterLockFile.withFileLock masterLock@{
            _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - obtained master file lock")
            val transactionListJob = harmonyCoroutineScope.async(Dispatchers.IO) {
                if (!harmonyTransactionsFile.createNewFile()) {
                    try {
                        return@async harmonyTransactionsFile.inputStream().buffered()
                            .use { HarmonyTransaction.generateHarmonyTransactions(it) }
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction during load")
                    }
                }
                return@async emptySet<HarmonyTransaction>()
            }

            // This backup mechanism was inspired by the SharedPreferencesImpl source code
            // Check for backup file
            if (harmonyMasterBackupFile.exists()) {
                _InternalHarmonyLog.d(LOG_TAG, "Backup exists!")
                harmonyMasterFile.delete()
                harmonyMasterBackupFile.renameTo(harmonyMasterFile)
            }

            if (!harmonyMasterFile.createNewFile()) {
                // Get master file
                val (_, map) = harmonyMasterFile.bufferedReader().use { readHarmonyMapFromStream(it) } // TODO ensure map name matches file
                masterSnapshot = HashMap(map)
            }

            _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - masterSnapshot: $masterSnapshot")

            val transactions = runBlocking { transactionListJob.await() }
            _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - current transactions: $transactions")
            val masterCopy = HashMap(masterSnapshot)

            // We want to commit any pending transactions to the master file on startup
            if (!transactions.isNullOrEmpty()) {
                transactions.sortedBy { it.memoryCommitTime }
                    .forEach { it.commitTransaction(masterCopy) }

                if (!harmonyMasterBackupFile.exists()) { // Back up file doesn't need to be locked, as the data lock already restricts concurrent modifications
                    // No backup file exists. Let's create one
                    _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - Creating master backup file")
                    if (!harmonyMasterFile.renameTo(harmonyMasterBackupFile)) {
                        return // Couldn't create a backup!
                    }
                } else {
                    harmonyMasterFile.delete()
                }

                val masterWriter =
                    ParcelFileDescriptor.open(harmonyMasterFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)

                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(masterWriter)
                        .use { masterOutputStream ->
                            _InternalHarmonyLog.v(LOG_TAG, "initialLoad() - Begin writing data to master file...")
                            JsonWriter(masterOutputStream.bufferedWriter())
                                .putHarmony(prefsName, masterCopy)
                                .flush()
                            _InternalHarmonyLog.v(LOG_TAG, "initialLoad() - Finish writing data to master file!")
                            // Write all changes to the physical storage
                            masterWriter.fileDescriptor.sync()
                            _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - Finished syncing master file to disk")
                        }

                    _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - Clearing transaction file")
                    harmonyTransactionsFile.writer().close() // Clears the file without the need to delete/recreate

                    _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - Deleting master backup file")
                    // Delete the backup file
                    harmonyMasterBackupFile.delete()

                    _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - exiting master file lock")
                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "initialLoad() - commitToDisk got exception:", e)
                }
            }

            _InternalHarmonyLog.d(LOG_TAG, "initialLoad() - master after transactions: $masterCopy")
            mapReentrantReadWriteLock.write {
                harmonyMap = masterCopy
            }
        }
    }

    private suspend fun handleTransactionUpdate() {
        checkForRequiredFiles()
        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - Attempting to get master file lock...")
        suspendCancellableCoroutine<Unit> { continuation ->
            harmonyMasterLockFile.withFileLock(shared = true) masterLock@{
                // Remove any transactions that were in flight for this process
                mapReentrantReadWriteLock.write {
                    if (continuation.isCancelled) {
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - master file update came in!")
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - exiting master file lock")
                        return@suspendCancellableCoroutine
                    }
                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - obtained master file lock")
                    val transactions = try {
                        harmonyTransactionsFile.inputStream().buffered()
                            .use { HarmonyTransaction.generateHarmonyTransactions(it) }
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transactions during update")
                        emptySet<HarmonyTransaction>()
                    }

                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - current transactions: $transactions")

                    // Cancellation came in while reading transactions, early exit
                    if (continuation.isCancelled) {
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - master file update came in!")
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - exiting master file lock")
                        continuation.resume(Unit)
                        return@suspendCancellableCoroutine
                    }

                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - current in process transactions: $transactionSet")
                    transactionSet.removeAll(transactions)
                    val combinedTransactions = transactions + transactionSet

                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - combined transactions: $combinedTransactions")

                    // Empty transactions, early exit
                    if (combinedTransactions.isEmpty()) {
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - there are no transactions to write!")
                        _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - exiting master file lock")
                        continuation.resume(Unit)
                        return@suspendCancellableCoroutine
                    }

                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - masterSnapshot: $masterSnapshot")

                    val masterCopy = HashMap(masterSnapshot)
                    combinedTransactions.sortedBy { it.memoryCommitTime }
                        .forEach { it.commitTransaction(masterCopy) }

                    _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - master copy after transactions: $masterCopy")

                    val notifyListeners = listenerMap.isNotEmpty()
                    val keysModified = if (notifyListeners) arrayListOf<String>() else null
                    val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                    val oldMap = harmonyMap
                    harmonyMap = masterCopy
                    if (harmonyMap.isNotEmpty()) {
                        harmonyMap.forEach { (k, v) ->
                            if (!oldMap.containsKey(k) || oldMap[k] != v) {
                                keysModified?.add(k)
                            }
                            oldMap.remove(k)
                        }
                        keysModified?.addAll(oldMap.keys)
                    }

                    // The variable 'shouldNotifyListeners' is only true if this read is due to a file update
                    if (notifyListeners) {
                        requireNotNull(keysModified)
                        harmonyCoroutineScope.launch(Dispatchers.Main) {
                            keysModified.asReversed().forEach { key ->
                                listeners?.forEach { listener ->
                                    listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                                }
                            }
                        }
                    }
                }
                _InternalHarmonyLog.d(LOG_TAG, "handleTransactionUpdate() - exiting master file lock")
                continuation.resume(Unit)
            }
        }
    }

    private fun handleMasterUpdate() {
        checkForRequiredFiles()
        _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - Attempting to get master file lock...")
        harmonyMasterLockFile.withFileLock {
            mapReentrantReadWriteLock.write {
                _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - obtained master file lock")

                // This backup mechanism was inspired by the SharedPreferencesImpl source code
                // Check for backup file
                if (harmonyMasterBackupFile.exists()) {
                    _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - Backup exists!")
                    harmonyMasterFile.delete()
                    harmonyMasterBackupFile.renameTo(harmonyMasterFile)
                }

                if (!harmonyMasterFile.createNewFile()) {
                    // Get master file
                    val (_, map) = harmonyMasterFile.bufferedReader().use { readHarmonyMapFromStream(it) } // TODO ensure map name matches file
                    masterSnapshot = HashMap(map)
                }

                _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - current in process transactions: $transactionSet")

                _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - masterSnapshot: $masterSnapshot")

                val masterCopy = HashMap(masterSnapshot)
                transactionSet.sortedBy { it.memoryCommitTime }
                    .forEach { it.commitTransaction(masterCopy) }

                _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - master copy after transactions: $masterCopy")

                val notifyListeners = listenerMap.isNotEmpty()
                val keysModified = if (notifyListeners) arrayListOf<String>() else null
                val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                val oldMap = harmonyMap
                harmonyMap = HashMap(masterSnapshot)
                if (harmonyMap.isNotEmpty()) {
                    harmonyMap.forEach { (k, v) ->
                        if (!oldMap.containsKey(k) || oldMap[k] != v) {
                            keysModified?.add(k)
                        }
                        oldMap.remove(k)
                    }
                    keysModified?.addAll(oldMap.keys)
                }

                // The variable 'shouldNotifyListeners' is only true if this read is due to a file update
                if (notifyListeners) {
                    requireNotNull(keysModified)
                    harmonyCoroutineScope.launch(Dispatchers.Main) {
                        keysModified.asReversed().forEach { key ->
                            listeners?.forEach { listener ->
                                listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                            }
                        }
                    }
                }
            }
            _InternalHarmonyLog.d(LOG_TAG, "handleMasterUpdate() - exiting master file lock")
        }
    }

    // Write the transactions to a file, appended to the end of the file
    private fun commitTransactionToDisk(transactionInFlight: HarmonyTransaction, sync: Boolean = false): Boolean {
        checkForRequiredFiles()

        _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - Attempting to get master file lock...")
        harmonyMasterLockFile.withFileLock {
            _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - obtained master file lock")
            if (harmonyTransactionsFile.length() >= transactionMaxSize || sync) { // TODO We should also do a checksum of the transaction file, to ensure it is not corrupted
                _InternalHarmonyLog.d(LOG_TAG, "Committing master to the disk! sync = $sync, file size = ${harmonyTransactionsFile.length()}")
                val result = commitTransactionsToMaster(transactionInFlight)
                _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - exiting master file lock")
                return result
            } else {
                try {
                    _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - Writing transaction to file")
                    FileOutputStream(harmonyTransactionsFile, true).buffered().use { outputStream ->
                        transactionInFlight.commitTransactionToOutputStream(outputStream)
                        outputStream.flush()
                    }
                    _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - completed writing transaction file")
                } catch (e: IOException) {
                    _InternalHarmonyLog.w(LOG_TAG, "Unable to write transaction", e)
                }
            }
            _InternalHarmonyLog.d(LOG_TAG, "commitTransactionToDisk() - exiting master file lock")
        }
        return false
    }

    // Function to commit all transactions to the prefs file
    private fun commitTransactionsToMaster(currentTransaction: HarmonyTransaction): Boolean {
        val transactionList = try {
            harmonyTransactionsFile.inputStream().buffered().use { inputStream ->
                HarmonyTransaction.generateHarmonyTransactions(inputStream)
            }
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction file", e)
            emptySet<HarmonyTransaction>()
        } + currentTransaction

        _InternalHarmonyLog.d(LOG_TAG, "Committing transactions to master: $transactionList")

        // Remove this current transaction as it won't be written to the transaction file
        val removed = mapReentrantReadWriteLock.write { transactionSet.remove(currentTransaction) }

        _InternalHarmonyLog.d(LOG_TAG, "Removed current transaction from list? $removed")

        if (transactionList.isNullOrEmpty()) return false

        // Then we delete the file and create a backup
        if (!harmonyMasterBackupFile.exists()) { // Back up file doesn't need to be locked, as the data lock already restricts concurrent modifications
            // No backup file exists. Let's create one
            _InternalHarmonyLog.d(LOG_TAG, "Creating master backup file")
            if (!harmonyMasterFile.renameTo(harmonyMasterBackupFile)) {
                return false // Couldn't create a backup!
            }
        } else {
            harmonyMasterFile.delete()
        }

        // Get the current preferences
        val (_, prefs) = harmonyMasterBackupFile.bufferedReader().use { readHarmonyMapFromStream(it) }

        val currentPrefs = HashMap(prefs)

        _InternalHarmonyLog.d(LOG_TAG, "Current snapshot from disk (backup): $currentPrefs")

        transactionList.sortedBy { it.memoryCommitTime }.forEach { it.commitTransaction(currentPrefs) }

        _InternalHarmonyLog.d(LOG_TAG, "Prefs after committing all transactions: $currentPrefs")

        val masterWriter = ParcelFileDescriptor.open(harmonyMasterFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)

        try {
            ParcelFileDescriptor.AutoCloseOutputStream(masterWriter).use { masterOutputStream ->
                _InternalHarmonyLog.v(LOG_TAG, "Begin writing data to master file...")
                JsonWriter(masterOutputStream.bufferedWriter())
                    .putHarmony(prefsName, currentPrefs)
                    .flush()
                _InternalHarmonyLog.v(LOG_TAG, "Finish writing data to master file!")
                // Write all changes to the physical storage
                masterWriter.fileDescriptor.sync()
                _InternalHarmonyLog.d(LOG_TAG, "Finished syncing master file to disk")
            }

            _InternalHarmonyLog.d(LOG_TAG, "Clearing transaction file")
            harmonyTransactionsFile.writer().close() // Clears the file without the need to delete/recreate

            _InternalHarmonyLog.d(LOG_TAG, "Deleting master backup file")
            // Delete the backup file
            harmonyMasterBackupFile.delete()

            return true
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "commitToDisk got exception:", e)
        }

        // We should reach this if there was a failure writing to the prefs file.
        if (harmonyMasterFile.exists()) {
            if (!harmonyMasterFile.delete()) {
                _InternalHarmonyLog.w(LOG_TAG, "Couldn't cleanup partially-written preference")
            }
        }

        return false
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
            }
        }

        override fun commit(): Boolean {
            val transaction = commitToMemory()
            return runBlocking(harmonySingleThreadDispatcher) { // Apply and commits should be run sequentially in the order received
                commitTransactionToDisk(transaction, sync = true)
            }
        }

        private fun commitToMemory(): HarmonyTransaction {
            mapReentrantReadWriteLock.write {
                val notifyListeners = listenerMap.isNotEmpty()
                val keysModified = if (notifyListeners) arrayListOf<String>() else null
                val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                val transactionInFlight = synchronized(this@HarmonyEditor) {
                    _InternalHarmonyLog.d(LOG_TAG, "Applying transaction to harmony map: $harmonyTransaction")
                    val transaction = harmonyTransaction
                    transaction.memoryCommitTime = SystemClock.elapsedRealtime() // The current time this "apply()" was called
                    transactionSet.add(transaction)
                    _InternalHarmonyLog.d(LOG_TAG, "Added to transaction set. transactionSet=$transactionSet")
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

private class HarmonyTransaction(private val uuid: UUID = UUID.randomUUID()) {
    private sealed class Operation(val data: Any?) {
        class Update(data: Any): Operation(data)
        object Delete: Operation(null)

        override fun hashCode(): Int {
            return data.hashCode()
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Update) return false
            if (this is Update) return data == other.data
            return false
        }

        override fun toString(): String {
            return "${javaClass.simpleName}(data=$data)"
        }
    }

    private val transactionMap: HashMap<String, Operation> = hashMapOf()
    private var cleared = false
    var memoryCommitTime = 0L

    fun update(key: String, value: Any?) {
        transactionMap[key] = value?.let { Operation.Update(it) } ?: Operation.Delete
    }

    fun delete(key: String) {
        transactionMap[key] = Operation.Delete
    }

    fun clear() {
        cleared = true
    }

    fun commitTransaction(dataMap: HashMap<String, Any?>, keysModified: MutableList<String>? = null) {
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
            keysModified?.add(k)
        }
    }

    fun commitTransactionToOutputStream(outputStream: OutputStream) {
        val dataOutputStream = DataOutputStream(outputStream)
        dataOutputStream.writeByte(Byte.MAX_VALUE.toInt())
        dataOutputStream.writeUTF(uuid.toString())
        dataOutputStream.writeBoolean(cleared)
        dataOutputStream.writeLong(memoryCommitTime)
        transactionMap.forEach { (k, v) ->
            dataOutputStream.writeBoolean(true)
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

            dataOutputStream.writeByte( // Write the transaction type
                when (v) {
                    is Operation.Update -> 0
                    is Operation.Delete -> 1
                }
            )
        }
        dataOutputStream.writeBoolean(false)
    }

    override fun toString(): String {
        return "HarmonyTransaction(uuid=$uuid, transactionMap=$transactionMap, cleared=$cleared, memoryCommitTime=$memoryCommitTime)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HarmonyTransaction) return false
        return uuid == other.uuid
            && transactionMap == other.transactionMap
            && cleared == other.cleared
            && memoryCommitTime == other.memoryCommitTime
    }

    override fun hashCode(): Int {
        var result = uuid.hashCode()
        result = 31 * result + transactionMap.hashCode()
        result = 31 * result + cleared.hashCode()
        result = 31 * result + memoryCommitTime.hashCode()
        return result
    }

    companion object {
        fun generateHarmonyTransactions(inputStream: InputStream): Set<HarmonyTransaction> {
            val transactionList = hashSetOf<HarmonyTransaction>()
            val dataInputStream = DataInputStream(inputStream)

            while (dataInputStream.read() != -1) {
                try {
                    val transaction =
                        HarmonyTransaction(UUID.fromString(dataInputStream.readUTF())).apply {
                            cleared = dataInputStream.readBoolean()
                            memoryCommitTime = dataInputStream.readLong()
                        }

                    while (dataInputStream.readBoolean()) {
                        val key = dataInputStream.readUTF()
                        val data = when (dataInputStream.readByte()) {
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
                        val operation = when (dataInputStream.readByte()) {
                            0.toByte() -> data?.let { Operation.Update(data) }
                            1.toByte() -> Operation.Delete
                            else -> null
                        }
                        operation?.let { op ->
                            transaction.transactionMap[key] = op
                        } ?: continue
                    }
                    transactionList.add(transaction)
                } catch (e: IOException) {
                    // This is expected if the transaction file write was not complete
                    _InternalHarmonyLog.e(LOG_TAG, "Unable to read current transaction in file", e)
                    return transactionList
                }
            }
            return transactionList
        }
    }
}

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"

private fun Context.harmonyPrefsFolder() = File(filesDir, HARMONY_PREFS_FOLDER)

private val posixRegex = "[^-_.A-Za-z0-9]".toRegex()
private const val LOG_TAG = "Harmony"

private const val PREFS_DATA = "prefs.data"
private const val PREFS_TRANSACTIONS = "prefs.transaction.data"
private const val PREFS_DATA_LOCK = "prefs.data.lock"
private const val PREFS_BACKUP = "prefs.backup"
private const val KILOBYTE = 1024L * Byte.SIZE_BYTES

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
