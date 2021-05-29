package com.frybits.harmony

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.internal.putHarmony
import com.frybits.harmony.internal.readHarmony
import java.io.StringReader
import java.io.StringWriter
import kotlin.random.Random
import kotlin.test.assertEquals
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

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
 */

private const val BASIC_NAME = "bar"
private const val BASIC_JSON = """
    {
        "metaData": {
            "name": "$BASIC_NAME"
        },
        "data": [
            {
                "type": "string",
                "key": "blah",
                "value": "bar"
            }
        ]
    }
"""
private val BASIC_MAP = mapOf<String, Any?>("blah" to "bar")

private const val MIXED_NAME = "foo"
private const val MIXED_JSON = """
    {
        "metaData": {
            "name": "$MIXED_NAME"
        },
        "data": [
            {
                "type": "string",
                "key": "blah",
                "value": "bar"
            },
            {
                "type": "boolean",
                "key": "isSomething",
                "value": true
            },
            {
                "type": "long",
                "key": "time",
                "value": 4530349853809348080
            },
            {
                "type": "int",
                "key": "count",
                "value": 3
            }
        ]
    }
"""
private val MIXED_MAP = mapOf<String, Any?>(
    "blah" to "bar",
    "isSomething" to true,
    "time" to 4530349853809348080L,
    "count" to 3
)

@RunWith(AndroidJUnit4::class)
class HarmonyUtilsTest {

    @Test
    fun basicJsonToHarmonyMapTest() {
        val map = StringReader(BASIC_JSON).readHarmony()
        assertEquals(BASIC_MAP, map.second, "Maps were not equal")
    }

    @Test
    fun basicHarmonyMapToJsonTest() {
        val stringWriter = StringWriter()
        stringWriter.putHarmony(BASIC_NAME, BASIC_MAP).flush()
        val expected = JSONObject(BASIC_JSON).toString()
        assertEquals(expected, stringWriter.toString(), "JSON strings were not equal")
    }

    @Test
    fun mixedJsonToHarmonyMapTest() {
        val map = StringReader(MIXED_JSON).readHarmony()
        assertEquals(MIXED_MAP, map.second, "Maps were not equal")
    }

    @Test
    fun mixedHarmonyMapToJsonTest() {
        val stringWriter = StringWriter()
        stringWriter.putHarmony(MIXED_NAME, MIXED_MAP).flush()
        val expected = JSONObject(MIXED_JSON).toString()
        assertEquals(expected, stringWriter.toString(), "JSON strings were not equal")
    }

    @Test
    fun harmonyMapToJsonAndBackTest() {
        val expectedName = "test${Random.nextInt()}"
        val list = Array(10) {
            "list${Random.nextInt()}"
        }
        val expectedMap = mapOf<String, Any?>(
            "item${Random.nextInt()}" to Random.nextInt(),
            "item${Random.nextInt()}" to Random.nextLong(),
            "item${Random.nextInt()}" to Random.nextBoolean(),
            "item${Random.nextInt()}" to "foo${Random.nextInt()}",
            "item${Random.nextInt()}" to list.toSet(),
            "item${Random.nextInt()}" to Random.nextFloat()
        )

        val stringWriter = StringWriter()
        stringWriter.putHarmony(expectedName, expectedMap).flush()

        val map = StringReader(stringWriter.toString()).readHarmony()
        assertEquals(expectedMap, map.second, "Maps were not equal")
    }
}
