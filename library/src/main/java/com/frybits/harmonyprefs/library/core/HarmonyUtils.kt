package com.frybits.harmonyprefs.library.core

import android.content.Context
import android.util.JsonReader
import android.util.JsonToken
import java.io.File
import java.io.IOException

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 *
 * Helper functions
 */

@JvmSynthetic
internal fun Context.harmonyPrefsFolder() =
    File(filesDir, "harmony").apply { if (!exists()) mkdirs() }

internal fun JsonReader.toMap(): MutableMap<String, Any?> = readMap()

private fun JsonReader.readMap(): MutableMap<String, Any?> {
    var name: String? = null
    val map = mutableMapOf<String, Any?>()
    try {
        if (this.peek() == JsonToken.END_DOCUMENT) return map
    } catch (e: IOException) {
        return map
    }

    beginObject()
    while(hasNext()) {
        when (peek()) {
            JsonToken.BEGIN_OBJECT -> name?.let { map[it] = readMap() }
            JsonToken.NAME -> name = nextName()
            JsonToken.BEGIN_ARRAY -> name?.let { map[it] = readList() }
            JsonToken.BOOLEAN -> name?.let { map[it] = nextBoolean() }
            JsonToken.NUMBER -> name?.let { map[it] = nextLong() }
            JsonToken.STRING -> name?.let { map[it] = nextString() }
            JsonToken.NULL -> nextNull()
            JsonToken.END_ARRAY -> {
                logWTF("JsonReader", "This shouldn't happen")
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
                logWTF("JsonReader", "This shouldn't happen")
                endObject()
            }
            else -> Unit
        }
    }
    return array
}
