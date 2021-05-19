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
import androidx.security.crypto.MasterKeys
import com.frybits.harmony.app.ACK_EVENT
import com.frybits.harmony.app.ITERATIONS_KEY
import com.frybits.harmony.app.LOG_EVENT
import com.frybits.harmony.app.LOG_KEY
import com.frybits.harmony.app.PREFS_NAME_KEY
import com.frybits.harmony.app.REMOTE_MESSENGER_KEY
import com.frybits.harmony.app.RESULTS_EVENT
import com.frybits.harmony.app.RESULTS_KEY
import com.frybits.harmony.app.USE_ENCRYPTION_KEY
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.collections.hashMapOf
import kotlin.collections.set

private const val LOG_TAG = "Remote"

class MMKVRemoteTestRunnerService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    private val serviceScope = MainScope()
    private val testKeyMap = hashMapOf<String, Int>()
    private val timeCaptureMap = SparseArrayCompat<Long>()
    private lateinit var mmkv: MMKV

    private lateinit var remoteMessenger: Messenger
    private var iterations = 0

    private var isStarted = false

    private var currJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isStarted) {
            throw IllegalStateException("Service should not be started more than once!")
        }
        MMKV.initialize(this)
        intent?.extras?.let { bundle ->
            val prefsName =
                requireNotNull(bundle.getString(PREFS_NAME_KEY)) { "No prefs name provided" }
            mmkv = requireNotNull(
                if (bundle.getBoolean(USE_ENCRYPTION_KEY)) {
                    MMKV.mmkvWithID(
                        "$prefsName-encrypted",
                        MMKV.MULTI_PROCESS_MODE,
                        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
                    )
                } else {
                    MMKV.mmkvWithID(prefsName, MMKV.MULTI_PROCESS_MODE)
                }
            )

            iterations = bundle.getInt(ITERATIONS_KEY)
            require(iterations > 0) { "Must have at least 1 iteration" }
            repeat(iterations) { testKeyMap[it.toString()] = it }
            remoteMessenger =
                requireNotNull(bundle.getParcelable(REMOTE_MESSENGER_KEY)) { "No messenger provided" }

            currJob = serviceScope.launch(Dispatchers.Default) {
                var lastCount = 0
                while (true) {
                    ensureActive()
                    val keys = mmkv.allKeys() ?: continue
                    val now = SystemClock.elapsedRealtimeNanos()
                    if (keys.size != lastCount) {
                        keys.forEach { key ->
                            ensureActive()
                            withContext(Dispatchers.Main) {
                                if (testKeyMap.containsKey(key)) {
                                    val keyInt = testKeyMap[key] ?: -1
                                    val activityTestTime = withContext(Dispatchers.Default) {
                                        mmkv.decodeString(key)?.toLong() ?: -1L
                                    }
                                    if (activityTestTime > -1L && keyInt > -1) {
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
                                    } else {
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
                        lastCount = keys.size
                    }
                }
            }

        } ?: throw IllegalStateException("Service should not be restarted!")
        remoteMessenger.send(Message.obtain().apply { what = ACK_EVENT })
        isStarted = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        currJob?.cancel()
        remoteMessenger.send(Message.obtain().apply {
            what = RESULTS_EVENT
            data = bundleOf(RESULTS_KEY to LongArray(iterations) { timeCaptureMap[it] ?: -1L })
        })
        super.onDestroy()
    }
}