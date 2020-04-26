package com.frybits.harmonyprefs.library.core

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.channels.FileLock

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Helper functions
 */

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"

@JvmSynthetic
internal fun Context.harmonyPrefsFolder() = File(filesDir, HARMONY_PREFS_FOLDER)

@JvmSynthetic
internal fun JsonReader.toMap(): MutableMap<String, Any?> = readMap()

@JvmSynthetic
internal inline fun <T> File.withFileLock(shared: Boolean = false, block: () -> T): T {
    val lockFileChannel =
        if (shared) FileInputStream(this).channel else FileOutputStream(this).channel
    // Lock the file to prevent other process from writing to it while this read is occurring. This is reentrant
    var lock: FileLock? = null
    while (lock == null) {
        lock = lockFileChannel.tryLock(0, Long.MAX_VALUE, shared)
    }
    try {
        return block()
    } finally {
        lock.release()
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
                HarmonyLog.wtf("JsonReader", "This shouldn't happen")
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
                HarmonyLog.wtf("JsonReader", "This shouldn't happen")
                endObject()
            }
            else -> Unit
        }
    }
    return array
}
