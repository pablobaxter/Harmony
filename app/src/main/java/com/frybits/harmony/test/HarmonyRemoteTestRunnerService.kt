package com.frybits.harmony.test

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import androidx.collection.SparseArrayCompat
import androidx.collection.set
import androidx.core.os.bundleOf
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.frybits.harmony.ACK_EVENT
import com.frybits.harmony.ITERATIONS_KEY
import com.frybits.harmony.LOG_EVENT
import com.frybits.harmony.LOG_KEY
import com.frybits.harmony.PREFS_NAME_KEY
import com.frybits.harmony.REMOTE_MESSENGER_KEY
import com.frybits.harmony.RESULTS_EVENT
import com.frybits.harmony.RESULTS_KEY
import com.frybits.harmony.USE_ENCRYPTION_KEY
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LOG_TAG = "Remote"

class HarmonyRemoteTestRunnerService : Service() {

    private val serviceScope = MainScope()
    private val testKeyMap = hashMapOf<String, Int>()
    private val timeCaptureMap = SparseArrayCompat<Long>()
    private lateinit var harmonyActivityPrefs: SharedPreferences

    private lateinit var remoteMessenger: Messenger
    private var iterations = 0

    private var isStarted = false

    private val onSharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val now = SystemClock.elapsedRealtimeNanos()
        require(prefs === harmonyActivityPrefs)
        serviceScope.launch {
            val keyInt = testKeyMap[key] ?: -1
            val activityTestTime = withContext(Dispatchers.Default) { prefs.getString(key, null)?.toLong() ?: -1L }
            if (activityTestTime > -1L && keyInt > -1) {
                if (timeCaptureMap.containsKey(keyInt)) {
                    withContext(Dispatchers.Default) {
                        remoteMessenger.send(Message.obtain().apply {
                            what = LOG_EVENT
                            data = bundleOf(LOG_KEY to LogEvent(
                                priority = Log.ERROR,
                                tag = LOG_TAG,
                                message = "Time result changed! Key=$key"
                            ))
                        })
                    }
                } else {
                    timeCaptureMap[keyInt] = now - activityTestTime
                }
            } else {
                withContext(Dispatchers.Default) {
                    remoteMessenger.send(Message.obtain().apply {
                        what = LOG_EVENT
                        data = bundleOf(LOG_KEY to LogEvent(
                            priority = Log.ERROR,
                            tag = LOG_TAG,
                            message = "Got default long value! Key=$key"
                        ))
                    })
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
            harmonyActivityPrefs = if (bundle.getBoolean(USE_ENCRYPTION_KEY)) {
                getEncryptedHarmonySharedPreferences(
                    prefsName,
                    MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } else {
                getHarmonySharedPreferences(prefsName)
            }

            harmonyActivityPrefs.registerOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)

            iterations = bundle.getInt(ITERATIONS_KEY)
            require(iterations > 0) { "Must have at least 1 iteration" }
            repeat(iterations) { testKeyMap[it.toString()] = it }
            remoteMessenger = requireNotNull(bundle.getParcelable(REMOTE_MESSENGER_KEY)) { "No messenger provided" }
        } ?: throw IllegalStateException("Service should not be restarted!")
        remoteMessenger.send(Message.obtain().apply { what = ACK_EVENT })
        isStarted = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent): IBinder? = null

    override fun onDestroy() {
        harmonyActivityPrefs.unregisterOnSharedPreferenceChangeListener(onSharedPreferenceChangeListener)
        remoteMessenger.send(Message.obtain().apply {
            what = RESULTS_EVENT
            data = bundleOf(RESULTS_KEY to LongArray(iterations) { timeCaptureMap[it] ?: -1L })
        })
        super.onDestroy()
    }
}