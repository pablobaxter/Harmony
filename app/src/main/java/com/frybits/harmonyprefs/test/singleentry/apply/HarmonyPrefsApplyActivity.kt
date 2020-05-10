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
import com.frybits.harmonyprefs.PREFS_NAME
import com.frybits.harmonyprefs.R
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsApplyActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences

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
        testRunDeferred = lifecycleScope.async(Dispatchers.Default) {
            activityHarmonyPrefs = getHarmonyPrefs(PREFS_NAME)
            activityHarmonyPrefs.edit { clear() }
            delay(1000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("START", true) })
            delay(3000) // Give the service enough time to setup
            Log.i("Trial", "Activity: Starting test!")
            val editor = activityHarmonyPrefs.edit()
            val time = measureTimeMillis {
                repeat(ITERATIONS) { i ->
                    editor.putLong(
                        "test$i",
                        SystemClock.elapsedRealtime()
                    ).apply()
                }
            }
            Log.i("Trial", "Time: $time ms")
            delay(10000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsApplyFooService::class.java))
            Log.i("Trial", "Activity: Stopping test!")
            return@async
        }
    }
}
