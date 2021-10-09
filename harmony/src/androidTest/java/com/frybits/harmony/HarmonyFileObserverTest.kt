package com.frybits.harmony

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HarmonyFileObserverTest {

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testMultipleHarmonyPrefsImplementation() = runBlocking {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        Log.e("Blah", "Manufacturer: ${Build.MANUFACTURER}")
        Log.e("Blah", "Model: ${Build.MODEL}")
        Log.e("Blah", "Brand: ${Build.BRAND}")
        Thread.currentThread().stackTrace.forEach { Log.e("Blah", it.className) }
        Log.e("Blah", "Is LG? ${Thread.currentThread().stackTrace.any { it.className.startsWith("com.lge") }}")

        val prefsJob = buildList {
            repeat(100) {
                val prefs = appContext.getHarmonySharedPreferences("prefs-${UUID.randomUUID()}")
                add(async(Dispatchers.IO) {
                    prefs.getString("blah-$it", null)
                    return@async
                })
            }
        }

        prefsJob.awaitAll()
        return@runBlocking
    }
}
