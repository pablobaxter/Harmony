package com.frybits.harmony

import android.util.JsonReader
import android.util.JsonWriter
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.internal.putHarmony
import com.frybits.harmony.internal.readHarmony
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.io.StringWriter
import kotlin.random.Random
import kotlin.test.assertEquals

/**
 * Created by Pablo Baxter (Github: pablobaxter)
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
        val (name, map) = JsonReader(StringReader(BASIC_JSON)).readHarmony()
        assertEquals(BASIC_NAME, name, "Names were not equal")
        assertEquals(BASIC_MAP, map,"Maps were not equal")
    }

    @Test
    fun basicHarmonyMapToJsonTest() {
        val stringWriter = StringWriter()
        JsonWriter(stringWriter).putHarmony(BASIC_NAME, BASIC_MAP).flush()
        val expected = JSONObject(BASIC_JSON).toString()
        assertEquals(expected, stringWriter.toString(), "JSON strings were not equal")
    }

    @Test
    fun mixedJsonToHarmonyMapTest() {
        val (name, map) = JsonReader(StringReader(MIXED_JSON)).readHarmony()
        assertEquals(MIXED_NAME, name, "Names were not equal")
        assertEquals(MIXED_MAP, map,"Maps were not equal")
    }

    @Test
    fun mixedHarmonyMapToJsonTest() {
        val stringWriter = StringWriter()
        JsonWriter(stringWriter).putHarmony(MIXED_NAME, MIXED_MAP).flush()
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
        JsonWriter(stringWriter).putHarmony(expectedName, expectedMap).flush()

        val (name, map) = JsonReader(StringReader(stringWriter.toString())).readHarmony()
        assertEquals(expectedName, name, "Names were not equal")
        assertEquals(expectedMap, map, "Maps were not equal")
    }
}
