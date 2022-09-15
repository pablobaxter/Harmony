package com.frybits.harmony.test

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.getHarmonySharedPreferences
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
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

private const val PREFS = "prefs"

@RunWith(AndroidJUnit4::class)
class HarmonyTest {

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        appContext.getHarmonySharedPreferences(PREFS).edit(true) { clear() }
    }

    @Test(timeout = 1000)
    fun testHarmonyLoad() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        assertFalse { sharedPreferences.contains("foo") }
    }

    @Test
    fun testIntStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomNumber = Random.nextInt()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putInt("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getInt("test", randomNumber + 1))
    }

    @Test
    fun testLongStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomNumber = Random.nextLong()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putLong("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getLong("test", randomNumber + 1))
    }

    @Test
    fun testFloatStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomNumber = Random.nextFloat()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putFloat("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getFloat("test", randomNumber + 1))
    }

    @Test
    fun testBooleanStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomBoolean = Random.nextBoolean()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putBoolean("test", randomBoolean) }
        assertEquals(randomBoolean, sharedPreferences.getBoolean("test", !randomBoolean))
    }

    @Test
    fun testStringStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomString = "${Random.nextLong()}"
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putString("test", randomString) }
        assertEquals(randomString, sharedPreferences.getString("test", null))
    }

    @Test
    fun testStringSetStorage() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
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
    fun testNullKey() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(PREFS)
        val randomString = "${Random.nextLong()}"
        assertFalse { sharedPreferences.contains(null) }
        sharedPreferences.edit { putString(null, randomString) }
        assertEquals(randomString, sharedPreferences.getString(null, null))
    }

    @Test
    fun testOnPreferenceChangeListener() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val harmonyPrefs = appContext.getHarmonySharedPreferences(PREFS)
        val keyCompletableDeferred = CompletableDeferred<String>()
        val onPreferenceChanListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                assertTrue { sharedPreferences === harmonyPrefs }
                keyCompletableDeferred.complete(key)
            }
        val pref1 = "foo${Random.nextInt()}"
        val pref2 = "bar${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains("test") }
        harmonyPrefs.edit { putString("test", pref1) }
        assertTrue { harmonyPrefs.contains("test") }
        assertEquals(pref1, harmonyPrefs.getString("test", null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenceChanListener)
        harmonyPrefs.edit { putString("test", pref2) }
        runBlocking {
            withTimeout(1000) {
                assertEquals("test", keyCompletableDeferred.await())
            }
        }
        assertEquals(pref2, harmonyPrefs.getString("test", null))
    }

    @Test
    fun testOnPreferenceChangeListenerWithClear() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val harmonyPrefs = appContext.getHarmonySharedPreferences(PREFS)
        val keyCompletableDeferred = CompletableDeferred<String>()
        val onPreferenChanListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                assertTrue { sharedPreferences === harmonyPrefs }
                keyCompletableDeferred.complete(key)
            }
        val pref1 = "foo${Random.nextInt()}"
        val pref2 = "bar${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains("test") }
        harmonyPrefs.edit { putString("test", pref1) }
        assertTrue { harmonyPrefs.contains("test") }
        assertEquals(pref1, harmonyPrefs.getString("test", null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenChanListener)
        harmonyPrefs.edit {
            clear()
            putString("test", pref2)
        }
        runBlocking {
            withTimeout(1000) {
                assertEquals("test", keyCompletableDeferred.await())
            }
        }
        assertEquals(pref2, harmonyPrefs.getString("test", null))
    }

    @Test
    fun testOnHarmonyPreferenceChangeListenerWithClear() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val harmonyPrefs = appContext.getHarmonySharedPreferences(PREFS)
        val keyCompletableDeferred = CompletableDeferred<String?>()
        val onPreferenChanListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences) {
                assertTrue { sharedPreferences === harmonyPrefs }
            }

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
                keyCompletableDeferred.complete(key)
            }
        }
        val pref1 = "foo${Random.nextInt()}"
        val pref2 = "bar${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains("test") }
        harmonyPrefs.edit { putString("test", pref1) }
        assertTrue { harmonyPrefs.contains("test") }
        assertEquals(pref1, harmonyPrefs.getString("test", null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenChanListener)
        harmonyPrefs.edit {
            clear()
            putString("test", pref2)
        }
        runBlocking {
            withTimeout(1000) {
                assertEquals("test", keyCompletableDeferred.await())
            }
        }
        assertEquals(pref2, harmonyPrefs.getString("test", null))
    }
}
