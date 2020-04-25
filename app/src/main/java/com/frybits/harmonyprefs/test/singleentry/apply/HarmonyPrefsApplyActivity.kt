package com.frybits.harmonyprefs.test.singleentry.apply

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsApplyActivity : AppCompatActivity() {

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
            Log.d("Trial", "Activity: Starting test!")
            repeat(1000) { i ->
                Log.d("Trial", "Activity: Sending test$i")
                activityHarmonyPrefs.edit {
                    putLong(
                        "test$i",
                        SystemClock.elapsedRealtime()
                    )
                }
            }
            delay(30000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("STOP", true) })
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyBarService::class.java).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java))
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyBarService::class.java))
            Log.d("Trial", "Activity: Stopping test!")
            withContext(Dispatchers.Main) {
                Log.d("Trial", "Activity: Foo count: ${fooCaptureList.size}")
                Log.d("Trial", "Activity: Foo Average receive time: ${fooCaptureList.average()} ms")
                Log.d("Trial", "Activity: Foo Max receive time: ${fooCaptureList.max()} ms")
                Log.d("Trial", "Activity: Foo Min receive time: ${fooCaptureList.min()} ms")
                Log.d("Trial", "===")
                Log.d("Trial", "Activity: Bar count: ${barCaptureList.size}")
                Log.d("Trial", "Activity: Bar Average receive time: ${barCaptureList.average()} ms")
                Log.d("Trial", "Activity: Bar Max receive time: ${barCaptureList.max()} ms")
                Log.d("Trial", "Activity: Bar Min receive time: ${barCaptureList.min()} ms")
                Log.d("Trial", "===")
                val totalCaptures = fooCaptureList + barCaptureList
                Log.d("Trial", "Activity: Total Average receive time: ${totalCaptures.average()} ms")
                Log.d("Trial", "Activity: Total Max receive time: ${totalCaptures.max()} ms")
                Log.d("Trial", "Activity: Total Min receive time: ${totalCaptures.min()} ms")
            }
        }
    }
}
