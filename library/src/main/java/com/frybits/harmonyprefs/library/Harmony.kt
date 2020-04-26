package com.frybits.harmonyprefs.library

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.util.JsonReader
import com.frybits.harmonyprefs.library.core.HarmonyLog
import com.frybits.harmonyprefs.library.core.harmonyFileObserver
import com.frybits.harmonyprefs.library.core.harmonyPrefsFolder
import com.frybits.harmonyprefs.library.core.toMap
import com.frybits.harmonyprefs.library.core.withFileLock
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.SyncFailedException
import java.util.WeakHashMap
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

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

    private val harmonyPrefsFolder = File(context.harmonyPrefsFolder(), prefsName)

    // Data file
    private val harmonyPrefsFile = File(harmonyPrefsFolder, PREFS_DATA)
    private val harmonyPrefsLockFile = File(harmonyPrefsFolder, PREFS_DATA_LOCK)

    // Backup file
    private val harmonyPrefsBackupFile = File(harmonyPrefsFolder, PREFS_BACKUP)
    private val harmonyPrefsBackupLockFile = File(harmonyPrefsFolder, PREFS_BACKUP_LOCK)

    // Single thread dispatcher, which handles file reads/writes for the prefs asynchronously
    private val harmonySingleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val harmonyCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(LOG_TAG))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val mapReentrantReadWriteLock = ReentrantReadWriteLock()

    // Prevents multiple threads from reading/writing the file at once
    private val fileDescriptorMutex = Mutex()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFolder) { event, path ->
            when (event) {
                FileObserver.MODIFY -> { // We only really care if this file has been modified
                    val isPrefsData = path?.contains(PREFS_DATA) ?: false
                    if (isPrefsData) {
                        HarmonyLog.d("Blah", path ?: "Nothing")
                        harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                            loadFromDisk(true)
                        }
                    }
                }
            }
        }

    // In-memory map. Concurrent to ensure map is always up-to-date
    private val harmonyMap: MutableMap<String, Any?> = hashMapOf()

    // Pref change listener
    private val listenerMap = WeakHashMap<SharedPreferences.OnSharedPreferenceChangeListener, Any>()

    init {
        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        isLoadedDeferred = harmonyCoroutineScope.async(harmonySingleThreadDispatcher) { loadFromDisk(false) }
    }

    override fun getInt(key: String, defValue: Int): Int {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?)?.toInt() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        if (!isLoadedDeferred.isCompleted) {
            runBlocking {
                isLoadedDeferred.await()
            }
        }
        val obj = mapReentrantReadWriteLock.read { harmonyMap[key] }
        return (obj as Long?) ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        if (!isLoadedDeferred.isCompleted) {
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
    private suspend fun loadFromDisk(shouldNotifyListeners: Boolean) {

        // Only allow one filedescriptor open at a time. Should not be reentrant
        fileDescriptorMutex.withLock {
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
                    // Exclusively lock the backup file
                    harmonyPrefsBackupLockFile.withFileLock {
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
                        JsonReader(prefsInputStream.reader()).toMap()
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
                @Suppress("UNCHECKED_CAST")
                val dataMap = map[DATA_KEY] as? Map<String, Any?>?
                val oldMap = mapReentrantReadWriteLock.write {
                    val oldMap = harmonyMap.toMutableMap()
                    harmonyMap.putAll(dataMap ?: emptyMap())
                    return@write oldMap
                }

                if (shouldNotifyListeners && listenerMap.isNotEmpty()) {
                    val listeners = listenerMap.keys.toMutableList()
                    val keysModified = arrayListOf<String>()
                    dataMap?.forEach { (k, v) ->
                        if (!oldMap.containsKey(k) || oldMap[k] != v) {
                            keysModified.add(k)
                        }
                    }

                    if (keysModified.isNotEmpty()) {
                        harmonyCoroutineScope.launch(Dispatchers.Main) {
                            keysModified.asReversed().forEach { key ->
                                listeners.forEach { listener ->
                                    listener.onSharedPreferenceChanged(this@Harmony, key)
                                }
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
    }

    private suspend fun commitToDisk(editedMap: Map<String, Any?>): Boolean {
        var commitResult = false

        // Only allow one filedescriptor open at a time. Should not be reentrant
        fileDescriptorMutex.withLock {
            if (!harmonyPrefsFolder.exists()) {
                HarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
                harmonyPrefsFolder.mkdirs()
                harmonyPrefsLockFile.createNewFile()
            }

            // Lock the file for writes across all processes. This is an exclusive lock
            harmonyPrefsLockFile.withFileLock {
                if (!harmonyPrefsBackupFile.exists()) {
                    // No backup file exists. Let's create one
                    if (!harmonyPrefsFile.renameTo(harmonyPrefsBackupFile)) {
                        return commitResult
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
                    HarmonyLog.d(LOG_TAG, "Stopping file observer...")

                    // We don't want to observe the changes happening in our own process for this file
                    harmonyFileObserver.stopWatching()
                    HarmonyLog.v(LOG_TAG, "Begin writing data to file...")
                    prefsOutputStream.writer().apply {
                        write(JSONObject().apply {
                            put(NAME_KEY, prefsName)
                            put(DATA_KEY, JSONObject(editedMap))
                        }.toString())
                    }.flush()
                    HarmonyLog.v(LOG_TAG, "Finish writing data to file!")

                    try {
                        HarmonyLog.v(LOG_TAG, "Syncing the filedescriptor...")

                        // Sync any in-memory buffer of file changes to the file system
                        pfd.fileDescriptor.sync()

                        // We wrote to file. We can delete the backup
                        harmonyPrefsBackupFile.delete()
                        commitResult = true
                    } catch (e: SyncFailedException) {
                        HarmonyLog.e(LOG_TAG, "Unable to sync filedescriptor", e)
                    }
                } finally {
                    HarmonyLog.d(LOG_TAG, "Releasing file lock and closing output stream")

                    prefsOutputStream.close()
                }
            }

            HarmonyLog.d(LOG_TAG, "Restarting the file observer!")

            // Begin observing the file changes again
            harmonyFileObserver.startWatching()
        }
        return commitResult
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
            val currMemoryMap = commitToMemory()
            harmonyCoroutineScope.launch(harmonySingleThreadDispatcher) {
                commitToDisk(currMemoryMap)
            }
        }

        override fun commit(): Boolean {
            return runBlocking {
                commitToDisk(commitToMemory())
            }
        }

        private fun commitToMemory(): Map<String, Any?> {
            // Synchronize to prevent changes to the changes map
            return synchronized(this) {
                var keysModified: MutableList<String>? = null
                var listeners: MutableSet<SharedPreferences.OnSharedPreferenceChangeListener>? =
                    null
                // Lock the in-memory map from being edited by multiple threads
                mapReentrantReadWriteLock.write {
                    if (listenerMap.isNotEmpty()) {
                        keysModified = arrayListOf()
                        listeners = listenerMap.keys.toHashSet()
                    }
                    if (cleared) {
                        if (harmonyMap.isNotEmpty()) {
                            harmonyMap.clear()
                        }
                        cleared = false
                    }

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

                        if (listenerMap.isNotEmpty()) {
                            keysModified?.add(k)
                        }
                    }
                    modifiedMap.clear()

                    harmonyCoroutineScope.launch(Dispatchers.Main) {
                        keysModified?.asReversed()?.forEach { key ->
                            listeners?.forEach { listener ->
                                listener.onSharedPreferenceChanged(this@Harmony, key)
                            }
                        }
                    }
                    // Return a copy of the new map. We don't want any changes on the in-memory map to reflect here
                    return@synchronized harmonyMap.toMap()
                }
            }
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
