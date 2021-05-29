package com.frybits.harmony.app.test

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import androidx.collection.SparseArrayCompat
import androidx.collection.set
import androidx.core.os.bundleOf
import com.frybits.harmony.app.ACK_EVENT
import com.frybits.harmony.app.ITERATIONS_KEY
import com.frybits.harmony.app.LOG_EVENT
import com.frybits.harmony.app.LOG_KEY
import com.frybits.harmony.app.PREFS_NAME_KEY
import com.frybits.harmony.app.REMOTE_MESSENGER_KEY
import com.frybits.harmony.app.RESULTS_EVENT
import com.frybits.harmony.app.RESULTS_KEY
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.grandcentrix.tray.TrayPreferences
import net.grandcentrix.tray.core.OnTrayPreferenceChangeListener

/*
 *  Copyright 2021 Pablo Baxter
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

private const val LOG_TAG = "Remote"

class TrayRemoteTestRunnerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val serviceScope = MainScope()
    private val testKeyMap = hashMapOf<String, Int>()
    private val timeCaptureMap = SparseArrayCompat<Long>()
    private lateinit var trayPreferences: TrayPreferences

    private lateinit var remoteMessenger: Messenger
    private var iterations = 0

    private var isStarted = false

    private val onTrayPreferenceChangeListener = OnTrayPreferenceChangeListener { items ->
        val now = SystemClock.elapsedRealtimeNanos()
        serviceScope.launch {
            items.forEach { item ->
                val key = item.key()
                val keyInt = testKeyMap[key] ?: -1
                val activityTestTime = withContext(Dispatchers.Default) { trayPreferences.getString(key, null)?.toLong() ?: -1L }
                if (activityTestTime > -1L && keyInt > -1) {
                    if (timeCaptureMap.containsKey(keyInt)) {
                        withContext(Dispatchers.Default) {
                            remoteMessenger.send(Message.obtain().apply {
                                what = LOG_EVENT
                                data = bundleOf(
                                    LOG_KEY to LogEvent(
                                        priority = Log.ERROR,
                                        tag = LOG_TAG,
                                        message = "Time result changed! Key=$key"
                                    )
                                )
                            })
                        }
                    } else {
                        val diff = now - activityTestTime
                        if (diff < 0) {
                            remoteMessenger.send(Message.obtain().apply {
                                what = LOG_EVENT
                                data = bundleOf(
                                    LOG_KEY to LogEvent(
                                        priority = Log.ERROR,
                                        tag = LOG_TAG,
                                        message = "Got negative value for key $key!"
                                    )
                                )
                            })
                        }
                        timeCaptureMap[keyInt] = diff
                    }
                } else {
                    withContext(Dispatchers.Default) {
                        remoteMessenger.send(Message.obtain().apply {
                            what = LOG_EVENT
                            data = bundleOf(
                                LOG_KEY to LogEvent(
                                    priority = Log.ERROR,
                                    tag = LOG_TAG,
                                    message = "Got default long value! Key=$key"
                                )
                            )
                        })
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isStarted) {
            throw IllegalStateException("Service should not be started more than once!")
        }
        intent?.extras?.let { bundle ->
            val prefsName = requireNotNull(bundle.getString(PREFS_NAME_KEY)) { "No prefs name provided" }
            trayPreferences = AppPreferences(this, prefsName)

            trayPreferences.registerOnTrayPreferenceChangeListener(onTrayPreferenceChangeListener)

            iterations = bundle.getInt(ITERATIONS_KEY)
            require(iterations > 0) { "Must have at least 1 iteration" }
            repeat(iterations) { testKeyMap[it.toString()] = it }
            remoteMessenger = requireNotNull(bundle.getParcelable(REMOTE_MESSENGER_KEY)) { "No messenger provided" }
        } ?: throw IllegalStateException("Service should not be restarted!")
        remoteMessenger.send(Message.obtain().apply { what = ACK_EVENT })
        isStarted = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        trayPreferences.unregisterOnTrayPreferenceChangeListener(onTrayPreferenceChangeListener)
        remoteMessenger.send(Message.obtain().apply {
            what = RESULTS_EVENT
            data = bundleOf(RESULTS_KEY to LongArray(iterations) { timeCaptureMap[it] ?: -1L })
        })
        super.onDestroy()
    }
}
