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
import java.util.concurrent.LinkedBlockingQueue
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
 * https://github.com/pablobaxter/Harmony
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
    private val transactionMaxByteSize: Long,
    private val transactionMaxBatchCount: Int
) : SharedPreferences {

    // Folder containing all harmony preference files
    // NOTE: This folder can only be observed on if it exists before observer is started
    private val harmonyPrefsFolder = File(context.harmonyPrefsFolder(), prefsName).apply { if (!exists()) mkdirs() }

    // Main file file
    private val harmonyMainFile = File(harmonyPrefsFolder, PREFS_DATA)

    // Lock file to prevent multiple processes from writing and reading to the data file
    private val harmonyMainLockFile = File(harmonyPrefsFolder, PREFS_DATA_LOCK)

    // Transaction file
    private val harmonyTransactionsFile = File(harmonyPrefsFolder, PREFS_TRANSACTIONS)

    // Backup file
    private val harmonyMainBackupFile = File(harmonyPrefsFolder, PREFS_BACKUP)

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
    private val lastReadTransactions = sortedSetOf<HarmonyTransaction>()

    private var lastTransaction = EMPTY_TRANSACTION

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
                    currentJob.cancel() // Cancel any transaction update, as an update to the main supersedes it
                    harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        handleMainUpdateWithFileLock()
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

    // Last snapshot of main read in this process that all transactions will apply to
    private var mainSnapshot: HashMap<String, Any?> = hashMapOf()

    // In-process transactions in-flight but not yet written to the file
    // This prevents losing changes done in this process
    @GuardedBy("mapReentrantReadWriteLock")
    private val transactionSet = sortedSetOf<HarmonyTransaction>()

    // A queue for any transactions that are pending writes. This allows for batching of transaction writes
    private val transactionQueue = LinkedBlockingQueue<HarmonyTransaction>()

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
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Int? ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Long? ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Float? ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as Boolean? ?: defValue
    }

    override fun getString(key: String, defValue: String?): String? {
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return obj as String? ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): Set<String>? {
        awaitForLoad()
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        @Suppress("UNCHECKED_CAST")
        return obj as Set<String>? ?: defValues
    }

    override fun contains(key: String): Boolean {
        awaitForLoad()
        return mapReentrantReadWriteLock.read { harmonyMap.containsKey(key) }
    }

    override fun getAll(): MutableMap<String, *> {
        awaitForLoad()
        return mapReentrantReadWriteLock.read { harmonyMap.toMutableMap() }
    }

    override fun edit(): SharedPreferences.Editor {
        awaitForLoad()
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

    private fun awaitForLoad() {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
    }

    private fun checkForRequiredFiles() {
        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            if (!harmonyPrefsFolder.mkdirs()) throw IOException("Unable to create harmony prefs directories")
            harmonyMainLockFile.createNewFile() // Skip the extra check and create the file. It didn't exist before.
        } else if (!harmonyMainLockFile.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony main lock file does not exist! Creating...")
            harmonyMainLockFile.createNewFile()
        }
    }

    // Helper function to handle exceptions from reading the main file
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
        harmonyMainLockFile.withFileLock mainLock@{

            // Load the transactions asynchronously
            val transactionListJob = harmonyCoroutineScope.async(Dispatchers.IO) {
                if (!harmonyTransactionsFile.createNewFile()) {
                    try {
                        return@async harmonyTransactionsFile.inputStream().buffered()
                            .use {
                                _InternalHarmonyLog.i(LOG_TAG, "Generating transactions from initLoad. prefsName=$prefsName")
                                HarmonyTransaction.generateHarmonyTransactions(it)
                            }
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction during load")
                    }
                }
                return@async emptySet<HarmonyTransaction>() to false
            }

            // This backup mechanism was inspired by the SharedPreferencesImpl source code
            // Check for backup file
            if (harmonyMainBackupFile.exists()) {
                harmonyMainFile.delete()
                harmonyMainBackupFile.renameTo(harmonyMainFile)
            }

            // Get a snapshot of the main file
            if (!harmonyMainFile.createNewFile()) {
                try {
                    // Get main file
                    val (_, map) = harmonyMainFile.bufferedReader()
                        .use { readHarmonyMapFromStream(it) }
                    mainSnapshot = HashMap(map)
                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "Unable to read harmony main file on init", e)
                }
            }

            // Wait for the transactions job to finish if it hasn't yet
            val (transactions) = runBlocking { transactionListJob.await() }

            // We want to commit any pending transactions to the main file on startup
            // This is due to how transactions are stored. The system uptime is reset on phone restart, which could cause transactions to be out of sync
            if (transactions.isNotEmpty()) {

                transactions.forEach { it.commitTransaction(mainSnapshot) }

                // We deleted the backup file earlier. Let's recreate it here as we are updating the main
                if (!harmonyMainBackupFile.exists()) {
                    // No backup file exists. Let's create one
                    if (!harmonyMainFile.renameTo(harmonyMainBackupFile)) {
                        return // Couldn't create a backup!
                    }
                } else {
                    harmonyMainFile.delete()
                }

                val mainWriter =
                    ParcelFileDescriptor.open(harmonyMainFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)

                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(mainWriter)
                        .use { mainOutputStream ->
                            JsonWriter(mainOutputStream.bufferedWriter())
                                .putHarmony(prefsName, mainSnapshot)
                                .flush()
                            // Write all changes to the physical storage
                            mainWriter.fileDescriptor.sync()
                        }

                    // Clear all the transactions, as they are committed
                    harmonyTransactionsFile.delete()
                    harmonyTransactionsFile.createNewFile()
                    lastReadTransactions.clear()
                    lastTransactionPosition = 0L

                    // Delete the backup file
                    harmonyMainBackupFile.delete()
                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "initialLoad() - commitToDisk got exception:", e)
                }
            }

            mapReentrantReadWriteLock.write {
                harmonyMap = HashMap(mainSnapshot)
            }
        }
    }

    private suspend fun handleTransactionUpdate() {
        checkForRequiredFiles()
        suspendCancellableCoroutine<Unit> { cont ->
            harmonyMainLockFile.withFileLock(shared = true) mainLock@{
                if (cont.isCancelled) return@suspendCancellableCoroutine // Early out if this job was cancelled before the lock was obtained
                val (transactions, isCorrupted) = RandomAccessFile(harmonyTransactionsFile, "r").use { accessFile ->
                    if (accessFile.length() == 0L) { // Don't read the transaction file if it's empty
                        lastReadTransactions.clear()
                        lastTransactionPosition = 0L
                        return@use lastReadTransactions to false
                    }
                    accessFile.seek(lastTransactionPosition) // Read from the last position
                    try {
                        val (readTransactions, isCorrupted) = FileInputStream(accessFile.fd).buffered()
                            .use {
                                _InternalHarmonyLog.i(LOG_TAG, "Generating transactions from handleTransactionUpdate. prefsName=$prefsName")
                                HarmonyTransaction.generateHarmonyTransactions(it)
                            }
                        lastTransactionPosition =
                            accessFile.length() // Set the last position to the length of the data
                        // Only set the latest transaction if the lastReadTransactions has any items
                        lastReadTransactions.maxOrNull()?.let { transaction ->
                            mapReentrantReadWriteLock.write { lastTransaction = maxOf(transaction, lastTransaction) } // Ensure only the latest used transaction is set
                        }
                        lastReadTransactions.addAll(readTransactions) // Store all the read transactions to avoid re-reading them again
                        return@use lastReadTransactions.toHashSet() to isCorrupted
                    } catch (e: IOException) {
                        _InternalHarmonyLog.w(LOG_TAG, "Unable to read transactions during update")
                        return@use emptySet<HarmonyTransaction>() to false
                    }
                }

                // The file was corrupted somehow. Commit everything to main and recreate the transaction file.
                if (isCorrupted) {
                    harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        harmonyMainLockFile.withFileLock {
                            _InternalHarmonyLog.e(LOG_TAG, "Data was corrupted! Storing valid transactions to disk, and resetting.")
                            if (!commitTransactionsToMain()) { // If nothing was committed, delete the transaction file anyways
                                harmonyTransactionsFile.delete()
                                harmonyTransactionsFile.createNewFile()
                                lastReadTransactions.clear()
                                lastTransactionPosition = 0L
                                // Clear the transaction set for this process too, as we have no guarantee it was written to disk
                                mapReentrantReadWriteLock.write { transactionSet.clear() }
                                // Re-update the map from the main snapshot immediately to resync the memory data.
                                // This is an optimization to avoid waiting for the file observer to trigger this call
                                handleMainUpdate()
                            }
                        }
                    }
                    return@mainLock
                }

                mapReentrantReadWriteLock.write {
                    // Remove any transactions that were in flight for this process
                    transactionSet.removeAll(transactions)

                    // Empty transactions, early exit
                    if (transactions.isEmpty() && transactionSet.isEmpty()) return@mainLock
                    val combinedTransactions = sortedSetOf<HarmonyTransaction>().apply {
                        addAll(transactions)
                        addAll(transactionSet)
                    }

                    // Get a copy of the last known main snapshot
                    val mainCopy = HashMap(mainSnapshot)

                    val notifyListeners = listenerMap.isNotEmpty()
                    val keysModified = if (notifyListeners) arrayListOf<String>() else null
                    val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                    // Commit all transactions to this snapshot
                    combinedTransactions.forEach {
                        val tempTransaction = lastTransaction
                        if (tempTransaction < it) {
                            it.commitTransaction(mainCopy, keysModified)
                        } else {
                            it.commitTransaction(mainCopy)
                        }
                    }

                    harmonyMap = mainCopy

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

    // Helper function to wrap the main update function in a file lock
    private fun handleMainUpdateWithFileLock() {
        checkForRequiredFiles()
        harmonyMainLockFile.withFileLock(shared = true) {
            handleMainUpdate()
        }
    }

    // This function should always be called in a file lock
    @GuardedBy("harmonyMainLockFile")
    private fun handleMainUpdate() {
        // Read the main file directly
        val (_, map) = try {
            harmonyMainFile.bufferedReader()
                .use { readHarmonyMapFromStream(it) }
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "Unable to get main file.", e)
            return
        }

        mapReentrantReadWriteLock.write {
            // Update the main snapshot
            mainSnapshot = HashMap(map)

            // Create a copy of the main snapshot
            val mainCopy = HashMap(mainSnapshot)

            // All transactions should be applied to a copy of the main snapshot, not directly on it
            transactionSet.forEach { it.commitTransaction(mainCopy) }

            val notifyListeners = listenerMap.isNotEmpty()
            val keysModified = if (notifyListeners) arrayListOf<String>() else null
            val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

            // Identify any keys that were modified
            val oldMap = harmonyMap
            harmonyMap = mainCopy
            // TODO There is an edge case bug where this won't properly handle change listener updates across processes if the main file is updated.
            // https://github.com/pablobaxter/Harmony/issues/13
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
        sync: Boolean = false
    ): Boolean {
        checkForRequiredFiles()
        harmonyMainLockFile.withFileLock {
            // Skip writing to the transaction file, and go directly to main file update
            if (harmonyTransactionsFile.length() >= transactionMaxByteSize || sync) {
                return commitTransactionsToMain()
            } else {
                // This should be the normal use case. This updates the file quickly, for replication to the other processes
                try {
                    FileOutputStream(harmonyTransactionsFile, true).buffered().use { outputStream ->
                        // Transaction batching to improve cross-process replication
                        repeat(transactionMaxBatchCount) {
                            val peekedTransaction = transactionQueue.peek() ?: return@use
                            peekedTransaction.commitTransactionToOutputStream(outputStream)
                            outputStream.flush()
                            transactionQueue.remove(peekedTransaction)
                        }
                    }
                } catch (e: IOException) {
                    _InternalHarmonyLog.w(LOG_TAG, "Unable to write transaction", e)
                }
            }
        }
        return false
    }

    // Function to commit all transactions to the main file
    @GuardedBy("harmonyMainLockFile")
    private fun commitTransactionsToMain(): Boolean {
        val readTransactions: Set<HarmonyTransaction> = try {
            RandomAccessFile(harmonyTransactionsFile, "r").use { accessFile ->
                accessFile.seek(lastTransactionPosition)
                val (readTransactions) = FileInputStream(accessFile.fd).buffered()
                    .use { inputStream ->
                        _InternalHarmonyLog.i(LOG_TAG, "Generating transactions from commitTransactionToMain. prefsName=$prefsName")
                        HarmonyTransaction.generateHarmonyTransactions(inputStream)
                    }
                return@use sortedSetOf<HarmonyTransaction>().apply {
                    addAll(lastReadTransactions)
                    addAll(readTransactions)
                }
            }
        } catch (e: IOException) {
            _InternalHarmonyLog.w(LOG_TAG, "Unable to read transaction file", e)
            emptySet()
        }

        // Add the in-flight transactions to the list, if they exist
        val transactionsInQueue = transactionQueue.toSortedSet()

        // Remove these transactions as they won't be written to the transaction file
        mapReentrantReadWriteLock.write { transactionSet.removeAll(transactionsInQueue) }

        // Early exit if there is nothing to change
        if (transactionsInQueue.isEmpty() && readTransactions.isEmpty()) return false

        val combinedTransactions = sortedSetOf<HarmonyTransaction>().apply {
            addAll(transactionsInQueue)
            addAll(readTransactions)
        }

        // Create a backup and delete the main file
        if (!harmonyMainBackupFile.exists()) {
            if (!harmonyMainFile.renameTo(harmonyMainBackupFile)) {
                return false // Couldn't create a backup!
            }
        } else { // A back up exists! Let's keep it and just delete the main
            harmonyMainFile.delete()
        }

        // Get the current preferences
        val (_, prefs) = try {
            harmonyMainBackupFile.bufferedReader()
                .use { readHarmonyMapFromStream(it) }
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "Unable to get main file.", e)
            null to emptyMap() // Make the main empty if there was an issue reading the main file
        }

        // Create a mutable copy
        val currentPrefs = HashMap(prefs)

        // Apply all transactions to the mutable copy
        combinedTransactions.forEach { it.commitTransaction(currentPrefs) }

        val mainWriter =
            ParcelFileDescriptor.open(harmonyMainFile, ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_WRITE_ONLY)

        try {
            ParcelFileDescriptor.AutoCloseOutputStream(mainWriter).use { mainOutputStream ->
                JsonWriter(mainOutputStream.bufferedWriter())
                    .putHarmony(prefsName, currentPrefs)
                    .flush()
                // Write all changes to the physical storage
                mainWriter.fileDescriptor.sync()
            }

            // Clear the transaction file
            harmonyTransactionsFile.delete()
            harmonyTransactionsFile.createNewFile()
            lastReadTransactions.clear()
            lastTransactionPosition = 0L

            // Ensure queue is cleared of all current transactions
            transactionQueue.removeAll(transactionsInQueue)

            // Delete the backup file
            harmonyMainBackupFile.delete()

            return true
        } catch (e: IOException) {
            _InternalHarmonyLog.e(LOG_TAG, "commitToDisk got exception:", e)
        }

        // We should reach this if there was a failure writing to the prefs file.
        if (harmonyMainFile.exists()) {
            if (!harmonyMainFile.delete()) {
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
            commitToMemory()
            harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) { // Apply and commits should be run sequentially in the order received
                if (transactionQueue.isNotEmpty()) { // If no transactions are in the queue, exit early
                    commitTransactionToDisk()
                }
            }
        }

        override fun commit(): Boolean {
            commitToMemory()
            return runBlocking(harmonySingleThreadDispatcher) { // Apply and commits should be run sequentially in the order received
                commitTransactionToDisk(sync = true) // This will skip writing the transaction to the transaction file and update the main immediately
            }
        }

        private fun commitToMemory() {
            mapReentrantReadWriteLock.write {
                val notifyListeners = listenerMap.isNotEmpty()
                val keysModified = if (notifyListeners) arrayListOf<String>() else null
                val listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                synchronized(this@HarmonyEditor) {
                    val transaction = harmonyTransaction
                    transaction.memoryCommitTime = SystemClock.elapsedRealtimeNanos() // The current time this "apply()" was called
                    transactionSet.add(transaction) // Add this to the in-flight transaction set for this process
                    transactionQueue.put(transaction) // Current queue of transactions that need to be written to disk
                    lastTransaction = maxOf(transaction, lastTransaction) // Extremely rare, but if the other process emitted a later transaction, keep that as the last, else use the current transaction
                    harmonyTransaction = HarmonyTransaction() // Generate a new transaction to prevent modifying one in-flight
                    transaction.commitTransaction(harmonyMap, keysModified) // Update the in-process map and get all modified keys
                    return@synchronized
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
            }
        }
    }
}

// Internal transaction class
private class HarmonyTransaction(private val uuid: UUID = UUID.randomUUID()) : Comparable<HarmonyTransaction> { // Unique identifier to prevent transaction collision
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
            return "${this::class.java.simpleName}(data=$data)"
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

    @Throws(IOException::class)
    fun commitTransactionToOutputStream(outputStream: OutputStream) {
        val checkSum = Adler32()
        val dataOutputStream = DataOutputStream(CheckedOutputStream(outputStream, checkSum))
        dataOutputStream.writeByte(CURR_TRANSACTION_FILE_VERSION)
        dataOutputStream.writeLong(uuid.mostSignificantBits)
        dataOutputStream.writeLong(uuid.leastSignificantBits)
        dataOutputStream.writeBoolean(cleared)
        dataOutputStream.writeLong(memoryCommitTime)
        transactionMap.forEach { (k, v) ->
            dataOutputStream.writeBoolean(true)
            dataOutputStream.writeInt(k.length)
            dataOutputStream.write(k.toByteArray()) // Write the key
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
                    dataOutputStream.writeInt(d.length)
                    dataOutputStream.write(d.toByteArray())
                }
                is Set<*> -> {
                    dataOutputStream.writeByte(5)
                    dataOutputStream.writeInt(d.size)
                    @Suppress("UNCHECKED_CAST")
                    val set = d as? Set<String>
                    set?.forEach { s ->
                        dataOutputStream.writeInt(s.length)
                        dataOutputStream.write(s.toByteArray())
                    }
                }
                null -> dataOutputStream.writeByte(6)
            }

            // Write the transaction type
            dataOutputStream.writeByte(
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

    override fun compareTo(other: HarmonyTransaction): Int {
        return memoryCommitTime.compareTo(other.memoryCommitTime)
    }

    companion object {
        // Generates the list of transactions from the inputstream, with a boolean value to determine if the transaction failed the checksum validation
        fun generateHarmonyTransactions(inputStream: InputStream): Pair<Set<HarmonyTransaction>, Boolean> {
            val transactionSet = sortedSetOf<HarmonyTransaction>()
            val checksum = Adler32()
            val dataInputStream = DataInputStream(CheckedInputStream(inputStream, checksum))
            var currTransaction: HarmonyTransaction? = null

            var versionByte = dataInputStream.read()
            while (versionByte != -1) {
                var key = ""
                var data: Any? = null
                var operation: Operation? = null
                var expected = 0L
                var expectedWasSet = false
                var hasPartialTransaction = false

                try {
                    val transaction =
                        HarmonyTransaction(UUID(dataInputStream.readLong(), dataInputStream.readLong())).apply {
                            cleared = dataInputStream.readBoolean()
                            memoryCommitTime = dataInputStream.readLong()
                        }
                    currTransaction = transaction

                    while (dataInputStream.readBoolean()) {
                        hasPartialTransaction = true
                        expectedWasSet = false
                        key = if (versionByte.toByte() == TRANSACTION_FILE_VERSION_1) { // Unused. Here for compat purposes
                            dataInputStream.readUTF()
                        } else {
                            val size = dataInputStream.readInt()
                            val byteArray = ByteArray(size)
                            dataInputStream.read(byteArray)
                            String(byteArray)
                        }
                        data = when (dataInputStream.readByte()) {
                            0.toByte() -> dataInputStream.readInt()
                            1.toByte() -> dataInputStream.readLong()
                            2.toByte() -> dataInputStream.readFloat()
                            3.toByte() -> dataInputStream.readBoolean()
                            4.toByte() -> if (versionByte.toByte() == TRANSACTION_FILE_VERSION_1) { // Unused. Here for compat purposes
                                dataInputStream.readUTF()
                            } else {
                                val size = dataInputStream.readInt()
                                val byteArray = ByteArray(size)
                                dataInputStream.read(byteArray)
                                String(byteArray)
                            }
                            5.toByte() -> {
                                val count = dataInputStream.readInt()
                                val set = hashSetOf<String>()
                                repeat(count) {
                                    if (versionByte.toByte() == TRANSACTION_FILE_VERSION_1) { // Unused. Here for compat purposes
                                        set.add(dataInputStream.readUTF())
                                    } else {
                                        val size = dataInputStream.readInt()
                                        val byteArray = ByteArray(size)
                                        dataInputStream.read(byteArray)
                                        set.add(String(byteArray))
                                    }
                                }
                                set
                            }
                            6.toByte() -> null
                            else -> return transactionSet to true
                        }
                        operation = when (dataInputStream.readByte()) {
                            0.toByte() -> data?.let { Operation.Update(data) }
                            1.toByte() -> Operation.Delete
                            else -> null
                        }
                        operation?.let { op ->
                            transaction.transactionMap[key] = op
                        } ?: return transactionSet to true
                    }
                    val checkSum = checksum.value
                    expected = dataInputStream.readLong()
                    expectedWasSet = true
                    if (checkSum == expected) {
                        transactionSet.add(transaction)
                    } else {
                        // Checksum validation failed! Assume the rest of the transaction file is corrupted and return early
                        throw IOException("Transaction checksum failed")
                    }
                    checksum.reset()
                } catch (e: Exception) {
                    // This is expected if the transaction file write was not complete
                    _InternalHarmonyLog.e(LOG_TAG, "Unable to read current transaction in file", e)
                    if (currTransaction != null) {
                        // Only log the keys, values should not be logged
                        if (hasPartialTransaction) {
                            _InternalHarmonyLog.e(LOG_TAG, "partial transaction={key=$key, hasData?=${data != null}, operation=${operation?.type()}, checkSum=${checksum.value}, expectedWasSet=$expectedWasSet${if (expectedWasSet) " expected=$expected" else ""}}")
                        }
                        _InternalHarmonyLog.e(LOG_TAG, "currentTransaction=${currTransaction.uuid}, transactionMap=${currTransaction.transactionMap.map { (k, v) -> "key=$k, operation=${v.type()}" }}, isCleared=${currTransaction.cleared}, commitTime=${currTransaction.memoryCommitTime}")
                    }
                    return transactionSet to true
                }
                versionByte = dataInputStream.read()
            }
            return transactionSet to false
        }

        private fun Operation.type(): String {
            return when (this) {
                is Operation.Update -> "Update"
                is Operation.Delete -> "Delete"
            }
        }
    }
}

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"

private fun Context.harmonyPrefsFolder() = File(filesDir, HARMONY_PREFS_FOLDER).apply { if (!exists()) mkdirs() }

private val posixRegex = "[^-_.A-Za-z0-9]".toRegex()
private const val LOG_TAG = "Harmony"

private const val PREFS_DATA = "prefs.data"
private const val PREFS_TRANSACTIONS = "prefs.transaction.data"
private const val PREFS_DATA_LOCK = "prefs.data.lock"
private const val PREFS_BACKUP = "prefs.backup"
private const val KILOBYTE = 1024L * Byte.SIZE_BYTES

// Original version was Byte MAX_VALUE. All new transaction file versions should be one lower from the previous.
private const val TRANSACTION_FILE_VERSION_1 = Byte.MAX_VALUE
private const val CURR_TRANSACTION_FILE_VERSION = TRANSACTION_FILE_VERSION_1 - 1

// Empty singleton to support WeakHashmap
private object CONTENT

private object SingletonLockObj

private val SINGLETON_MAP = hashMapOf<String, HarmonyImpl>()

private val EMPTY_TRANSACTION = HarmonyTransaction().apply { memoryCommitTime = Long.MIN_VALUE }

@VisibleForTesting
@JvmSynthetic
internal fun Context.getHarmonySharedPreferences(
    name: String,
    maxTransactionSize: Long,
    maxTransactionBatchCount: Int = 250
): SharedPreferences {
    return SINGLETON_MAP[name] ?: synchronized(SingletonLockObj) {
        SINGLETON_MAP.getOrPut(name) {
            HarmonyImpl(applicationContext, name, maxTransactionSize, maxTransactionBatchCount)
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
