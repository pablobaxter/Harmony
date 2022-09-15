package com.frybits.harmony.test

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Process
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.frybits.harmony.OnHarmonySharedPreferenceChangedListener
import com.frybits.harmony.app.ALTERNATE_PROCESS_NAME
import com.frybits.harmony.app.ClearDataApplyService
import com.frybits.harmony.app.PREF_NAME
import com.frybits.harmony.app.TEST_CLEAR_DATA_KEY
import com.frybits.harmony.getHarmonySharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class HarmonyProcessApplyTest31 {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext =  ApplicationProvider.getApplicationContext<Context>()
        appContext.getHarmonySharedPreferences(PREF_NAME).edit(true) { clear() }

        // Ensure we are in the right process
        val pid = Process.myPid()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var processName: String? = null
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                processName = processInfo.processName
            }
        }
        requireNotNull(processName) { "Process name was null!" }
        assertFalse("Test is running in alternate process!") { processName.endsWith(ALTERNATE_PROCESS_NAME) }
    }

    @Test
    fun testClearedDataChangesNotifiesAcrossProcesses() = runBlocking {
        // Setup test
        val application = ApplicationProvider.getApplicationContext<Context>()

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)

        // Start clear data + simple change notify test
        val clearDataTestString = "clearData${Random.nextInt(0, Int.MAX_VALUE)}"

        sharedPreferences.edit { putString(TEST_CLEAR_DATA_KEY, clearDataTestString) } // Pre-populate the prefs with known data

        val clearDataKeyChangedCompletableDeferred = CompletableDeferred<String?>()
        val clearEmittedNullValue = CompletableDeferred<Boolean>()

        @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
        val clearDataChangeListener = object : OnHarmonySharedPreferenceChangedListener {
            override fun onSharedPreferencesCleared(prefs: SharedPreferences) {
                clearEmittedNullValue.complete(true)
                assertTrue("Wrong Harmony object was returned") { sharedPreferences == prefs }
            }

            override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
                clearEmittedNullValue.complete(false)
                assertTrue("Wrong Harmony object was returned") { sharedPreferences == prefs }
                assertTrue("Wrong key was emitted") { key == TEST_CLEAR_DATA_KEY } // We expect the change listener to emit, even though we have the same string. Prefs were cleared.
                clearDataKeyChangedCompletableDeferred.complete(prefs.getString(TEST_CLEAR_DATA_KEY, null))
            }
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(clearDataChangeListener)

        val serviceIntent = Intent(application, ClearDataApplyService::class.java).apply {
            putExtra(TEST_CLEAR_DATA_KEY, clearDataTestString) // Pass the same string
        }
        serviceRule.startService(serviceIntent)

        withTimeout(1000) {
            val emittedNull = clearEmittedNullValue.await()
            val clearDataResponse = clearDataKeyChangedCompletableDeferred.await()
            assertTrue(emittedNull, "Cleared data change listener didn't emit null first!")
            assertEquals(clearDataTestString, clearDataResponse, "Cleared data change listener failed to emit the correct key!")
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(clearDataChangeListener)
    }
}
