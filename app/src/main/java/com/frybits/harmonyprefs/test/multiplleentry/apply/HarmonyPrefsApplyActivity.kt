package com.frybits.harmonyprefs.test.multiplleentry.apply

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.frybits.harmonyprefs.ITERATIONS
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsApplyActivity : Activity() {

    private lateinit var activityHarmonyPrefs: SharedPreferences
    private lateinit var fooServicePrefs: SharedPreferences
    private lateinit var barServicePrefs: SharedPreferences

    private val fooCaptureList = arrayListOf<Long>()
    private val barCaptureList = arrayListOf<Long>()

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val now = SystemClock.elapsedRealtime()
        require(fooServicePrefs === prefs || barServicePrefs === prefs)
        Log.d("Trial", "Activity: Received the response for key: $key from ${if (fooServicePrefs === prefs) "fooPrefs" else "barPrefs"}")
        val activityTestTime = prefs.getLong(key, -1L)
        require(activityTestTime > -1L)
        Log.d("Trial", "Activity: Time to receive $key: ${now - activityTestTime}")
        if (fooServicePrefs === prefs) {
            fooCaptureList.add(now - activityTestTime)
        } else {
            barCaptureList.add(now - activityTestTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        activityHarmonyPrefs = getHarmonyPrefs("ActivityPrefs")
        activityHarmonyPrefs.edit { clear() }
        fooServicePrefs = getHarmonyPrefs("fooServicePrefs")
        fooServicePrefs.edit { clear() }
        fooServicePrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        barServicePrefs = getHarmonyPrefs("barServicePrefs")
        barServicePrefs.edit { clear() }
        barServicePrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

        GlobalScope.launch(Dispatchers.IO) {
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("START", true) })
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyBarService::class.java).apply { putExtra("START", true) })
            delay(3000)
            Log.i("Trial", "Activity: Starting test!")
            activityHarmonyPrefs.edit {
                repeat(ITERATIONS) { i ->
                    putBoolean("test$i", true)
                }
            }
            delay(5000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("STOP", true) })
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyBarService::class.java).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java))
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyBarService::class.java))
            Log.i("Trial", "Activity: Stopping test!")
            withContext(Dispatchers.Main) {
                Log.i("Trial", "Activity: Foo count: ${fooCaptureList.size}, expecting $ITERATIONS")
                Log.i("Trial", "Activity: Foo Average receive time: ${fooCaptureList.average()} ms")
                Log.i("Trial", "Activity: Foo Max receive time: ${fooCaptureList.max()} ms")
                Log.i("Trial", "Activity: Foo Min receive time: ${fooCaptureList.min()} ms")
                Log.i("Trial", "===")
                Log.i("Trial", "Activity: Bar count: ${barCaptureList.size}, expecting $ITERATIONS")
                Log.i("Trial", "Activity: Bar Average receive time: ${barCaptureList.average()} ms")
                Log.i("Trial", "Activity: Bar Max receive time: ${barCaptureList.max()} ms")
                Log.i("Trial", "Activity: Bar Min receive time: ${barCaptureList.min()} ms")
                Log.i("Trial", "===")
                val totalCaptures = fooCaptureList + barCaptureList
                Log.i("Trial", "Activity: Total Average receive time: ${totalCaptures.average()} ms")
                Log.i("Trial", "Activity: Total Max receive time: ${totalCaptures.max()} ms")
                Log.i("Trial", "Activity: Total Min receive time: ${totalCaptures.min()} ms")
            }
        }
    }
}
