@file:JvmName("_InternalCoreHarmony")
@file:JvmMultifileClass

package com.frybits.harmony.internal

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.io.Reader
import java.io.Writer

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

private val json = Json {
    prettyPrint = false
    isLenient = false
}

@JvmSynthetic
@Throws(IOException::class, SerializationException::class)
internal fun <T : Reader> T.readHarmony(): Pair<String?, Map<String?, Any?>> {
    val harmonyPrefs = json.parseToJsonElement(readText()).jsonObject

    // Get the metaData object
    val metaData = harmonyPrefs[METADATA]?.jsonObject

    // Get the prefs name
    val prefsName = metaData?.get(NAME_KEY)?.jsonPrimitive?.contentOrNull

    // Get data array that contains preferences
    // Return empty map if data array is not found
    val dataArray: JsonArray = harmonyPrefs[DATA]?.jsonArray ?: return prefsName to hashMapOf()

    // Create preferences data map from Json structure
    return prefsName to dataArray.asSequence().filterIsInstance<JsonObject>()
        .mapNotNull { jsonObject ->
            // Type is necessary. Early exit if no type is found.
            val type = jsonObject[TYPE]?.jsonPrimitive?.content ?: return@mapNotNull null

            // If key is not found, early exit. This is not the same as if the key is null.
            val keyPrimitive = jsonObject[KEY]?.jsonPrimitive ?: return@mapNotNull null

            // Null keys are allowed. See https://github.com/pablobaxter/Harmony/issues/29
            val key = keyPrimitive.contentOrNull

            // Null values are equivalent to the preference being removed.
            // See https://developer.android.com/reference/android/content/SharedPreferences.Editor
            val valueObject = jsonObject[VALUE] ?: return@mapNotNull null

            // Properly cast values
            return@mapNotNull key to when (type) {
                INT -> valueObject.jsonPrimitive.int
                LONG -> valueObject.jsonPrimitive.long
                FLOAT -> valueObject.jsonPrimitive.float
                BOOLEAN -> valueObject.jsonPrimitive.boolean
                STRING -> valueObject.jsonPrimitive.content
                SET -> valueObject.jsonArray.mapTo(hashSetOf()) { it.jsonPrimitive.contentOrNull }
                else -> return@mapNotNull null
            }
        }.toMap()
}

@JvmSynthetic
internal fun <T : Writer> T.putHarmony(prefsName: String, data: Map<String?, Any?>): T {
    val json = buildJsonObject {

        // Still not used, but left for future use
        putJsonObject(METADATA) {
            put(NAME_KEY, prefsName)
        }

        putJsonArray(DATA) {
            data.forEach { (k, v) ->
                // Only add a json object if expected type is found. Ignore all other types.
                when (v) {
                    is Int -> {
                        addJsonObject {
                            put(TYPE, INT)
                            put(KEY, k)
                            put(VALUE, v)
                        }
                    }

                    is Long -> {
                        addJsonObject {
                            put(TYPE, LONG)
                            put(KEY, k)
                            put(VALUE, v)
                        }
                    }

                    is Float -> {
                        addJsonObject {
                            put(TYPE, FLOAT)
                            put(KEY, k)
                            put(VALUE, v)
                        }
                    }

                    is Boolean -> {
                        addJsonObject {
                            put(TYPE, BOOLEAN)
                            put(KEY, k)
                            put(VALUE, v)
                        }
                    }

                    is String -> {
                        addJsonObject {
                            put(TYPE, STRING)
                            put(KEY, k)
                            put(VALUE, v)
                        }
                    }

                    is Set<*> -> {
                        // Ignore values that aren't String
                        addJsonObject {
                            put(TYPE, SET)
                            put(KEY, k)
                            putJsonArray(VALUE) {
                                @Suppress("UNCHECKED_CAST")
                                (v as Set<String>).forEach(::add)
                            }
                        }
                    }
                }
            }
        }
    }
    write(json.toString())
    return this
}
