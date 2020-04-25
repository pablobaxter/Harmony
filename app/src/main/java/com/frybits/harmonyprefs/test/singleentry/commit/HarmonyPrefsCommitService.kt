package com.frybits.harmonyprefs.test.singleentry.commit

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

abstract class HarmonyBasePrefsCommitService(private val servicePrefs: String) : Service() {

    private lateinit var harmonyActivityPrefs: SharedPreferences
    private lateinit var harmonyServicePrefs: SharedPreferences

    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val now = SystemClock.elapsedRealtime()
        require(prefs === harmonyActivityPrefs)
        val activityTestTime = prefs.getLong(key, -1L)
        require(activityTestTime > -1L)
        Log.d("Trial", "${this::class.java.simpleName}: Time to receive $key: ${now - activityTestTime}")
        Log.d("Trial", "${this::class.java.simpleName}: Sending response with key: $key")
        timeCaptureList.add(now - activityTestTime)
        harmonyServicePrefs.edit(true) { putLong(key, SystemClock.elapsedRealtime()) }
    }

    private var isStarted = false
    private var isRegistered = false

    private val timeCaptureList = arrayListOf<Long>()

    override fun onCreate() {
        super.onCreate()
        harmonyActivityPrefs = getHarmonyPrefs("ActivityPrefs")
        harmonyServicePrefs = getHarmonyPrefs(servicePrefs)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startCommand = intent?.getBooleanExtra("START", false) ?: false
        val endCommand = intent?.getBooleanExtra("STOP", false) ?: false
        if (!isStarted && startCommand) {
            timeCaptureList.clear()
            Log.d("Trial", "${this::class.java.simpleName}: Starting test!")
            isStarted = true
            isRegistered = true
            harmonyActivityPrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        }
        if (isStarted && endCommand) {
            Log.d("Trial", "${this::class.java.simpleName}: Stopping test!")
            Log.d("Trial", "${this::class.java.simpleName}: Capture count: ${timeCaptureList.size}")
            Log.d("Trial", "${this::class.java.simpleName}: Average receive time: ${timeCaptureList.average()} ms")
            Log.d("Trial", "${this::class.java.simpleName}: Max receive time: ${timeCaptureList.max()} ms")
            Log.d("Trial", "${this::class.java.simpleName}: Min receive time: ${timeCaptureList.min()} ms")
            isStarted = false
            harmonyActivityPrefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
            isRegistered = false
        }
        return START_NOT_STICKY
    }
}

class HarmonyPrefsCommitFooService : HarmonyBasePrefsCommitService("fooServicePrefs")
class HarmonyPrefsCommitBarService : HarmonyBasePrefsCommitService("barServicePrefs")
