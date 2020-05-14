package com.frybits.harmonyprefs.library

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.util.JsonReader
import android.util.JsonWriter
import com.frybits.harmonyprefs.library.core._InternalHarmonyLog
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
import java.io.IOException
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/*
 * Created by Pablo Baxter (Github: pablobaxter)
 */

/**
 * Harmony is a process-safe, thread-safe [SharedPreferences] implementation.
 *
 * For the most part, documentation from [SharedPreferences] is also true for Harmony, except the warning about multiple processes not being supported.
 * It's totally supported here.
 */
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

    private val commitsInFlight = AtomicLong(0L)

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
    private var harmonyMap: HashMap<String, Any?> = hashMapOf()

    // Pref change listener map
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    /**
     * @suppress
     */
    init {
        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        // This deferred job wil block any reads of the preferences until it is complete
        isLoadedDeferred =
            harmonyCoroutineScope.async(harmonySingleThreadDispatcher) { loadFromDisk(false) }
    }

    /**
     * @see [SharedPreferences.getInt]
     */
    override fun getInt(key: String, defValue: Int): Int {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?)?.toInt() ?: defValue
    }

    /**
     * @see [SharedPreferences.getLong]
     */
    override fun getLong(key: String, defValue: Long): Long {
        if (!isLoadedDeferred.isCompleted) { // Only block if this job is not completed
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?) ?: defValue
    }

    /**
     * @see [SharedPreferences.getFloat]
     */
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

    /**
     * @see [SharedPreferences.getBoolean]
     */
    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Boolean?) ?: defValue
    }

    /**
     * @see [SharedPreferences.getString]
     */
    override fun getString(key: String, defValue: String?): String? {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as String?) ?: defValue
    }

    /**
     * @see [SharedPreferences.getStringSet]
     */
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

    /**
     * @see [SharedPreferences.contains]
     */
    override fun contains(key: String): Boolean {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return mapReentrantReadWriteLock.read { harmonyMap.containsKey(key) }
    }

    /**
     * @see [SharedPreferences.getAll]
     */
    override fun getAll(): MutableMap<String, *> {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return mapReentrantReadWriteLock.read { harmonyMap.toMutableMap() }
    }

    /**
     * @see [SharedPreferences.edit]
     */
    override fun edit(): SharedPreferences.Editor {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        return HarmonyEditor()
    }

    /**
     * This listener will also listen for changes that occur to the [Harmony] preference with the same name from other processes.
     *
     * @see [SharedPreferences.registerOnSharedPreferenceChangeListener]
     */
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mapReentrantReadWriteLock.write {
            listenerMap[listener] = CONTENT
        }
    }

    /**
     * @see [SharedPreferences.unregisterOnSharedPreferenceChangeListener]
     */
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        mapReentrantReadWriteLock.write {
            listenerMap.remove(listener)
        }
    }

    // Load from disk logic
    private fun loadFromDisk(shouldNotifyListeners: Boolean) {
        // Only allow one filedescriptor open at a time. Should not be reentrant
        if (!harmonyPrefsFolder.exists()) {
            _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
            harmonyPrefsFolder.mkdirs()
            harmonyPrefsLockFile.createNewFile()
            harmonyPrefsBackupLockFile.createNewFile()
        }

        val map: Map<String, Any?> = harmonyPrefsLockFile.withFileLock(true) {

            // Check for backup file
            if (harmonyPrefsBackupFile.exists()) {
                _InternalHarmonyLog.d(LOG_TAG, "Backup exists!")
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
                    _InternalHarmonyLog.v(LOG_TAG, "File exists! Reading json...")
                    return@withFileLock JsonReader(prefsInputStream.bufferedReader()).toMap()
                } else {
                    _InternalHarmonyLog.v(LOG_TAG, "File doesn't exist!")
                    return@withFileLock emptyMap()
                }
            } catch (e: IllegalStateException) {
                return@withFileLock emptyMap()
            } catch (e: JSONException) {
                return@withFileLock emptyMap()
            } finally {
                _InternalHarmonyLog.v(LOG_TAG, "Closing input stream")
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

                val oldMap = harmonyMap
                @Suppress("UNCHECKED_CAST")
                harmonyMap = map[DATA_KEY] as? HashMap<String, Any?>? ?: hashMapOf()
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
                            listener.onSharedPreferenceChanged(this@Harmony, key)
                        }
                    }
                }
            }
        }
    }

    private inner class HarmonyEditor : SharedPreferences.Editor {

        // Container for our current changes
        private val modifiedMap = hashMapOf<String, Any?>()
        private var cleared = AtomicBoolean(false)

        /**
         * @see [SharedPreferences.Editor.putLong]
         */
        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.putInt]
         */
        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value.toLong()
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.putBoolean]
         */
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.putFloat]
         */
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value.toRawBits().toLong()
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.putString]
         */
        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = value ?: this
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.putStringSet]
         */
        override fun putStringSet(
            key: String,
            values: MutableSet<String>?
        ): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = values?.toHashSet() ?: this
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.clear]
         */
        override fun clear(): SharedPreferences.Editor {
            synchronized(this) {
                cleared.set(true)
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.remove]
         */
        override fun remove(key: String): SharedPreferences.Editor {
            synchronized(this) {
                modifiedMap[key] = this
                return this
            }
        }

        /**
         * @see [SharedPreferences.Editor.apply]
         */
        override fun apply() {
            val currMemoryMap = commitToMemory()
            harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                commitToDisk(currMemoryMap)
            }
        }

        /**
         * @see [SharedPreferences.Editor.commit]
         */
        override fun commit(): Boolean {
            val currMemoryMap = commitToMemory()
            return runBlocking(harmonySingleThreadDispatcher) {
                return@runBlocking commitToDisk(currMemoryMap)
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

                if (commitsInFlight.getAndIncrement() > 0L) {
                    harmonyMap = HashMap(harmonyMap)
                }

                if (cleared.getAndSet(false)) {
                    if (harmonyMap.isNotEmpty()) {
                        harmonyMap.clear()
                    }
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
                return@write harmonyMap
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
            return map
        }

        private fun commitToDisk(updatedMap: Map<String, Any?>): Boolean {
            var commitResult = false

            // Lock the file for writes across all processes. This is an exclusive lock
            harmonyPrefsLockFile.withFileLock {

                if (!harmonyPrefsFolder.exists()) {
                    _InternalHarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
                    harmonyPrefsFolder.mkdirs()
                    harmonyPrefsLockFile.createNewFile()
                }

                _InternalHarmonyLog.d(LOG_TAG, "Stopping file observer...")

                // We don't want to observe the changes happening in our own process for this file
                harmonyFileObserver.stopWatching()

                if (!harmonyPrefsBackupFile.exists()) { // Back up file doesn't need to be locked, as the data lock already restricts concurrent modifications
                    // No backup file exists. Let's create one
                    if (!harmonyPrefsFile.renameTo(harmonyPrefsBackupFile)) {
                        return commitResult // Couldn't create a backup!
                    }
                } else {
                    harmonyPrefsFile.delete()
                }

                val pfd = ParcelFileDescriptor.open(
                    harmonyPrefsFile,
                    ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
                )
                val prefsOutputStream =
                    ParcelFileDescriptor.AutoCloseOutputStream(pfd)
                try {
                    try {
                        _InternalHarmonyLog.v(LOG_TAG, "Begin writing data to file...")
                        JsonWriter(prefsOutputStream.bufferedWriter())
                            .beginObject()
                            .name(NAME_KEY).value(prefsName)
                            .name(DATA_KEY).putMap(updatedMap)
                            .endObject()
                            .flush()
                        _InternalHarmonyLog.v(LOG_TAG, "Finish writing data to file!")

                        // Write all changes to the physical storage
                        pfd.fileDescriptor.sync()
                    } finally {
                        _InternalHarmonyLog.d(
                            LOG_TAG,
                            "Releasing file lock and closing output stream"
                        )
                        prefsOutputStream.close()
                    }

                    // We wrote to file. We can delete the backup
                    harmonyPrefsBackupFile.delete()
                    commitResult = true

                    commitsInFlight.decrementAndGet()

                    _InternalHarmonyLog.d(LOG_TAG, "Restarting the file observer!")

                    // Begin observing the file changes again
                    harmonyFileObserver.startWatching()
                    return commitResult
                } catch (e: IOException) {
                    _InternalHarmonyLog.e(LOG_TAG, "commitToDisk got exception:", e)
                }

                if (harmonyPrefsFile.exists()) {
                    if (!harmonyPrefsFile.delete()) {
                        _InternalHarmonyLog.w(LOG_TAG, "Couldn't cleanup partially-written preference")
                    }
                }
                return false
            }
        }
    }

    companion object {
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
        private val SINGLETON_MAP = hashMapOf<String, Harmony>()

        /**
         * Main entry to get Harmony Preferences
         *
         * This creates and holds a single instance of a [Harmony] object in memory for each unique string provided.
         * This method is thread-safe. This method is also process-safe, meaning that if the same name is given to this method
         * from different processes, the same content is returned, and changes in from one process will be reflected in the other.
         *
         * @receiver Any valid context
         * @param name The desired preference file
         *
         * @return A [SharedPreferences] object backed by [Harmony]
         */
        @JvmStatic
        @JvmName("getSharedPreferences")
        fun Context.getHarmonySharedPreferences(name: String): SharedPreferences {
            return SINGLETON_MAP[name] ?: synchronized(this@Companion) {
                SINGLETON_MAP.getOrPut(name) { Harmony(applicationContext, name) }
            }
        }
    }
}
