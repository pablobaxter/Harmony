@file:JvmName("Harmony")

package com.frybits.harmony

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonWriter
import androidx.annotation.GuardedBy
import androidx.annotation.VisibleForTesting
import com.frybits.harmony.internal._InternalHarmonyLog
import com.frybits.harmony.internal.harmonyFileObserver
import com.frybits.harmony.internal.putHarmony
import com.frybits.harmony.internal.readHarmony
import com.frybits.harmony.internal.withFileLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONException
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.RandomAccessFile
import java.io.Reader
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import java.util.zip.Adler32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream
import kotlin.concurrent.read
import kotlin.concurrent.thread
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

private class HarmonyImpl constructor(
    context: Context,
    private val prefsName: String,
    private val transactionMaxSize: Long
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
    private val harmonySingleThreadDispatcher = Executors.newSingleThreadExecutor {
        thread(
            start = false,
            isDaemon = false,
            name = "$LOG_TAG-$prefsName",
            priority = Thread.NORM_PRIORITY
        ) { it.run() }
    }.asCoroutineDispatcher()

    // Scope for all coroutines launched from this Harmony object
    private val harmonyCoroutineScope =
        CoroutineScope(SupervisorJob() + CoroutineName("$LOG_TAG-$prefsName"))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val mapReentrantReadWriteLock = ReentrantReadWriteLock()

    // In-memory copy of the last read transactions in the transaction file. Should be cleared when transaction is cleared.
    private val lastReadTransactions = hashSetOf<HarmonyTransaction>()

    // The last read position of the transaction file. Prevents having to read the entire file each update
    private var lastTransactionPosition = 0L

    // The current job spun from the file observer. Meant only for the transaction updates, and cancelled for any other.
    private var currentJob: Job = Job()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFolder, FileObserver.CLOSE_WRITE or FileObserver.DELETE) { event, path ->
            if (path.isNullOrBlank()) return@harmonyFileObserver
            if (event == FileObserver.CLOSE_WRITE) {
                if (path.endsWith(PREFS_TRANSACTIONS)) {
                    currentJob.cancel() // Don't keep a queue of all transaction updates
                    currentJob = harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        handleTransactionUpdate()
                    }
                } else if (path.endsWith(PREFS_DATA)) {
                    currentJob.cancel() // Cancel any transaction update, as an update to the master supersedes it
                    harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        handleMasterUpdateWithFileLock()
                    }
                }
            } else if (event == FileObserver.DELETE && path.endsWith(PREFS_TRANSACTIONS)) {
                currentJob.cancel() // Ensure the transaction data is cleared when transaction is deleted
                harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                    lastReadTransactions.clear()
                    lastTransactionPosition = 0L
                }
            }
        }

    // In-memory map. Read and modified only under a reentrant lock
    @GuardedBy("mapReentrantReadWriteLock")
    private var harmonyMap: HashMap<String, Any?> = hashMapOf()

    // Last snapshot of master read in this process that all transactions will apply to
    private var masterSnapshot: HashMap<String, Any?> = hashMapOf()

    // In process transactions in-flight but not yet written to the file
    // This prevents losing changes done in this process
    @GuardedBy("mapReentrantReadWriteLock")
    private val transactionSet = hashSetOf<HarmonyTransaction>()

    // Preference change listener map
    @GuardedBy("mapReentrantReadWriteLock")
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
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

    // Helper function to handle exceptions from reading the master file
    private fun readHarmonyMapFromStream(prefsReader: Reader): Pair<String?, Map<String, Any?>> {
        return try {
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
        harmonyMasterLockFile.withFileLock masterLock@{

            // Load the transactions asynchronously
            val transactionListJob = harmonyCoroutineScope.async(Dispatchers.IO) {
                if (!harmonyTransactionsFile.createNewFile()) {
                    try {
                        return@async harmonyTransactionsFile.inputStream().buffered()
                            .use { HarmonyTransaction.generateHarmonyTransactions(it) }
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction during load")
                    }
                }
                return@async emptySet<HarmonyTransaction>() to false
            }

            // This backup mechanism was inspired by the SharedPreferencesImpl source code
            // Check for backup file
            if (harmonyMasterBackupFile.exists()) {
                harmonyMasterFile.delete()
                harmonyMasterBackupFile.renameTo(harmonyMasterFile)
            }

            // Get a snapshot of the master file
            if (!harmonyMasterFile.createNewFile()) {
                try {
                    // Get master file
                    val (_, map) = harmonyMasterFile.bufferedReader()
                        .use { readHarmonyMapFromStream(it) }
                    masterSnapshot = HashMap(map)
                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "Unable to read harmony master file on init", e)
                }
            }

            // Wait for the transactions job to finish if it hasn't yet
            val transactions = runBlocking { transactionListJob.await().first }

            // We want to commit any pending transactions to the master file on startup
            if (!transactions.isNullOrEmpty()) {
                transactions.sortedBy { it.memoryCommitTime }
                    .forEach { it.commitTransaction(masterSnapshot) }

                // We deleted the backup file earlier. Let's recreate it here as we are updating the master
                if (!harmonyMasterBackupFile.exists()) {
                    // No backup file exists. Let's create one
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
                            JsonWriter(masterOutputStream.bufferedWriter())
                                .putHarmony(prefsName, masterSnapshot)
                                .flush()
                            // Write all changes to the physical storage
                            masterWriter.fileDescriptor.sync()
                        }

                    // Clear all the transactions, as they are committed
                    harmonyTransactionsFile.delete()
                    harmonyTransactionsFile.createNewFile()
                    lastReadTransactions.clear()
                    lastTransactionPosition = 0L

                    // Delete the backup file
                    harmonyMasterBackupFile.delete()

                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "initialLoad() - commitToDisk got exception:", e)
                }
            }

            mapReentrantReadWriteLock.write {
                harmonyMap = HashMap(masterSnapshot)
            }
        }
    }

    private suspend fun handleTransactionUpdate() {
        checkForRequiredFiles()
        suspendCancellableCoroutine<Unit> { cont ->
            harmonyMasterLockFile.withFileLock masterLock@{
                if (cont.isCancelled) return@suspendCancellableCoroutine // Early out if this job was cancelled before the lock was obtained
                val (transactions, isCorrupted) = RandomAccessFile(harmonyTransactionsFile, "r").use { accessFile ->
                    if (accessFile.length() == 0L) { // Don't read the transaction file it it's empty
                        lastReadTransactions.clear()
                        lastTransactionPosition = 0L
                        return@use lastReadTransactions.toHashSet() to false
                    }
                    accessFile.seek(lastTransactionPosition) // Read from the last position
                    try {
                        val (readTransactions, isCorrupted) = FileInputStream(accessFile.fd).buffered()
                            .use { HarmonyTransaction.generateHarmonyTransactions(it) }
                        lastTransactionPosition =
                            accessFile.length() // Set the last position to the length of the data
                        lastReadTransactions.addAll(readTransactions) // Store all the read transactions to avoid re-reading them again
                        return@use lastReadTransactions.toHashSet() to isCorrupted
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transactions during update")
                        return@use emptySet<HarmonyTransaction>() to false
                    }
                }

                // The file was corrupted somehow. Commit everything to master and recreate the transaction file.
                if (isCorrupted) {
                    _InternalHarmonyLog.e(LOG_TAG, "Data was corrupted! Storing valid transactions to disk, and resetting.")
                    if (!commitTransactionsToMaster(null)) { // If nothing was committed, delete the transaction file anyways
                        harmonyTransactionsFile.delete()
                        harmonyTransactionsFile.createNewFile()
                        lastReadTransactions.clear()
                        lastTransactionPosition = 0L
                        // Clear the transaction set for this process too, as we have no guarantee it was written to disk
                        mapReentrantReadWriteLock.write { transactionSet.clear() }
                        // Re-update the map from the master snapshot immediately to resync the memory data.
                        // This is an optimization to avoid waiting for the file observer to trigger this call
                        handleMasterUpdate()
                    }
                    return@masterLock
                }

                mapReentrantReadWriteLock.write {
                    // Remove any transactions that were in flight for this process
                    transactionSet.removeAll(transactions)
                    val combinedTransactions = transactions + transactionSet

                    // Empty transactions, early exit
                    if (combinedTransactions.isEmpty()) return@masterLock

                    // Get a copy of the last known master snapshot
                    val masterCopy = HashMap(masterSnapshot)

                    // Commit all transactions to this snapshot
                    combinedTransactions.sortedBy { it.memoryCommitTime }
                        .forEach { it.commitTransaction(masterCopy) }

                    val notifyListeners = listenerMap.isNotEmpty()
                    val keysModified = if (notifyListeners) arrayListOf<String>() else null
                    val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                    // Identify any keys that were modified
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

                    if (notifyListeners) {
                        requireNotNull(keysModified)
                        harmonyCoroutineScope.launch(Dispatchers.Main) { // Listeners are notified on the main thread
                            keysModified.asReversed().forEach { key ->
                                listeners?.forEach { listener ->
                                    listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                                }
                            }
                        }
                    }
                }
            }
            // Resume this coroutine
            if (cont.isActive) cont.resume(Unit)
        }
    }

    // Helper function to wrap the master update function in a file lock
    private fun handleMasterUpdateWithFileLock() {
        checkForRequiredFiles()
        harmonyMasterLockFile.withFileLock {
            handleMasterUpdate()
        }
    }

    // This function should always be called in a file lock
    @GuardedBy("harmonyMasterLockFile")
    private fun handleMasterUpdate() {
        // Read the master file directly
        val (_, map) = try {
            harmonyMasterFile.bufferedReader()
                .use { readHarmonyMapFromStream(it) }
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "Unable to get master file.", e)
            return
        }

        mapReentrantReadWriteLock.write {
            // Update the master snapshot
            masterSnapshot = HashMap(map)

            // Create a copy of the master snapshot
            val masterCopy = HashMap(masterSnapshot)

            // All transactions should be applied to a copy of the master snapshot, not directly on it
            transactionSet.sortedBy { it.memoryCommitTime }
                .forEach { it.commitTransaction(masterCopy) }

            val notifyListeners = listenerMap.isNotEmpty()
            val keysModified = if (notifyListeners) arrayListOf<String>() else null
            val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

            // Identify any keys that were modified
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

            if (notifyListeners) {
                requireNotNull(keysModified)
                harmonyCoroutineScope.launch(Dispatchers.Main) { // Listeners are notified on the main thread
                    keysModified.asReversed().forEach { key ->
                        listeners?.forEach { listener ->
                            listener.onSharedPreferenceChanged(this@HarmonyImpl, key)
                        }
                    }
                }
            }
        }
    }

    // Write the transactions to a file, appended to the end of the file
    private fun commitTransactionToDisk(
        transactionInFlight: HarmonyTransaction,
        sync: Boolean = false
    ): Boolean {
        checkForRequiredFiles()
        harmonyMasterLockFile.withFileLock {
            // Skip writing to the transaction file, and go directly to master file update
            if (harmonyTransactionsFile.length() >= transactionMaxSize || sync) {
                return commitTransactionsToMaster(transactionInFlight)
            } else {
                // This should be the normal use case. This updates the file quickly, for replication to the other processes
                try {
                    FileOutputStream(harmonyTransactionsFile, true).buffered().use { outputStream ->
                        transactionInFlight.commitTransactionToOutputStream(outputStream)
                        outputStream.flush()
                    }
                } catch (e: IOException) {
                    _InternalHarmonyLog.w(LOG_TAG, "Unable to write transaction", e)
                }
            }
        }
        return false
    }

    // Function to commit all transactions to the master file
    @GuardedBy("harmonyMasterLockFile")
    private fun commitTransactionsToMaster(currentTransaction: HarmonyTransaction?): Boolean {
        val transactionList: Set<HarmonyTransaction> = try {
            RandomAccessFile(harmonyTransactionsFile, "r").use { accessFile ->
                accessFile.seek(lastTransactionPosition)
                val (readTransactions) = FileInputStream(accessFile.fd).buffered()
                    .use { inputStream ->
                        HarmonyTransaction.generateHarmonyTransactions(inputStream)
                    }
                return@use lastReadTransactions + readTransactions
            }
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction file", e)
            emptySet()
        }

        // Add the in-flight transaction to the list, if it exists
        val combinedTransactions =
            currentTransaction?.let { transactionList + it } ?: transactionList

        // Remove this current transaction as it won't be written to the transaction file
        mapReentrantReadWriteLock.write { transactionSet.remove(currentTransaction) }

        // Early exit if there is nothing to change
        if (combinedTransactions.isNullOrEmpty()) return false

        // Create a backup and delete the master file
        if (!harmonyMasterBackupFile.exists()) {
            if (!harmonyMasterFile.renameTo(harmonyMasterBackupFile)) {
                return false // Couldn't create a backup!
            }
        } else { // A back up exists! Let's keep it and just delete the master
            harmonyMasterFile.delete()
        }

        // Get the current preferences
        val (_, prefs) = try {
            harmonyMasterBackupFile.bufferedReader()
                .use { readHarmonyMapFromStream(it) }
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "")
            null to emptyMap<String, Any?>() // Make the master empty if there was an issue reading the master file
        }

        // Create a mutable copy
        val currentPrefs = HashMap(prefs)

        // Apply all transactions to the mutable copy
        combinedTransactions.sortedBy { it.memoryCommitTime }
            .forEach { it.commitTransaction(currentPrefs) }

        val masterWriter =
            ParcelFileDescriptor.open(harmonyMasterFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)

        try {
            ParcelFileDescriptor.AutoCloseOutputStream(masterWriter).use { masterOutputStream ->
                JsonWriter(masterOutputStream.bufferedWriter())
                    .putHarmony(prefsName, currentPrefs)
                    .flush()
                // Write all changes to the physical storage
                masterWriter.fileDescriptor.sync()
            }

            // Clear the transaction file
            harmonyTransactionsFile.delete()
            harmonyTransactionsFile.createNewFile()
            lastReadTransactions.clear()
            lastTransactionPosition = 0L

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

    // Internal Editor implementation
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
                commitTransactionToDisk(transaction, sync = true) // This will skip writing the transaction to the transaction file and update the master immediately
            }
        }

        private fun commitToMemory(): HarmonyTransaction {
            mapReentrantReadWriteLock.write {
                val notifyListeners = listenerMap.isNotEmpty()
                val keysModified = if (notifyListeners) arrayListOf<String>() else null
                val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                val transactionInFlight = synchronized(this@HarmonyEditor) {
                    val transaction = harmonyTransaction
                    transaction.memoryCommitTime =
                        SystemClock.elapsedRealtime() // The current time this "apply()" was called
                    transactionSet.add(transaction) // Add this to the in-flight transaction set for this process
                    harmonyTransaction =
                        HarmonyTransaction() // Generate a new transaction to prevent modifying one in-flight
                    transaction.commitTransaction(harmonyMap, keysModified) // Update the in-process map and get all modified keys
                    return@synchronized transaction
                }

                // Notify this process of changes immediately
                if (!keysModified.isNullOrEmpty() && !listeners.isNullOrEmpty())
                    harmonyCoroutineScope.launch(Dispatchers.Main) { // Listeners are notified on the main thread
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

// Internal transaction class
private class HarmonyTransaction(private val uuid: UUID = UUID.randomUUID()) { // Unique identifier to prevent transaction collision
    private sealed class Operation(val data: Any?) {
        class Update(data: Any) : Operation(data)
        object Delete : Operation(null)

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

    // All transaction operations
    private val transactionMap: HashMap<String, Operation> = hashMapOf()

    // Flag for cleared data
    private var cleared = false

    // Used to ensure that transactions are applied in the order received
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

    fun commitTransaction(
        dataMap: HashMap<String, Any?>,
        keysModified: MutableList<String>? = null
    ) {
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
        val checkSum = Adler32()
        val dataOutputStream = DataOutputStream(CheckedOutputStream(outputStream, checkSum))
        dataOutputStream.writeByte(Byte.MAX_VALUE.toInt())
        dataOutputStream.writeLong(uuid.mostSignificantBits)
        dataOutputStream.writeLong(uuid.leastSignificantBits)
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
        val checkSumValue = checkSum.value
        dataOutputStream.writeLong(checkSumValue)
    }

    override fun toString(): String {
        return "HarmonyTransaction(uuid=$uuid, transactionMap=$transactionMap, cleared=$cleared, memoryCommitTime=$memoryCommitTime)"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HarmonyTransaction) return false
        return uuid == other.uuid // The UUID is the only immutable variable, but maybe consider using the other variables as well?
    }

    override fun hashCode(): Int {
        return uuid.hashCode()
    }

    companion object {
        // Generates the list of transactions from the inputstream, with a boolean value to determine if the transaction failed the checksum validation
        fun generateHarmonyTransactions(inputStream: InputStream): Pair<Set<HarmonyTransaction>, Boolean> {
            val transactionList = hashSetOf<HarmonyTransaction>()
            val checksum = Adler32()
            val dataInputStream = DataInputStream(CheckedInputStream(inputStream, checksum))

            while (dataInputStream.read() != -1) {
                try {
                    val transaction =
                        HarmonyTransaction(UUID(dataInputStream.readLong(), dataInputStream.readLong())).apply {
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
                            6.toByte() -> null
                            else -> return transactionList to true
                        }
                        val operation = when (dataInputStream.readByte()) {
                            0.toByte() -> data?.let { Operation.Update(data) }
                            1.toByte() -> Operation.Delete
                            else -> null
                        }
                        operation?.let { op ->
                            transaction.transactionMap[key] = op
                        } ?: return transactionList to true
                    }
                    val checkSum = checksum.value
                    val expected = dataInputStream.readLong()
                    if (checkSum == expected) {
                        transactionList.add(transaction)
                    } else {
                        // Checksum validation failed! Assume the rest of the transaction file is corrupted and return early
                        return transactionList to true
                    }
                    checksum.reset()
                } catch (e: IOException) {
                    // This is expected if the transaction file write was not complete
                    _InternalHarmonyLog.e(LOG_TAG, "Unable to read current transaction in file", e)
                    return transactionList to true
                }
            }
            return transactionList to false
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

@VisibleForTesting
@JvmSynthetic
internal fun Context.getHarmonySharedPreferences(
    name: String,
    maxTransactionSize: Long
): SharedPreferences {
    return SINGLETON_MAP[name] ?: synchronized(SingletonLockObj) {
        SINGLETON_MAP.getOrPut(name) {
            HarmonyImpl(applicationContext, name, maxTransactionSize)
        }
    }
}

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
    // 128 KB is ~3k transactions with single operations.
    return getHarmonySharedPreferences(name, 128 * KILOBYTE)
}
