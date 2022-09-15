package com.frybits.harmony.secure.test

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

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
class EncryptedHarmonyTest {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        sharedPreferences = appContext.getEncryptedHarmonySharedPreferences(
            PREFS,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.edit(true) { clear() }
    }

    @Test
    fun testIntStorage() {
        val randomNumber = Random.nextInt()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putInt("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getInt("test", randomNumber + 1))
    }

    @Test
    fun testLongStorage() {
        val randomNumber = Random.nextLong()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putLong("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getLong("test", randomNumber + 1))
    }

    @Test
    fun testFloatStorage() {
        val randomNumber = Random.nextFloat()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putFloat("test", randomNumber) }
        assertEquals(randomNumber, sharedPreferences.getFloat("test", randomNumber + 1))
    }

    @Test
    fun testBooleanStorage() {
        val randomBoolean = Random.nextBoolean()
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putBoolean("test", randomBoolean) }
        assertEquals(randomBoolean, sharedPreferences.getBoolean("test", !randomBoolean))
    }

    @Test
    fun testStringStorage() {
        val randomString = "${Random.nextLong()}"
        assertFalse { sharedPreferences.contains("test") }
        sharedPreferences.edit { putString("test", randomString) }
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(randomString, sharedPreferences.getString("test", null))
    }

    @Test
    fun testStringSetStorage() {
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
    fun testOnPreferenceChangeListener() {
        val harmonyPrefs = sharedPreferences
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
        val harmonyPrefs = sharedPreferences
        val keyCompletableDeferred = CompletableDeferred<String?>()
        val onPreferenceChangeListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences) {
            }

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
                assertTrue { sharedPreferences === harmonyPrefs }
                keyCompletableDeferred.complete(key)
            }
        }
        val pref1 = "foo${Random.nextInt()}"
        val pref2 = "bar${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains("test") }
        harmonyPrefs.edit { putString("test", pref1) }
        assertTrue { harmonyPrefs.contains("test") }
        assertEquals(pref1, harmonyPrefs.getString("test", null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenceChangeListener)
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
    fun testOnPreferenceNullKey() {
        val harmonyPrefs = sharedPreferences
        val keyCompletableDeferred = CompletableDeferred<String?>()
        val onPreferenceChangeListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences) {
                fail("onSharedPreferencesCleared Should not be called!")
            }

            override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
                assertTrue { prefs === harmonyPrefs }
                keyCompletableDeferred.complete(key)
            }
        }
        val pref1 = "foo${Random.nextInt()}"
        val pref2 = "bar${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains(null) }
        harmonyPrefs.edit { putString(null, pref1) }
        assertTrue { harmonyPrefs.contains(null) }
        assertEquals(pref1, harmonyPrefs.getString(null, null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenceChangeListener)
        harmonyPrefs.edit {
            putString(null, pref2)
        }
        runBlocking {
            withTimeout(1000) {
                assertEquals(null, keyCompletableDeferred.await())
            }
        }
        assertEquals(pref2, harmonyPrefs.getString(null, null))
    }

    @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
    @Test
    fun testOnPreferenceOnClearCalled() {
        val harmonyPrefs = sharedPreferences
        val keyCompletableDeferred = CompletableDeferred<Unit>()
        val onPreferenceChangeListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(prefs: SharedPreferences) {
                assertTrue { prefs === harmonyPrefs }
                keyCompletableDeferred.complete(Unit)
            }

            override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
                fail("onSharedPreferenceChanged Should not be called!")
            }
        }
        val pref1 = "foo${Random.nextInt()}"
        assertFalse { harmonyPrefs.contains(null) }
        harmonyPrefs.edit { putString(null, pref1) }
        assertTrue { harmonyPrefs.contains(null) }
        assertEquals(pref1, harmonyPrefs.getString(null, null))
        harmonyPrefs.registerOnSharedPreferenceChangeListener(onPreferenceChangeListener)
        harmonyPrefs.edit {
            clear()
        }
        runBlocking {
            withTimeout(1000) {
                keyCompletableDeferred.await()
            }
        }
        assertNotEquals(pref1, harmonyPrefs.getString(null, null))
    }

    @Test
    fun testEncryptedIntStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomInt = Random.nextInt()
        sharedPreferences.edit { putInt("test", randomInt) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertEquals(-1, unencryptedPrefs.getInt("test", -1)) // This key is encrypted, so it should return null
        assertEquals(randomInt, sharedPreferences.getInt("test", -1))
    }

    @Test
    fun testEncryptedLongStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomLong = Random.nextLong()
        sharedPreferences.edit { putLong("test", randomLong) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertEquals(-1L, unencryptedPrefs.getLong("test", -1L)) // This key is encrypted, so it should return null
        assertEquals(randomLong, sharedPreferences.getLong("test", -1L))
    }

    @Test
    fun testEncryptedFloatStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomFloat = Random.nextFloat()
        sharedPreferences.edit { putFloat("test", randomFloat) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertEquals(-1F, unencryptedPrefs.getFloat("test", -1F)) // This key is encrypted, so it should return null
        assertEquals(randomFloat, sharedPreferences.getFloat("test", -1F))
    }


    @Test
    fun testEncryptedBooleanStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomBoolean = Random.nextBoolean()
        sharedPreferences.edit { putBoolean("test", randomBoolean) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertEquals(!randomBoolean, unencryptedPrefs.getBoolean("test", !randomBoolean)) // This key is encrypted, so it should return null
        assertEquals(randomBoolean, sharedPreferences.getBoolean("test", !randomBoolean))
    }

    @Test
    fun testEncryptedStringStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomString = "${Random.nextInt()}"
        sharedPreferences.edit { putString("test", randomString) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertNull(unencryptedPrefs.getString("test", null)) // This key is encrypted, so it should return null
        assertEquals(randomString, sharedPreferences.getString("test", null))
    }

    @Test
    fun testEncryptedStringSetStorage() {
        val unencryptedPrefs = InstrumentationRegistry
            .getInstrumentation()
            .targetContext.getHarmonySharedPreferences(PREFS)

        // Everything should be empty
        assertFalse { unencryptedPrefs.contains("test") }
        assertFalse { sharedPreferences.contains("test") }

        assertEquals(0, unencryptedPrefs.all.size)
        assertEquals(0, sharedPreferences.all.size)

        val randomStringSet = hashSetOf<String>().apply {
            repeat(Random.nextInt(100)) {
                add("${Random.nextLong()}")
            }
        }
        sharedPreferences.edit { putStringSet("test", randomStringSet) }

        assertFalse { unencryptedPrefs.contains("test") } // Keys are encrypted, so this should be false
        assertTrue { sharedPreferences.contains("test") }
        assertEquals(1, unencryptedPrefs.all.size) // We should have one item
        assertEquals(1, sharedPreferences.all.size)

        assertNull(unencryptedPrefs.getStringSet("test", null)) // This key is encrypted, so it should return null
        assertEquals(randomStringSet, sharedPreferences.getStringSet("test", null))
    }
}
