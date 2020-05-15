package com.frybits.harmony.app.test.singleentry.commit

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.lifecycle.lifecycleScope
import com.frybits.harmony.app.ITERATIONS
import com.frybits.harmony.app.PREFS_NAME
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.app.R
import com.frybits.harmony.app.test.singleentry.HarmonyPrefsReceiveService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsCommitActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }
    private val commitTimeSpent = LongArray(ITERATIONS)

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
            commitTimeSpent.fill(0L, 0, ITERATIONS)
            activityHarmonyPrefs = getHarmonySharedPreferences(PREFS_NAME)
            activityHarmonyPrefs.edit(true) { clear() }
            startService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsReceiveService::class.java
                ).apply { putExtra("START", true) })
            delay(3000) // Give the service enough time to setup
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Starting single entry test of $ITERATIONS items!")
            val editor = activityHarmonyPrefs.edit()
            val time = measureTimeMillis {
                repeat(ITERATIONS) { i ->
                    if (!isActive) return@async
                    val measure = measureTimeMillis {
                        editor.putLong(
                            testKeyArray[i],
                            SystemClock.elapsedRealtime()
                        ).commit()
                    }
                    commitTimeSpent[i] = measure
                }
            }
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Time to commit $ITERATIONS items: $time ms")
            delay(3000)
            startService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsReceiveService::class.java
                ).apply { putExtra("STOP", true) })
            delay(1000)
            stopService(
                Intent(
                    this@HarmonyPrefsCommitActivity,
                    HarmonyPrefsReceiveService::class.java
                )
            )
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Stopping test!")
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Commit count: ${commitTimeSpent.size}, expecting $ITERATIONS")
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Average to commit one item: ${commitTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Max to commit one item: ${commitTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsCommitActivity::class.java.simpleName}: Min to commit one item: ${commitTimeSpent.min()} ms")
        }
    }
}
