package com.frybits.harmonyprefs.library

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import com.frybits.harmonyprefs.library.core.harmonyPrefsFolder
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val PREFS = "prefs"

@RunWith(AndroidJUnit4::class)
class HarmonyTest {

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.getHarmonyPrefs(PREFS).edit(true) { clear() }
        appContext.harmonyPrefsFolder().deleteRecursively()
    }

    @Test(timeout = 1000)
    fun testHarmonyLoad() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        assertFalse { sharedPreferences.contains("blah") }
    }

    @Test
    fun testIntStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomNumber = Random.nextInt()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putInt("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getInt("test", randomNumber + 1))
    }

    @Test
    fun testLongStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomNumber = Random.nextLong()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putLong("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getLong("test", randomNumber + 1))
    }

    @Test
    fun testFloatStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomNumber = Random.nextFloat()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putFloat("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getFloat("test", randomNumber + 1))
    }

    @Test
    fun testBooleanStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomBoolean = Random.nextBoolean()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putBoolean("test", randomBoolean) }
        assertEquals(randomBoolean, sharedPreferences.getBoolean("test", !randomBoolean))
    }

    @Test
    fun testStringStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomString = "${Random.nextLong()}"
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putString("test", randomString) }
        assertEquals(randomString, sharedPreferences.getString("test", null))
    }

    @Test
    fun testStringSetStorage() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonyPrefs(PREFS)
        val randomStringSet = hashSetOf<String>().apply {
            repeat(Random.nextInt(100)) {
                add("${Random.nextLong()}")
            }
        }
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putStringSet("test", randomStringSet) }
        assertEquals(randomStringSet, sharedPreferences.getStringSet("test", null))
    }

    @Test
    fun useAppContext() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.frybits.harmonyprefs.library.test", appContext.packageName)
    }
}
