package com.frybits.harmony.test

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.frybits.harmony.getHarmonySharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HarmonyFileObserverTest {

    // This tests for regression on a bug specific to LG devices running Android 9 and below
    // See https://github.com/pablobaxter/Harmony/issues/38
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testMultipleHarmonyPrefsImplementation() = runBlocking {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext

        val prefsJob = buildList {
            repeat(100) {
                val prefs = appContext.getHarmonySharedPreferences("prefs-${UUID.randomUUID()}")
                add(async(Dispatchers.IO) {
                    prefs.getString("test-$it", null)
                    return@async
                })
            }
        }

        prefsJob.awaitAll()
        return@runBlocking
    }
}
