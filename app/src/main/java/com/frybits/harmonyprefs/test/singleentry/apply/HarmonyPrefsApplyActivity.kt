package com.frybits.harmonyprefs.test.singleentry.apply

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.frybits.harmonyprefs.ITERATIONS
import com.frybits.harmonyprefs.R
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsApplyActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences
    private lateinit var fooServicePrefs: SharedPreferences

    private val fooCaptureList = arrayListOf<Long>()

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val now = SystemClock.elapsedRealtime()
        require(fooServicePrefs === prefs)
        Log.d("Trial", "Activity: Received the response for key: $key from ${if (fooServicePrefs === prefs) "fooPrefs" else "barPrefs"}")
        val activityTestTime = prefs.getLong(key, -1L)
        require(activityTestTime > -1L)
        Log.d("Trial", "Activity: Time to receive $key: ${now - activityTestTime}")
        if (fooServicePrefs === prefs) {
            fooCaptureList.add(now - activityTestTime)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        findViewById<Button>(R.id.actionTestButton).setOnClickListener {
            val v = it as Button
            if (testRunDeferred.isCompleted) {
                runTest()
                v.text = "Stop test"
                lifecycleScope.launch {
                    testRunDeferred.await()
                    v.text = "Start test"
                }
            } else {
                v.text = "Start test"
                testRunDeferred.cancel()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        findViewById<Button>(R.id.actionTestButton)?.let { v ->
            if (testRunDeferred.isCompleted) {
                v.text = "Start test"
            } else {
                v.text = "Stop test"
            }
        }
    }

    override fun onStop() {
        super.onStop()
        testRunDeferred.cancel()
    }

    private fun runTest() {
        fooCaptureList.clear()

        testRunDeferred = lifecycleScope.async(Dispatchers.IO) {
            activityHarmonyPrefs = getHarmonyPrefs("ActivityPrefs")
            activityHarmonyPrefs.edit(true) { clear() }
            fooServicePrefs = getHarmonyPrefs("fooServicePrefs")
            fooServicePrefs.edit(true) { clear() }
            fooServicePrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)

            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("START", true) })
            delay(3000)
            Log.i("Trial", "Activity: Starting test!")
            repeat(ITERATIONS) { i ->
                activityHarmonyPrefs.edit {
                    putLong(
                        "test$i",
                        SystemClock.elapsedRealtime()
                    )
                }
            }
            delay(15000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java))
            Log.i("Trial", "Activity: Stopping test!")
            withContext(Dispatchers.Main) {
                Log.i("Trial", "Activity: Foo count: ${fooCaptureList.size}, expecting $ITERATIONS")
                Log.i("Trial", "Activity: Foo Average receive time: ${fooCaptureList.average()} ms")
                Log.i("Trial", "Activity: Foo Max receive time: ${fooCaptureList.max()} ms")
                Log.i("Trial", "Activity: Foo Min receive time: ${fooCaptureList.min()} ms")
            }
            return@async
        }
    }
}
