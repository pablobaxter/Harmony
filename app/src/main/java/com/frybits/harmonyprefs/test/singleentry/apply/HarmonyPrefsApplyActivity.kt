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
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonySharedPreferences
import com.frybits.harmonyprefs.test.singleentry.HarmonyPrefsReceiveService
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

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }
    private val applyTimeSpent = LongArray(ITERATIONS)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_test)
        title = this::class.java.simpleName
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
            applyTimeSpent.fill(0L, 0, ITERATIONS)
            activityHarmonyPrefs = getHarmonySharedPreferences(PREFS_NAME)
            activityHarmonyPrefs.edit(true) { clear() }
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java).apply { putExtra("START", true) })
            delay(3000) // Give the service enough time to setup
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Starting single entry test of $ITERATIONS items!")
            val editor = activityHarmonyPrefs.edit()
            val time = measureTimeMillis {
                repeat(ITERATIONS) { i ->
                    val measure = measureTimeMillis {
                        editor.putLong(
                            testKeyArray[i],
                            SystemClock.elapsedRealtime()
                        ).apply()
                    }
                    applyTimeSpent[i] = measure
                }
            }
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Time to apply $ITERATIONS items: $time ms")
            delay(20000)
            startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java))
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Stopping test!")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Apply count: ${applyTimeSpent.size}, expecting $ITERATIONS")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Average to apply one item: ${applyTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Max to apply one item: ${applyTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Min to apply one item: ${applyTimeSpent.min()} ms")
            return@async
        }
    }
}
