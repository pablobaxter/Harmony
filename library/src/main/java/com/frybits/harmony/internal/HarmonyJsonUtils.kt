@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.internal

import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import java.io.IOException

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
 * https://github.com/pablobaxter/Harmony
 *
 * Helper functions
 */

private const val METADATA = "metaData"
private const val DATA = "data"
private const val NAME_KEY = "name"

private const val TYPE = "type"
private const val KEY = "key"
private const val VALUE = "value"

private const val INT = "int"
private const val LONG = "long"
private const val FLOAT = "float"
private const val BOOLEAN = "boolean"
private const val STRING = "string"
private const val SET = "set"

@JvmSynthetic
@Throws(IOException::class)
internal fun JsonReader.readHarmony(): HashMap<String, Any?> {
    var prefsName: String? = null
    var currName: String? = null
    val map = hashMapOf<String, Any?>()

    if (this.peek() == JsonToken.END_DOCUMENT) return map

    beginObject()
    while (hasNext()) {
        when (peek()) {
            JsonToken.NAME -> currName = nextName()
            JsonToken.BEGIN_OBJECT -> {
                if (currName == METADATA) {
                    beginObject()
                    val n = nextName()
                    if (n == NAME_KEY) {
                        prefsName = nextString()
                    }
                    endObject()
                } else {
                    skipValue()
                }
            }
            JsonToken.BEGIN_ARRAY -> {
                if (currName == DATA) {
                    beginArray()
                    while (hasNext()) {
                        beginObject()
                        var type: String? = null
                        var key: String? = null
                        while (hasNext()) {
                            when (nextName()) {
                                TYPE -> type = nextString()
                                KEY -> key = nextString()
                                VALUE -> {
                                    when (type) {
                                        INT -> key?.let { map[it] = nextInt() }
                                        LONG -> key?.let { map[it] = nextLong() }
                                        FLOAT -> key?.let { map[it] = nextDouble().toFloat() }
                                        BOOLEAN -> key?.let { map[it] = nextBoolean() }
                                        STRING -> key?.let { map[it] = nextString() }
                                        SET -> {
                                            val stringSet = mutableSetOf<String>()
                                            beginArray()
                                            while (hasNext()) {
                                                stringSet.add(nextString())
                                            }
                                            endArray()
                                            key?.let { map[it] = stringSet }
                                        }
                                    }
                                }
                            }
                        }
                        endObject()
                    }
                    endArray()
                } else {
                    skipValue()
                }
            }
            else -> skipValue()
        }
    }
    endObject()

    return map
}

@JvmSynthetic
internal fun JsonWriter.putHarmony(prefsName: String, data: Map<String, Any?>): JsonWriter {
    beginObject().run {
        name(METADATA).run {
            beginObject()
            name(NAME_KEY).value(prefsName)
            endObject()
        }
        name(DATA).run {
            beginArray()
            data.forEach { (key, value) ->
                beginObject()
                when (value) {
                    is Int -> {
                        name(TYPE).value(INT)
                        name(KEY).value(key)
                        name(VALUE).value(value)
                    }
                    is Long -> {
                        name(TYPE).value(LONG)
                        name(KEY).value(key)
                        name(VALUE).value(value)
                    }
                    is Float -> {
                        name(TYPE).value(FLOAT)
                        name(KEY).value(key)
                        name(VALUE).value(value)
                    }
                    is Boolean -> {
                        name(TYPE).value(BOOLEAN)
                        name(KEY).value(key)
                        name(VALUE).value(value)
                    }
                    is String -> {
                        name(TYPE).value(STRING)
                        name(KEY).value(key)
                        name(VALUE).value(value)
                    }
                    is Set<*> -> {
                        name(TYPE).value(SET)
                        name(KEY).value(key)
                        name(VALUE).run {
                            beginArray()
                            @Suppress("UNCHECKED_CAST")
                            (value as Set<String>).forEach {
                                value(it)
                            }
                            endArray()
                        }
                    }
                }
                endObject()
            }
            endArray()
        }
    }
    endObject()
    return this
}
