package com.frybits.harmonyprefs.test.bulkentry

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.util.Log
import com.frybits.harmonyprefs.ITERATIONS
import com.frybits.harmonyprefs.PREFS_NAME
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class HarmonyPrefsBulkReadService : Service() {

    private lateinit var harmonyActivityPrefs: SharedPreferences

    private var isStarted = false
    private var isRegistered = false

    private val testKeyArray = Array(ITERATIONS) { i -> "test$i" }

    override fun onCreate() {
        super.onCreate()
        harmonyActivityPrefs = getHarmonyPrefs(PREFS_NAME)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val startCommand = intent?.getBooleanExtra("START", false) ?: false
        val endCommand = intent?.getBooleanExtra("STOP", false) ?: false
        if (!isStarted && startCommand) {
            Log.i(
                "Trial",
                "${this::class.java.simpleName}: Starting service to read changes from main process!"
            )
            isStarted = true
            isRegistered = true
            val measure = measureTimeMillis {
                testKeyArray.forEach { s ->
                    if (harmonyActivityPrefs.getLong(s, -1L) == -1L) {
                        Log.e("Trial", "${this::class.java.simpleName}: Key $s was not found!")
                    }
                }
            }
            Log.i("Trial", "${this::class.java.simpleName}: Time to read $ITERATIONS items: $measure ms!")
        }
        if (isStarted && endCommand) {
            Log.i("Trial", "${this::class.java.simpleName}: Stopping test!")
            isStarted = false
            isRegistered = false
        }
        return START_NOT_STICKY
    }
}
