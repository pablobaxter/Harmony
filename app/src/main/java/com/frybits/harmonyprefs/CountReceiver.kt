package com.frybits.harmonyprefs

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.frybits.harmonyprefs.library.Harmony.Companion.getHarmonyPrefs

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

class CountReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val blah = context.getHarmonyPrefs("Blah")
        Log.d("Trial", blah.contains("test").toString())
        blah.edit().clear().apply()
        Log.d("Trial", blah.contains("test").toString())
        var gotFirstValue = false
        var startTime = 0L
        Log.d("Trial", "Start CountReceiver loop...")
        var currCount = -1L
        do {
            val i = blah.getLong("test", -1L)
            if (!gotFirstValue && i >= 0) {
                Log.d("Trial", "Got first event! Event: $i")
                startTime = System.currentTimeMillis()
                gotFirstValue = true
                currCount = i
            }
            if (currCount != i) {
                Thread.sleep(5)
                currCount = i
            }
            if (i == 99L) {
                Log.d("Trial", "Got last event! Event: $i")
                Log.d("Trial", "End CountReceiver loop: Duration=${System.currentTimeMillis() - startTime}")
            }
            Log.d("Blah", "Count: $i")
        } while (i != 99L)
    }
}
