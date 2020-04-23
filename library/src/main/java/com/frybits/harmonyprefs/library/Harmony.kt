package com.frybits.harmonyprefs.library

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.util.JsonReader
import android.util.Log
import com.frybits.harmonyprefs.library.core.HarmonyLog
import com.frybits.harmonyprefs.library.core.harmonyFileObserver
import com.frybits.harmonyprefs.library.core.harmonyPrefsFolder
import com.frybits.harmonyprefs.library.core.toMap
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

// Empty singleton to support WeakHashmap
private object CONTENT

class Harmony private constructor(
    context: Context,
    private val prefsName: String
) : SharedPreferences {

    private val harmonyPrefsFolder = context.harmonyPrefsFolder()
    private val harmonyPrefsFile = File(harmonyPrefsFolder, prefsName)

    // Single thread dispatcher, which handles file reads/writes for the prefs asynchronously
    private val harmonyDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val harmonyCoroutineScope = CoroutineScope(SupervisorJob() + CoroutineName(LOG_TAG))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val mapReentrantReadWriteLock = ReentrantReadWriteLock()

    // Prevents multiple threads from reading/writing the file at once
    private val fileDescriptorMutex = Mutex()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFile) { event, _ ->
            when (event) {
                FileObserver.MODIFY -> { // We only really care if this file has been modified
                    harmonyCoroutineScope.launch(harmonyDispatcher) {
                        HarmonyLog.d(LOG_TAG, "A file closed!")
                        loadFromDisk()
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
        isLoadedDeferred = harmonyCoroutineScope.async(harmonyDispatcher) { loadFromDisk() }
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
    private suspend fun loadFromDisk() {

        // Only allow one filedescriptor open at a time. Should not be reentrant
        fileDescriptorMutex.withLock {
            if (!harmonyPrefsFolder.exists()) {
                HarmonyLog.e(LOG_TAG, "Harmony folder does not exist! Creating...")
                harmonyPrefsFolder.mkdirs()
            }
            val pfd = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY
            )
            val prefsInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            HarmonyLog.v(LOG_TAG, "Attempting to get file lock...")

            // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
            val lock = prefsInputStream.channel.lock(0, Long.MAX_VALUE, true)
            HarmonyLog.v(LOG_TAG, "Got the file lock!")
            val map: Map<String, Any?> = try {
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
                HarmonyLog.v(LOG_TAG, "Releasing lock and closing input stream")

                // Release the file lock
                lock.release()
                prefsInputStream.close()
            }

            if (prefsName == map[NAME_KEY]) {
                @Suppress("UNCHECKED_CAST")
                mapReentrantReadWriteLock.write {
                    harmonyMap.putAll((map[DATA_KEY] as? Map<String, Any?>?) ?: emptyMap())
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
            }
            val pfd = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
            val prefsOutputStream =
                ParcelFileDescriptor.AutoCloseOutputStream(pfd)
            HarmonyLog.v(LOG_TAG, "Attempting to get file lock...")

            // Lock the file for writes across all processes. This is an exclusive lock
            val lock = prefsOutputStream.channel.lock()
            HarmonyLog.v(LOG_TAG, "Got the file lock!")
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
            } finally {
                try {
                    HarmonyLog.v(LOG_TAG, "Syncing the filedescriptor...")

                    // Sync any in-memory buffer of file changes to the file system
                    pfd.fileDescriptor.sync()
                    commitResult = true
                } catch (e: SyncFailedException) {
                    HarmonyLog.e(LOG_TAG, "Unable to sync filedescriptor", e)
                }
                HarmonyLog.d(LOG_TAG, "Releasing file lock and closing output stream")

                // Release the file lock
                lock.release()
                prefsOutputStream.close()
                HarmonyLog.d(LOG_TAG, "Restarting the file observer!")

                // Begin observing the file changes again
                harmonyFileObserver.startWatching()
            }
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
            Log.d("Blah", "Calling clear!")
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
            harmonyCoroutineScope.launch(harmonyDispatcher) {
                commitToDisk(currMemoryMap)
            }
        }

        override fun commit(): Boolean {
            Log.d("Blah", "Commit called!")
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
