package com.frybits.harmonyprefs.test.singleentry.commit

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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsCommitActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences

    private val commitTimeSpent = arrayListOf<Long>()

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
        commitTimeSpent.clear()

        testRunDeferred = lifecycleScope.async {
            activityHarmonyPrefs = getHarmonyPrefs(PREFS_NAME)
            activityHarmonyPrefs.edit(true) { clear() }
            startService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsCommitFooService::class.java
                ).apply { putExtra("START", true) })
            delay(3000) // Give the service enough time to setup
            Log.i("Trial", "Activity: Starting test!")
            val editor = activityHarmonyPrefs.edit()
            repeat(ITERATIONS) { i ->
                if (!isActive) return@async
                val measure = measureTimeMillis {
                    editor.putLong(
                        "test$i",
                        SystemClock.elapsedRealtime()
                    ).commit()
                }
                commitTimeSpent.add(measure)
            }
            delay(3000)
            startService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsCommitFooService::class.java
                ).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsCommitFooService::class.java
                )
            )
            Log.i("Trial", "Activity: Stopping test!")
            withContext(Dispatchers.Main) {
                Log.i("Trial", "Activity: Commit count: ${commitTimeSpent.size}, expecting $ITERATIONS")
                Log.i("Trial", "Activity: Total Average commit time: ${commitTimeSpent.average()} ms")
                Log.i("Trial", "Activity: Total Max commit time: ${commitTimeSpent.max()} ms")
                Log.i("Trial", "Activity: Total Min commit time: ${commitTimeSpent.min()} ms")
            }
        }
    }
}
