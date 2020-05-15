package com.frybits.harmony

import android.util.JsonReader
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.core.toMap
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import kotlin.test.assertTrue

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val BASIC_JSON = """
    {
        "foo":"bar",
        "baz":1,
        "car":true
    }
"""
private val BASIC_MAP = mapOf<String, Any?>("foo" to "bar", "baz" to 1L, "car" to true)

private const val NESTED_OBJECT_JSON = """
    {
        "foo":"bar",
        "baz":1,
        "car": {
            "truck":true,
            "boat": {
                "titanic":32
            }
        }
    }
"""
private val NESTED_OBJECT_MAP = mapOf<String, Any?>(
    "foo" to "bar",
    "baz" to 1L,
    "car" to mapOf<String, Any?>(
        "truck" to true,
        "boat" to mapOf<String, Any?>(
            "titanic" to 32L
        )
    )
)

private const val NESTED_ARRAY_JSON = """
    {
        "foo":"bar",
        "baz":1,
        "car": [
            "truck",
            [
                "titanic",
                32
            ]
        ]
    }
"""
private val NESTED_ARRAY_MAP = mapOf<String, Any?>(
    "foo" to "bar",
    "baz" to 1L,
    "car" to listOf<Any?>(
        "truck",
        listOf<Any?>(
            "titanic", 32L
        )
    )
)

@RunWith(AndroidJUnit4::class)
class HarmonyUtilsTest {

    @Test
    fun basicTestJsonToMap() {
        val map = JsonReader(StringReader(BASIC_JSON)).toMap()
        assertTrue("Maps were not equal") { BASIC_MAP == map }
    }

    @Test
    fun nestedObjectJsonToMap() {
        val map = JsonReader(StringReader(NESTED_OBJECT_JSON)).toMap()
        assertTrue("Maps were not equal") { NESTED_OBJECT_MAP == map }
    }

    @Test
    fun nestedArrayJsonToMap() {
        val map = JsonReader(StringReader(NESTED_ARRAY_JSON)).toMap()
        assertTrue("Maps were not equal") { NESTED_ARRAY_MAP == map }
    }
}
