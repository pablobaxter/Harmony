package com.frybits.harmonyprefs.library

import android.content.Context
import android.content.SharedPreferences
import android.os.FileObserver
import android.os.ParcelFileDescriptor
import android.util.JsonReader
import com.frybits.harmonyprefs.library.core.harmonyFileObserver
import com.frybits.harmonyprefs.library.core.harmonyPrefsFolder
import com.frybits.harmonyprefs.library.core.logDebug
import com.frybits.harmonyprefs.library.core.logError
import com.frybits.harmonyprefs.library.core.logVerbose
import com.frybits.harmonyprefs.library.core.toMap
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private val posixRegex = "[^-_.A-Za-z0-9]".toRegex()

class Harmony private constructor(
    context: Context,
    private val prefsName: String
) : SharedPreferences {

    private val harmonyPrefsFile = File(context.harmonyPrefsFolder(), prefsName)

    // Single thread dispatcher, which handles file reads/writes for the prefs asynchronously
    private val singleThreadDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val coroutineScope = CoroutineScope(SupervisorJob() + CoroutineName("harmonyReader"))

    // Lock to ensure data is loaded before attempting to read the map for the first time
    private val isLoadedDeferred: Deferred<Unit>

    // Prevents multiple threads from editing the in-memory map at once
    private val editorMutex = Mutex()

    // Prevents multiple threads from reading/writing the file at once
    private val fileDescriptorMutex = Mutex()

    // Observes changes that occur to the backing file of this preference
    private val harmonyFileObserver =
        harmonyFileObserver(harmonyPrefsFile) { event, path ->
            when (event) {
                FileObserver.MODIFY -> { // We only really care if this file has been modified
                    coroutineScope.launch(singleThreadDispatcher) {
                        logDebug("harmonyFileObserver", "A file closed!")
                        loadFromDisk()
                    }
                }
            }
        }

    // In-memory map. Concurrent to ensure map is always up-to-date
    private val harmonyMap: MutableMap<String, Any?> = ConcurrentHashMap()

    init {
        logDebug("Init", "Starting...")

        // Empty names or invalid characters are not allowed!
        if (prefsName.isEmpty() || posixRegex.containsMatchIn(prefsName)) {
            throw IllegalArgumentException("Preference name is not valid: $prefsName")
        }

        // Run the load of the file in a different thread
        isLoadedDeferred = coroutineScope.async(singleThreadDispatcher) { loadFromDisk() }
        logDebug("Init", "End")
    }

    override fun getInt(key: String, defValue: Int): Int {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }
        return (obj as Long?)?.toInt() ?: defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }
        return (obj as Long?) ?: defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }

        return (obj as Long?)?.let { Float.fromBits(it.toInt()) } ?: defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }
        return (obj as Boolean?) ?: defValue
    }

    override fun getString(key: String, defValue: String?): String? {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }
        return (obj as String?) ?: defValue
    }

    override fun getStringSet(key: String, defValues: MutableSet<String>?): Set<String>? {
        val obj = runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap[key]
        }
        return (obj as Set<String>?) ?: defValues
    }

    override fun contains(key: String): Boolean {
        return runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap.containsKey(key)
        }
    }

    override fun getAll(): MutableMap<String, *> {
        return runBlocking {
            isLoadedDeferred.await()
            return@runBlocking harmonyMap.toMutableMap()
        }
    }

    override fun edit(): SharedPreferences.Editor {
        return runBlocking {
            isLoadedDeferred.await()
            return@runBlocking HarmonyEditor()
        }
    }

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    // Load from disk logic
    private suspend fun loadFromDisk() {
        logDebug("loadFromDisk", "Begin...")

        // Only allow one filedescriptor open at a time. Should not be reentrant
        fileDescriptorMutex.withLock {
            logVerbose("loadFromDisk", "In mutex!")
            val pfd = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_ONLY
            )
            val prefsInputStream = ParcelFileDescriptor.AutoCloseInputStream(pfd)
            logVerbose("loadFromDisk", "Attempting to get file lock...")

            // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
            val lock = prefsInputStream.channel.lock(0, Long.MAX_VALUE, true)
            logVerbose("loadFromDisk", "Got the file lock!")
            val map: Map<String, Any?> = try {
                if (harmonyPrefsFile.length() > 0) {
                    logVerbose("loadFromDisk", "File exists! Reading json...")
                    JsonReader(prefsInputStream.reader()).toMap()
                } else {
                    logVerbose("loadFromDisk", "File doesn't exist!")
                    emptyMap()
                }
            } catch (e: IllegalStateException) {
                logError("loadFromDisk", "Unable to read json", e)
                emptyMap()
            } catch (e: JSONException) {
                logError("loadFromDisk", "Unable to read json", e)
                emptyMap()
            } finally {
                logVerbose("loadFromDisk", "Releasing lock and closing input stream")

                // Release the file lock
                lock.release()
                prefsInputStream.close()
            }

            logVerbose("loadFromDisk", "Populating harmony memory cache")
            @Suppress("UNCHECKED_CAST")
            harmonyMap.putAll((map["data"] as? Map<String, Any?>?) ?: emptyMap())
        }
        logDebug("loadFromDisk", "End!")
    }

    private suspend fun commitToDisk(editedMap: Map<String, Any?>): Boolean {
        var commitResult = false
        logDebug("commitToDisk", "Begin...")

        // Only allow one filedescriptor open at a time. Should not be reentrant
        fileDescriptorMutex.withLock {
            logVerbose("commitToDisk", "In mutex!")
            val pfd = ParcelFileDescriptor.open(
                harmonyPrefsFile,
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_READ_WRITE
            )
            val prefsOutputStream =
                ParcelFileDescriptor.AutoCloseOutputStream(pfd)
            logVerbose("commitToDisk", "Attempting to get file lock...")

            // Lock the file for writes across all processes. This is an exclusive lock
            val lock = prefsOutputStream.channel.lock()
            logVerbose("commitToDisk", "Got the file lock!")
            try {
                logDebug("commitToDisk", "Stopping file observer...")

                // We don't want to observe the changes happening in our own process for this file
                harmonyFileObserver.stopWatching()
                logVerbose("commitToDisk", "Begin writing data to file...")
                prefsOutputStream.writer().apply {
                    write(JSONObject().apply {
                        put("name", prefsName)
                        put("data", JSONObject(editedMap))
                    }.toString())
                }.flush()
                logVerbose("commitToDisk", "Finish writing data to file!")
            } finally {
                try {
                    logVerbose("commitToDisk", "Syncing the filedescriptor...")

                    // Sync any in-memory buffer of file changes to the file system
                    pfd.fileDescriptor.sync()
                    commitResult = true
                    logVerbose("commitToDisk", "Synced filedescriptor!")
                } catch (e: SyncFailedException) {
                    logError("commitToDisk", "Unable to sync filedescriptor", e)
                }
                logDebug("commitToDisk", "Releasing file lock and closing output stream")

                // Release the file lock
                lock.release()
                prefsOutputStream.close()
                logDebug("commitToDisk", "Restarting the file observer!")

                // Begin observing the file changes again
                harmonyFileObserver.startWatching()
            }
        }
        logDebug("commitToDisk", "End!")
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
            coroutineScope.launch(singleThreadDispatcher) {
                commitToDisk(currMemoryMap)
            }
        }

        override fun commit(): Boolean {
            return runBlocking {
                commitToDisk(commitToMemory())
            }
        }

        private fun commitToMemory(): Map<String, Any?> {
            // TODO update listeners
            logDebug("commitToMemory", "Begin...")

            // Synchronize to prevent changes to the changes map
            return synchronized(this) {
                logVerbose("commitToMemory", "In synchronized block!")
                var changesMade = false
                return@synchronized runBlocking {
                    // Lock the in-memory map from being edited by multiple threads
                    editorMutex.withLock {
                        logVerbose("commitToMemory", "In mutex!")
                        if (cleared) {
                            if (harmonyMap.isNotEmpty()) {
                                changesMade = true
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

                            changesMade = true
                            // TODO add keys modified to a list
                        }
                        modifiedMap.clear()
                        logDebug("commitToMemory", "End!")
                        // Return a copy of the new map. We don't want any changes on the in-memory map to reflect here
                        return@runBlocking harmonyMap.toMap()
                    }
                }
            }
        }
    }

    companion object {

        private val SINGLETON_MAP = hashMapOf<String, Harmony>()

        @JvmStatic
        @JvmName("getPreferences")
        fun Context.getHarmonyPrefs(name: String): Harmony {
            return SINGLETON_MAP[name] ?: synchronized(this@Companion) {
                SINGLETON_MAP.getOrPut(name) { Harmony(applicationContext, name) }
            }
        }
    }
}
