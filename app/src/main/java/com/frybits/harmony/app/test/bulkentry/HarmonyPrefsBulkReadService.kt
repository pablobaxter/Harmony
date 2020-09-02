package com.frybits.harmony.app.test.bulkentry

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.frybits.harmony.app.ITERATIONS
import com.frybits.harmony.app.NUM_TESTS
import com.frybits.harmony.app.PREFS_NAME
import com.frybits.harmony.getHarmonySharedPreferences
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
 * https://github.com/pablobaxter/Harmony
 */

class HarmonyPrefsBulkReadService : Service() {

    private lateinit var harmonyActivityPrefs: SharedPreferences

    private var isStarted = false
    private var isRegistered = false

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }

    private val singleReadTimeCaptureList = ArrayList<Long>(ITERATIONS * NUM_TESTS)
    private val totalReadTimeCaptureList = ArrayList<Long>(NUM_TESTS)

    override fun onCreate() {
        super.onCreate()
        harmonyActivityPrefs = getHarmonySharedPreferences(PREFS_NAME)
        singleReadTimeCaptureList.clear()
        totalReadTimeCaptureList.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startCommand = intent?.getBooleanExtra("START", false) ?: false
        val endCommand = intent?.getBooleanExtra("STOP", false) ?: false
        if (!isStarted && startCommand) {
            Log.i("Trial", "${this::class.java.simpleName}: Starting service to receive from main process!")
            isStarted = true
            isRegistered = true
        }
        if (isStarted && endCommand) {
            Log.i("Trial", "${this::class.java.simpleName}: Stopping service to receive from main process!")
            val measure = measureTimeMillis {
                testKeyArray.forEach { s ->
                    val readTime = measureTimeMillis {
                        if (harmonyActivityPrefs.getLong(s, -1L) == -1L) {
                            Log.e("Trial", "${this::class.java.simpleName}: Key $s was not found!")
                        }
                    }
                    singleReadTimeCaptureList.add(readTime)
                }
            }
            totalReadTimeCaptureList.add(measure)
            isStarted = false
            isRegistered = false
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i("Trial", this::class.java.simpleName)
        Log.i("Trial", "${this::class.java.simpleName}: =========================Harmony Single Read=========================")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Read count: ${singleReadTimeCaptureList.size}, expecting ${ITERATIONS * NUM_TESTS}")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Average to read one item: ${singleReadTimeCaptureList.average()} ms")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Max to read one item: ${singleReadTimeCaptureList.maxOrNull()} ms")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Min to read one item: ${singleReadTimeCaptureList.minOrNull()} ms")

        Log.i("Trial", this::class.java.simpleName)
        Log.i("Trial", "${this::class.java.simpleName}: =========================Harmony Total Read=========================")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Read test count: ${totalReadTimeCaptureList.size}, expecting $NUM_TESTS")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Average read test time: ${totalReadTimeCaptureList.average()} ms")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Max read test time: ${totalReadTimeCaptureList.maxOrNull()} ms")
        Log.i("Trial", "${this::class.java.simpleName}: Harmony - Min read test time: ${totalReadTimeCaptureList.minOrNull()} ms")
    }
}
