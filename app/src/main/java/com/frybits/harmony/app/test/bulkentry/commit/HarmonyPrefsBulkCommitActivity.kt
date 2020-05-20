package com.frybits.harmony.app.test.bulkentry.commit

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
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.app.R
import com.frybits.harmony.app.test.bulkentry.HarmonyPrefsBulkReadService
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

class HarmonyPrefsBulkCommitActivity : AppCompatActivity() {

    private var testRunDeferred: Deferred<Unit> = CompletableDeferred(Unit)
    private lateinit var activityHarmonyPrefs: SharedPreferences
    private lateinit var activityVanillaPrefs: SharedPreferences

    private val testKeyArray = Array(ITERATIONS * NUM_TESTS) { i -> "test$i" }
    private val harmonySingleCommitTimeSpent = LongArray(ITERATIONS * NUM_TESTS)
    private val harmonyTotalCommitTimeSpent = LongArray(NUM_TESTS)
    private val vanillaSingleCommitTimeSpent = LongArray(ITERATIONS * NUM_TESTS)
    private val vanillaTotalCommitTimeSpent = LongArray(NUM_TESTS)
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
            harmonySingleCommitTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            harmonyTotalCommitTimeSpent.fill(0L, 0, NUM_TESTS)
            vanillaSingleCommitTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            vanillaTotalCommitTimeSpent.fill(0L, 0, NUM_TESTS)
            vanillaSingleReadTimeSpent.fill(0L, 0, ITERATIONS * NUM_TESTS)
            vanillaTotalReadTimeSpent.fill(0L, 0, NUM_TESTS)

            activityHarmonyPrefs = getHarmonySharedPreferences(PREFS_NAME)
            activityVanillaPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            activityHarmonyPrefs.edit(true) { clear() }
            activityVanillaPrefs.edit(true) { clear() }

            // Start the test
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Starting bulk entry test of $ITERATIONS items for $NUM_TESTS runs!")

            // Vanilla tests
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Running Vanilla SharedPreferences test...")
            repeat(NUM_TESTS) { testCount ->
                Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Running Vanilla SharedPreferences test-$testCount")
                val editor = activityVanillaPrefs.edit()
                val time = measureTimeMillis {
                    repeat(ITERATIONS) { i ->
                        if (!isActive) return@async
                        val measure = measureTimeMillis {
                            editor.putLong(testKeyArray[i], SystemClock.elapsedRealtime())
                        }
                        vanillaSingleCommitTimeSpent[i] = measure
                    }
                    editor.commit()
                }
                vanillaTotalCommitTimeSpent[testCount] = time
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
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Finished running Vanilla SharedPreferences test!")
            // End Vanilla tests

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: ==================================================")
            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)

            // Harmony tests
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Running Harmony SharedPreferences test...")
            repeat(NUM_TESTS) { testCount ->
                Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Running Harmony SharedPreferences test-$testCount")
                startService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java).apply { putExtra("START", true) })
                delay(3000) // Give the service enough time to setup
                val editor = activityHarmonyPrefs.edit()
                val time = measureTimeMillis {
                    repeat(ITERATIONS) { i ->
                        if (!isActive) return@async
                        val measure = measureTimeMillis {
                            editor.putLong(testKeyArray[i], SystemClock.elapsedRealtime())
                        }
                        harmonySingleCommitTimeSpent[i] = measure
                    }
                    editor.commit()
                }
                harmonyTotalCommitTimeSpent[testCount] = time
                delay(1000) // Give ample time to let the data replicate to process
                startService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java).apply { putExtra("STOP", true) })
                delay(1000) // Give ample time to let service run read tests
                activityHarmonyPrefs.edit(true) { clear() }
                delay(3000) // Give ample time for clearing of data across all processes
            }
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Finished running Harmony SharedPreferences test!")
            // End Harmony tests

            // End of test
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Stopping test!")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Vanilla Single Commit=========================")
            // Vanilla results
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Commit count: ${vanillaSingleCommitTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Average to commit one item: ${vanillaSingleCommitTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Max to commit one item: ${vanillaSingleCommitTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Min to commit one item: ${vanillaSingleCommitTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Vanilla Total Commit=========================")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Commit test count: ${vanillaTotalCommitTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Average commit test time: ${vanillaTotalCommitTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Max commit test time: ${vanillaTotalCommitTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Min commit test time: ${vanillaTotalCommitTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Vanilla Single Read=========================")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Read count: ${vanillaSingleReadTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Average to read one item: ${vanillaSingleReadTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Max to read one item: ${vanillaSingleReadTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Min to read one item: ${vanillaSingleReadTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Vanilla Total Read=========================")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Read test count: ${vanillaTotalReadTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Average read test time: ${vanillaTotalReadTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Max read test time: ${vanillaTotalReadTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Vanilla - Min read test time: ${vanillaTotalReadTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Harmony Single Commit=========================")
            // Harmony results
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Commit count: ${harmonySingleCommitTimeSpent.size}, expecting ${ITERATIONS * NUM_TESTS}")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Average to commit one item: ${harmonySingleCommitTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Max to commit one item: ${harmonySingleCommitTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Min to commit one item: ${harmonySingleCommitTimeSpent.min()} ms")

            Log.i("Trial", this@HarmonyPrefsBulkCommitActivity::class.java.simpleName)
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: =========================Harmony Total Commit=========================")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Commit Test count: ${harmonyTotalCommitTimeSpent.size}, expecting $NUM_TESTS")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Average commit test time: ${harmonyTotalCommitTimeSpent.average()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Max commit test time: ${harmonyTotalCommitTimeSpent.max()} ms")
            Log.i("Trial", "${this@HarmonyPrefsBulkCommitActivity::class.java.simpleName}: Harmony - Min commit test time: ${harmonyTotalCommitTimeSpent.min()} ms")

            delay(250)

            stopService(Intent(this@HarmonyPrefsBulkCommitActivity, HarmonyPrefsBulkReadService::class.java))
        }
    }
}
