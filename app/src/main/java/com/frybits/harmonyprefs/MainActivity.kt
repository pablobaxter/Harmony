package com.frybits.harmonyprefs

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.system.measureTimeMillis

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class MainActivity : AppCompatActivity() {

    private lateinit var harmonyPrefs1: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        harmonyPrefs1 = getHarmonyPrefs("Blah")
        sendBroadcast(Intent(this, CountReceiver::class.java))
        GlobalScope.launch(Dispatchers.Default) {
            delay(5000)
            Log.d("Trial", "Start MainActivity loop...")
            val measuredTime = measureTimeMillis {
                repeat(1000) {
                    if (it == 0) Log.d("Trial", "Sending first event!")
                    if (it == 999) Log.d("Trial", "Sending last event!")
//                    Log.d("Trial", "Sending $it")
                    harmonyPrefs1.edit().putLong("test", it.toLong()).apply()
                    if (it < 999) delay(1)
                }
            }
            Log.d("Trial", "End MainActivity loop: Duration=$measuredTime")
        }
    }
}
