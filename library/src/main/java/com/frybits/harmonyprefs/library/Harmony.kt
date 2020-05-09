package com.frybits.harmonyprefs.library

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.JsonReader
import android.util.JsonWriter
import com.frybits.harmonyprefs.library.core.HarmonyLog
import com.frybits.harmonyprefs.library.core.harmonyFileObserver
import com.frybits.harmonyprefs.library.core.harmonyPrefsFolder
import com.frybits.harmonyprefs.library.core.putMap
import com.frybits.harmonyprefs.library.core.toMap
import com.frybits.harmonyprefs.library.core.withFileLock
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import java.io.File
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private val posixRegex = "[^-_.A-Za-z0-9]".toRegex()
private const val LOG_TAG = "Harmony"
private const val NAME_KEY = "name"
private const val DATA_KEY = "data"

private const val PREFS_DATA = "prefs.data"
private const val PREFS_DATA_LOCK = "prefs.data.lock"
private const val PREFS_BACKUP = "prefs.backup"
private const val PREFS_BACKUP_LOCK = "prefs.backup.lock"

// Empty singleton to support WeakHashmap
private object CONTENT

class Harmony private constructor(
    context: Context,
    private val prefsName: String
) : SharedPreferences {

    // Folder containing all harmony preference files
    private val harmonyPrefsFolder = File(context.harmonyPrefsFolder(), prefsName)

    // Data file
    private val harmonyPrefsFile = File(harmonyPrefsFolder, PREFS_DATA)

    // Lock file to prevent multiple processes from writing and reading to the data file
    private val harmonyPrefsLockFile = File(harmonyPrefsFolder, PREFS_DATA_LOCK)

    // Backup file
    private val harmonyPrefsBackupFile = File(harmonyPrefsFolder, PREFS_BACKUP)

    // Lock file to prevent manipulation of backup file while it is restored
    private val harmonyPrefsBackupLockFile = File(harmonyPrefsFolder, PREFS_BACKUP_LOCK)

    // Single thread dispatcher, to serialize any calls to read/write the prefs
    private val harmonySingleThreadDispatcher =
        Executors.newSingleThreadExecutor().asCoroutineDispatcher()
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
                if (isPrefsData) { // Ignore the lock files
                    harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                        loadFromDisk(true)
                    }
                }
            }
        }

    // In-memory map. Read and modified only under a reentrant lock
    private val harmonyMap: HashMap<String, Any?> = hashMapOf()

    // Pref change listener map
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        // This deferred job wil block any reads of the preferences until it is complete
        isLoadedDeferred = harmonyCoroutineScope.async(harmonySingleThreadDispatcher) { loadFromDisk(false) }
    }

    override fun getInt(key: String, defValue: Int): Int {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?)?.toInt() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?) ?: defValue
    }

    // Due to the backing nature of this data, we store all numbers as longs. Float raw bits are stored when saved, and used for reading it back
    override fun getFloat(key: String, defValue: Float): Float {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?)?.let { Float.fromBits(it.toInt()) } ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Boolean?) ?: defValue
    }

    override fun getString(key: String, defValue: String?): String? {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as String?) ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): Set<String>? {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        @Suppress("UNCHECKED_CAST")
        return (obj as Set<String>?) ?: defValues
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

    // Load from disk logic
    private fun loadFromDisk(shouldNotifyListeners: Boolean) {
        // Only allow one filedescriptor open at a time. Should not be reentrant
        if (!harmonyPrefsFolder.exists()) {
            HarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
            harmonyPrefsLockFile.createNewFile()
            harmonyPrefsBackupLockFile.createNewFile()
        }

        // Lock the data file for writes. Reads are reentrant
        val map: Map<String, Any?> = harmonyPrefsLockFile.withFileLock(true) {

            // Check for backup file
            if (harmonyPrefsBackupFile.exists()) {
                HarmonyLog.d(LOG_TAG, "Backup exists!")
                // Exclusively lock the backup file
                harmonyPrefsBackupLockFile.withFileLock { // Because the data lock is reentrant, we need to do an exclusive lock on backup file here
                    if (harmonyPrefsBackupFile.exists()) { // Check again if file exists
                        harmonyPrefsFile.delete()
                        if (harmonyPrefsBackupFile.renameTo(harmonyPrefsFile)) {
                            harmonyPrefsBackupFile.delete()
                        }
                    }
                }
            }

            val pfd = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY
            )
            val prefsInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            try {
                if (harmonyPrefsFile.length() > 0) {
                    HarmonyLog.v(LOG_TAG, "File exists! Reading json...")
                    JsonReader(prefsInputStream.bufferedReader()).toMap()
                } else {
                    HarmonyLog.v(LOG_TAG, "File doesn't exist!")
                    emptyMap()
                }
            } catch (e: IllegalStateException) {
                emptyMap()
            } catch (e: JSONException) {
                emptyMap()
            } finally {
                HarmonyLog.v(LOG_TAG, "Closing input stream")
                prefsInputStream.close()
            }
        }

        if (prefsName == map[NAME_KEY]) {
            var notifyListeners = false
            var keysModified: ArrayList<String>? = null
            var listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>? = null

            mapReentrantReadWriteLock.write {
                notifyListeners = shouldNotifyListeners && listenerMap.isNotEmpty()
                keysModified = if (notifyListeners) arrayListOf() else null
                listeners = if (notifyListeners) listenerMap.keys.toHashSet() else null

                @Suppress("UNCHECKED_CAST")
                val dataMap = map[DATA_KEY] as? Map<String, Any?>?
                if (dataMap.isNullOrEmpty()) {
                    keysModified?.addAll(harmonyMap.keys)
                    harmonyMap.clear()
                } else {
                    dataMap.forEach { (k, v) ->
                        if (!harmonyMap.containsKey(k) || harmonyMap[k] != v) {
                            keysModified?.add(k)
                            harmonyMap[k] = v
                        }
                    }
                    harmonyMap.keys.removeAll {
                        val removed = !dataMap.containsKey(it)
                        if (removed) {
                            keysModified?.add(it)
                        }
                        return@removeAll removed
                    }
                }
            }

            // The variable 'shouldNotifyListeners' is only true if this read is due to a file update
            if (notifyListeners) {
                requireNotNull(keysModified)
                harmonyCoroutineScope.launch(Dispatchers.Main) {
                    keysModified?.asReversed()?.forEach { key ->
                        listeners?.forEach { listener ->
                            listener.onSharedPreferenceChanged(this@Harmony, key)
                        }
                    }
                }
            }
        } else {
            HarmonyLog.w(
                LOG_TAG,
                "File name changed under us!"
            )
        }
    }

    private inner class HarmonyEditor : SharedPreferences.Editor {

        // Container for our current changes
        private val modifiedMap = hashMapOf<String, Any?>()
        private var cleared = false

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value
                return this
            }
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value.toLong()
                return this
            }
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value
                return this
            }
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value.toRawBits().toLong()
                return this
            }
        }

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value ?: this
                return this
            }
        }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = values?.toHashSet() ?: this
                return this
            }
        }

        override fun clear(): SharedPreferences.Editor {
            synchronized(this) {
                cleared = true
                return this
            }
        }

        override fun remove(key: String): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = this
                return this
            }
        }

        override fun apply() {
            val now = SystemClock.elapsedRealtime();
            val currMemoryMap = commitToMemory()
            HarmonyLog.d("Timing", "Time to commit to memory: ${SystemClock.elapsedRealtime() - now} ms")
            harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                commitToDisk(currMemoryMap)
            }
        }

        override fun commit(): Boolean {
            val now = SystemClock.elapsedRealtime()
            try {
                val currMemoryMap = commitToMemory()
                HarmonyLog.i(
                    "Timing",
                    "Time to commit to memory: ${SystemClock.elapsedRealtime() - now} ms"
                )
                val deferred = harmonyCoroutineScope.async(harmonySingleThreadDispatcher) {
                    val nowCommit = SystemClock.elapsedRealtime()
                    val result = commitToDisk(currMemoryMap)
                    HarmonyLog.i("Timing", "Time to commit to disk: ${SystemClock.elapsedRealtime() - nowCommit} ms")
                    return@async result
                }
                return runBlocking { deferred.await() }
            } finally {
                HarmonyLog.i(
                    "Timing",
                    "Time to commit: ${SystemClock.elapsedRealtime() - now} ms"
                )
            }
        }

        private fun commitToMemory(): Map<String, Any?> {
            var listenersNotEmpty = false
            var keysModified: ArrayList<String>? = null
            var listeners: Set<SharedPreferences.OnSharedPreferenceChangeListener>? = null

            val map = mapReentrantReadWriteLock.write {

                listenersNotEmpty = listenerMap.isNotEmpty()
                keysModified = if (listenersNotEmpty) arrayListOf() else null
                listeners = if (listenersNotEmpty) listenerMap.keys.toHashSet() else null

                if (cleared) {
                    if (harmonyMap.isNotEmpty()) {
                        harmonyMap.clear()
                    }
                    cleared = false
                }

                synchronized(this@HarmonyEditor) {
                    modifiedMap.forEach { (k, v) ->
                        // "this" is the magic value to remove the key associated to it
                        if (v == this || v == null) {
                            if (!harmonyMap.containsKey(k)) {
                                return@forEach
                            }
                            harmonyMap.remove(k)
                        } else {
                            if (harmonyMap.containsKey(k)) {
                                val existingVal = harmonyMap[k]
                                if (existingVal != null && existingVal == v) return@forEach
                            }
                            harmonyMap[k] = v
                        }

                        keysModified?.add(k)
                    }
                    modifiedMap.clear()
                }
                return@write harmonyMap.toMap()
            }

            if (listenersNotEmpty) {
                requireNotNull(keysModified)
                harmonyCoroutineScope.launch(Dispatchers.Main) {
                    keysModified?.asReversed()?.forEach { key ->
                        listeners?.forEach { listener ->
                            listener.onSharedPreferenceChanged(this@Harmony, key)
                        }
                    }
                }
            }
            // Return a copy of the new map. We don't want any changes on the in-memory map to reflect here
            return map
        }

        private fun commitToDisk(updatedMap: Map<String, Any?>): Boolean {
            var commitResult = false

            // Lock the file for writes across all processes. This is an exclusive lock
            val now = SystemClock.elapsedRealtime()
            harmonyPrefsLockFile.withFileLock {
                HarmonyLog.d("Timing", "Time to get write lock: ${SystemClock.elapsedRealtime() - now} ms")

                val timeToCreateFolders = measureTimeMillis {
                    if (!harmonyPrefsFolder.exists()) {
                        HarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
                        harmonyPrefsFolder.mkdirs()
                        harmonyPrefsLockFile.createNewFile()
                    }
                }

                HarmonyLog.d("Timing", "Time to create folders and lock files: $timeToCreateFolders ms")

                HarmonyLog.d(LOG_TAG, "Stopping file observer...")

                val timeTostopWatch = measureTimeMillis {
                    // We don't want to observe the changes happening in our own process for this file
                    harmonyFileObserver.stopWatching()
                }

                HarmonyLog.d("Timing", "Time to stop watcher: $timeTostopWatch ms")

                val timeToCheckForBackups = measureTimeMillis {
                    if (!harmonyPrefsBackupFile.exists()) { // Back up file doesn't need to be locked, as the data lock already restricts concurrent modifications
                        // No backup file exists. Let's create one
                        if (!harmonyPrefsFile.renameTo(harmonyPrefsBackupFile)) {
                            return commitResult
                        }
                    } else {
                        harmonyPrefsFile.delete()
                    }
                }

                HarmonyLog.d("Timing", "Time to check for backups: $timeToCheckForBackups ms")

                val pfd = ParcelFileDescriptor.open(
                    harmonyPrefsFile,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
                )
                val prefsOutputStream =
                    ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                try {
                    val writeDataTime = measureTimeMillis {
                        HarmonyLog.v(LOG_TAG, "Begin writing data to file...")
                        JsonWriter(prefsOutputStream.bufferedWriter())
                            .beginObject()
                            .name(NAME_KEY).value(prefsName)
                            .name(DATA_KEY).putMap(updatedMap)
                            .endObject()
                            .flush()
                        HarmonyLog.v(LOG_TAG, "Finish writing data to file!")
                    }

                    HarmonyLog.d("Timing", "Time to write dat: $writeDataTime ms")

                    val timeToSyncData = measureTimeMillis {
                        // We wrote to file. We can delete the backup
                        harmonyPrefsBackupFile.delete()
                        commitResult = true
                    }
                    HarmonyLog.d("Timing", "Time to sync data: $timeToSyncData ms")
                } finally {
                    HarmonyLog.d(LOG_TAG, "Releasing file lock and closing output stream")

                    val timeToClose = measureTimeMillis {
                        prefsOutputStream.close()
                    }
                    HarmonyLog.d("Timing", "Time to close outputstream: $timeToClose ms")
                }
            }

            HarmonyLog.d(LOG_TAG, "Restarting the file observer!")

            val timeToStartWatching = measureTimeMillis {
                // Begin observing the file changes again
                harmonyFileObserver.startWatching()
            }
            HarmonyLog.d("Timing", "Time to start watcher: $timeToStartWatching ms")
            return commitResult
        }
    }

    companion object {

        private val SINGLETON_MAP = hashMapOf<String, Harmony>()

        @JvmStatic
        @JvmName("getSharedPreferences")
        fun Context.getHarmonyPrefs(name: String): Harmony {
            return SINGLETON_MAP[name] ?: synchronized(this@Companion) {
                SINGLETON_MAP.getOrPut(name) { Harmony(applicationContext, name) }
            }
        }
    }
}
