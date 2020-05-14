package com.frybits.harmonyprefs.test.singleentry

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.frybits.harmonyprefs.ITERATIONS
import com.frybits.harmonyprefs.PREFS_NAME
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonySharedPreferences
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsReceiveService : Service() {

    private lateinit var harmonyActivityPrefs: SharedPreferences

    // This listener receives changes that occur to this shared preference from any process, not just this one.
    private val sharedPreferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        val now = SystemClock.elapsedRealtime()
        require(prefs === harmonyActivityPrefs)
        val activityTestTime = prefs.getLong(key, -1L)
        if (activityTestTime > -1L) {
            timeCaptureList.add(now - activityTestTime)
        } else {
            Log.e("Trial", "${this::class.java.simpleName}: Got default long value!")
        }
    }

    private var isStarted = false
    private var isRegistered = false

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }

    private val timeCaptureList = arrayListOf<Long>()

    override fun onCreate() {
        super.onCreate()
        harmonyActivityPrefs = getHarmonySharedPreferences(PREFS_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startCommand = intent?.getBooleanExtra("START", false) ?: false
        val endCommand = intent?.getBooleanExtra("STOP", false) ?: false
        if (!isStarted && startCommand) {
            timeCaptureList.clear()
            Log.i("Trial", "${this::class.java.simpleName}: Starting service to receive from main process!")
            isStarted = true
            isRegistered = true
            harmonyActivityPrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
        }
        if (isStarted && endCommand) {
            val measure = measureTimeMillis {
                testKeyArray.forEach { s ->
                    if (harmonyActivityPrefs.getLong(s, -1L) == -1L) {
                        Log.e("Trial", "${this::class.java.simpleName}: Key $s was not found!")
                    }
                }
            }
            Log.i("Trial", "${this::class.java.simpleName}: Time to read $ITERATIONS items: $measure ms!")
            Log.i("Trial", "${this::class.java.simpleName}: Stopping test!")
            Log.i("Trial", "${this::class.java.simpleName}: Capture count: ${timeCaptureList.size}, expecting $ITERATIONS")
            Log.i("Trial", "${this::class.java.simpleName}: Average time to receive from main process: ${timeCaptureList.average()} ms")
            Log.i("Trial", "${this::class.java.simpleName}: Max time to receive from main process: ${timeCaptureList.max()} ms")
            Log.i("Trial", "${this::class.java.simpleName}: Min time to receive from main process: ${timeCaptureList.min()} ms")
            isStarted = false
            harmonyActivityPrefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
            isRegistered = false
        }
        return START_NOT_STICKY
    }
}
