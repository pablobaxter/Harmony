package com.frybits.harmony

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
