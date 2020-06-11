package com.frybits.harmony.app.test.singleentry.apply

import android.content.Context
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
import com.frybits.harmony.app.NUM_TESTS
import com.frybits.harmony.app.PREFS_NAME
import com.frybits.harmony.app.R
import com.frybits.harmony.app.test.singleentry.HarmonyPrefsReceiveService
import com.frybits.harmony.getHarmonySharedPreferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/*
 *  Copyright 2020 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsApplyActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences
    private lateinit var activityVanillaPrefs: SharedPreferences

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }
    private val harmonySingleApplyTimeSpent = LongArray(ITERATIONS * NUM_TESTS)
    private val harmonyTotalApplyTimeSpent = LongArray(NUM_TESTS)
    private val vanillaSingleApplyTimeSpent = LongArray(ITERATIONS * NUM_TESTS)
    private val vanillaTotalApplyTimeSpent = LongArray(NUM_TESTS)
    private val vanillaSingleReadTimeSpent = LongArray(ITERATIONS * NUM_TESTS)
    private val vanillaTotalReadTimeSpent = LongArray(NUM_TESTS)

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

            // Prep the tests
            harmonySingleApplyTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            harmonyTotalApplyTimeSpent.fill(0L, 0, NUM_TESTS)
            vanillaSingleApplyTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            vanillaTotalApplyTimeSpent.fill(0L, 0, NUM_TESTS)
            vanillaSingleReadTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            vanillaTotalReadTimeSpent.fill(0L, 0, NUM_TESTS)

            activityHarmonyPrefs = getHarmonySharedPreferences(PREFS_NAME)
            activityVanillaPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            activityHarmonyPrefs.edit(true) { clear() }
            activityVanillaPrefs.edit(true) { clear() }

            // Start the test
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Starting single entry test of $ITERATIONS items for $NUM_TESTS runs!")

            // Vanilla tests
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Running Vanilla SharedPreferences test...")
            repeat(NUM_TESTS) { testCount ->
                Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Running Vanilla SharedPreferences test-$testCount")
                val editor = activityVanillaPrefs.edit()
                val time = measureTimeMillis {
                    repeat(ITERATIONS) { i ->
                        if (!isActive) return@async
                        val measure = measureTimeMillis {
                            editor.putLong(testKeyArray[i], SystemClock.elapsedRealtime()).apply()
                        }
                        vanillaSingleApplyTimeSpent[i] = measure
                    }
                }
                vanillaTotalApplyTimeSpent[testCount] = time
                delay(1000) // Give vanilla preferences ample time to ensure data is set

                // Read test
                val readTime = measureTimeMillis {
                    repeat(ITERATIONS) { i ->
                        if (!isActive) return@async
                        val measure = measureTimeMillis {
                            if (activityVanillaPrefs.getLong(testKeyArray[i], -1L) == -1L) {
                                Log.e("Trial", "${this::class.java.simpleName}: Vanilla - Key ${testKeyArray[i]} was not found!")
                            }
                        }
                        vanillaSingleReadTimeSpent[i] = measure
                    }
                }
                vanillaTotalReadTimeSpent[testCount] = readTime
                activityVanillaPrefs.edit(true) { clear() }
            }
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Finished running Vanilla SharedPreferences test!")
            // End Vanilla tests

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: ==================================================")
            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)

            // Harmony tests
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Running Harmony SharedPreferences test...")
            repeat(NUM_TESTS) { testCount ->
                Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Running Harmony SharedPreferences test-$testCount")
                startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java).apply { putExtra("START", true) })
                delay(3000) // Give the service enough time to setup
                val editor = activityHarmonyPrefs.edit()
                val time = measureTimeMillis {
                    repeat(ITERATIONS) { i ->
                        if (!isActive) return@async
                        val measure = measureTimeMillis {
                            editor.putLong(testKeyArray[i], SystemClock.elapsedRealtime()).apply()
                        }
                        harmonySingleApplyTimeSpent[i] = measure
                    }
                }
                harmonyTotalApplyTimeSpent[testCount] = time
                // Due to the quick successions of "apply()" called, as well as the data being reloaded ITERATION times on the other process,
                // we need to let the other process "catch up". This test is the worst case scenario for multi-process replication!
                delay(10000)
                startService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java).apply { putExtra("STOP", true) })
                delay(1000) // Give ample time for service to perform read test
                activityHarmonyPrefs.edit(true) { clear() }
                delay(1000) // Give ample time for clearing of data across all processes
            }
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Finished running Harmony SharedPreferences test!")
            // End Harmony tests

            // End of test
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Stopping test!")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Vanilla Single Apply=========================")
            // Vanilla results
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Apply count: ${vanillaSingleApplyTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Average to apply one item: ${vanillaSingleApplyTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Max to apply one item: ${vanillaSingleApplyTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Min to apply one item: ${vanillaSingleApplyTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Vanilla Total Apply=========================")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Apply test count: ${vanillaTotalApplyTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Average apply test time: ${vanillaTotalApplyTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Max apply test time: ${vanillaTotalApplyTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Min apply test time: ${vanillaTotalApplyTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Vanilla Single Read=========================")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Read count: ${vanillaSingleReadTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Average to read one item: ${vanillaSingleReadTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Max to read one item: ${vanillaSingleReadTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Min to read one item: ${vanillaSingleReadTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Vanilla Total Read=========================")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Read test count: ${vanillaTotalReadTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Average read test time: ${vanillaTotalReadTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Max read test time: ${vanillaTotalReadTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Vanilla - Min read test time: ${vanillaTotalReadTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Harmony Single Apply=========================")
            // Harmony results
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Apply count: ${harmonySingleApplyTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Average to apply one item: ${harmonySingleApplyTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Max to apply one item: ${harmonySingleApplyTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Min to apply one item: ${harmonySingleApplyTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsApplyActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: =========================Harmony Total Apply=========================")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Apply Test count: ${harmonyTotalApplyTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Average apply test time: ${harmonyTotalApplyTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Max apply test time: ${harmonyTotalApplyTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsApplyActivity::class.java.simpleName}: Harmony - Min apply test time: ${harmonyTotalApplyTimeSpent.min()} ms")

            delay(250)

            stopService(Intent(this@HarmonyPrefsApplyActivity, HarmonyPrefsReceiveService::class.java))
        }
    }
}
