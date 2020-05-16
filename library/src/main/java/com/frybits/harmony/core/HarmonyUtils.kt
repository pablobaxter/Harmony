@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.core

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileLock

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
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Helper functions
 */

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"

// String source: https://github.com/aosp-mirror/platform_prebuilt/blob/master/ndk/android-ndk-r7/platforms/android-14/arch-arm/usr/include/sys/_errdefs.h#L73
private const val RESOURCE_DEADLOCK_ERROR = "Resource deadlock would occur"

@JvmSynthetic
internal fun Context.harmonyPrefsFolder() = File(filesDir,
    HARMONY_PREFS_FOLDER
)

@JvmSynthetic
internal fun JsonReader.toMap(): MutableMap<String, Any?> = readMap()

@JvmSynthetic
internal fun JsonWriter.putMap(map: Map<String, Any?>): JsonWriter {
    beginObject()
    map.forEach { (k, v) ->
        when (v) {
            is Long -> name(k).value(v)
            is Boolean -> name(k).value(v)
            is String -> name(k).value(v)
            is Set<*> -> {
                @Suppress("UNCHECKED_CAST")
                (v as? Set<String>)?.let { set ->
                    name(k).beginArray()
                        set.forEach { s ->
                            value(s)
                        }
                    endArray()
                }
            }
        }
    }
    endObject()
    return this
}

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T {
    // File Channel must be write enabled for exclusive file locking
    val lockFileChannel =
        if (shared) FileInputStream(this).channel else FileOutputStream(this).channel
    // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
    var lock: FileLock? = null
    try {
        // Keep retrying to get the lock
        while (lock == null) {
            try {
                // This should block the thread, and prevent misuse of CPU cycles
                lock = lockFileChannel.lock(0L, Long.MAX_VALUE, shared)
            } catch (e: IOException) {
                // This would not actually cause a deadlock
                // Ignore this specific error and throw all others
                if (e.message != RESOURCE_DEADLOCK_ERROR) {
                    throw e
                }
            }
        }
        return block()
    } finally {
        lock?.release()
        lockFileChannel.close()
    }
}

private fun JsonReader.readMap(): MutableMap<String, Any?> {
    var name: String? = null
    val map = mutableMapOf<String, Any?>()
    try {
        if (this.peek() == JsonToken.END_DOCUMENT) return map
    } catch (e: IOException) {
        return map
    }

    beginObject()
    while (hasNext()) {
        when (peek()) {
            JsonToken.BEGIN_OBJECT -> name?.let { map[it] = readMap() }
            JsonToken.NAME -> name = nextName()
            JsonToken.BEGIN_ARRAY -> name?.let { map[it] = readList() }
            JsonToken.BOOLEAN -> name?.let { map[it] = nextBoolean() }
            JsonToken.NUMBER -> name?.let { map[it] = nextLong() }
            JsonToken.STRING -> name?.let { map[it] = nextString() }
            JsonToken.NULL -> nextNull()
            JsonToken.END_ARRAY -> {
                _InternalHarmonyLog.wtf("JsonReader", "This shouldn't happen")
                endArray()
            }
            JsonToken.END_OBJECT -> endObject()
            else -> Unit
        }
    }
    return map
}

private fun JsonReader.readList(): MutableList<Any?> {
    val array = mutableListOf<Any?>()
    beginArray()
    while (hasNext()) {
        when (peek()) {
            JsonToken.BEGIN_OBJECT -> array.add(readMap())
            JsonToken.BEGIN_ARRAY -> array.add(readList())
            JsonToken.BOOLEAN -> array.add(nextBoolean())
            JsonToken.NUMBER -> array.add(nextLong())
            JsonToken.STRING -> array.add(nextString())
            JsonToken.NULL -> nextNull()
            JsonToken.END_ARRAY -> endArray()
            JsonToken.END_OBJECT -> {
                _InternalHarmonyLog.wtf("JsonReader", "This shouldn't happen")
                endObject()
            }
            else -> Unit
        }
    }
    return array
}
