package com.frybits.harmony.test

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.getHarmonySharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val PREFS = "prefs"

@RunWith(AndroidJUnit4::class)
class HarmonyTest31 {

    @Test
    fun testOnPreferenceChangeListenerWithClear() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val harmonyPrefs = appContext.getHarmonySharedPreferences(PREFS)
        val keyCompletableDeferred = CompletableDeferred<String>()
        val emittedNullOnClearDeferred = CompletableDeferred<Boolean>()
        val onPreferenChanListener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                assertTrue { sharedPreferences === harmonyPrefs }
                if (key == null) {
                    emittedNullOnClearDeferred.complete(true)
                    return@OnSharedPreferenceChangeListener
                } else if (!emittedNullOnClearDeferred.isCompleted) {
                    emittedNullOnClearDeferred.complete(false)
                }
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
                assertTrue(emittedNullOnClearDeferred.await(), "Did not emit null first!")
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
        val emittedNullOnClearDeferred = CompletableDeferred<Boolean>()
        val onPreferenChanListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(sharedPreferences: SharedPreferences) {
                assertTrue { sharedPreferences === harmonyPrefs }
                emittedNullOnClearDeferred.complete(true)
            }

            override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
                emittedNullOnClearDeferred.complete(false)
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
                assertTrue(emittedNullOnClearDeferred.await(), "Did not emit null first!")
                assertEquals("test", keyCompletableDeferred.await())
            }
        }
        assertEquals(pref2, harmonyPrefs.getString("test", null))
    }
}
