package com.frybits.harmony.test.secure

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.*

private const val PREFS = "prefs"

@RunWith(AndroidJUnit4::class)
class EncryptedHarmonyTest31 {

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private lateinit var sharedPreferences: SharedPreferences

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        sharedPreferences = appContext.getEncryptedHarmonySharedPreferences(
            PREFS,
            masterKeyAlias,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )

        sharedPreferences.edit(true) { clear() }
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
}
