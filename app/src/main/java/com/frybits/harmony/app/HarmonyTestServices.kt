package com.frybits.harmony.app

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.IBinder
import android.os.Process
import androidx.core.content.edit
import com.frybits.harmony.getHarmonySharedPreferences
import kotlin.test.assertTrue

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

const val PREF_NAME = "prefName"
const val ALTERNATE_PROCESS_NAME = ":alternate"
const val MESSENGER_KEY = "messenger"
const val TEST_SIMPLE_KEY = "testSimple"
const val TEST_CLEAR_DATA_KEY = "testClearedData"

abstract class HarmonyService : Service() {

    protected lateinit var testPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        assertTrue("Service is not running in alternate process!") { getServiceProcess().endsWith(ALTERNATE_PROCESS_NAME) }

        testPrefs = getHarmonySharedPreferences(PREF_NAME)
    }

    // Binder cannot be null. Returning NoOp instead
    override fun onBind(intent: Intent?): IBinder? = Binder()

    private fun getServiceProcess(): String {
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return ""
    }
}

class ClearDataApplyService : HarmonyService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.hasExtra(TEST_SIMPLE_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_SIMPLE_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit { putString(TEST_SIMPLE_KEY, testString) }
            }

            if (intent.hasExtra(TEST_CLEAR_DATA_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_CLEAR_DATA_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit {
                    clear()
                    putString(TEST_CLEAR_DATA_KEY, testString)
                }
            }
        }
        return START_NOT_STICKY
    }
}

class ClearDataCommitService : HarmonyService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.hasExtra(TEST_SIMPLE_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_SIMPLE_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit(true) { putString(TEST_SIMPLE_KEY, testString) }
            }

            if (intent.hasExtra(TEST_CLEAR_DATA_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_CLEAR_DATA_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit(true) {
                    clear()
                    putString(TEST_CLEAR_DATA_KEY, testString)
                }
            }
        }
        return START_NOT_STICKY
    }
}
