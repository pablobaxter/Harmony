package com.frybits.harmonyprefs.test.bulkentry.commit

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
import com.frybits.harmonyprefs.test.bulkentry.HarmonyPrefsBulkReadService
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

class HarmonyPrefsBulkCommitActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences

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
            activityHarmonyPrefs = getHarmonySharedPreferences(PREFS_NAME)
            activityHarmonyPrefs.edit(true) { clear() }
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Starting bulk entry test of $ITERATIONS items!")
            val editor = activityHarmonyPrefs.edit()
            repeat(ITERATIONS) { i ->
                editor.putLong(
                    "test$i",
                    SystemClock.elapsedRealtime()
                )
            }
            val measure = measureTimeMillis { editor.commit() }
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Time to apply $ITERATIONS items: $measure ms")
            delay(17) // Give it about the time it takes for a single frame render at 60hz
            startService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java).apply { putExtra("START", true) })
            startService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java).apply { putExtra("STOP", true) })
            stopService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java))
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Stopping test!")
            return@async
        }
    }
}
