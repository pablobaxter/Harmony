package com.frybits.harmony

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.HandlerThread
import android.os.Messenger
import android.os.Process
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.test.Ignore
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/*
 *  Copyright 2020 Pablo Baxter
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * Created by Pablo Baxter (Github: pablobaxter)
 * https://github.com/pablobaxter/Harmony
 */

@RunWith(AndroidJUnit4::class)
class HarmonyProcessCommitTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE).edit(true) { clear() }

        // Ensure we are in the right process
        val pid = Process.myPid()
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        var processName: String? = null
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                processName = processInfo.processName
            }
        }
        requireNotNull(processName) { "Process name was null!" }
        assertFalse("Test is running in alternate process!") { processName.endsWith(ALTERNATE_PROCESS_NAME) }
    }

    @Test
    fun testMultiProcessPreferences_Int() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt()
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            val value = msg.data[key] as? Int
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putInt(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Long() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong()
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            val value = msg.data[key] as? Long
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putLong(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Float() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat()
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            val value = msg.data[key] as? Float
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putFloat(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Boolean() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean()
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            val value = msg.data[key] as? Boolean
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putBoolean(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_String() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}"
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            val value = msg.data[key] as? String
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putString(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_StringSet() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = ConcurrentHashMap(mutableMapOf(
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet()
        ))

        // Setup new looper
        val handlerThread = HandlerThread("test").apply { start() }

        // Deferrable to wait on while test completes
        val testDeferred = CompletableDeferred<Exception?>()

        // Setup a messenger to report results back from alternate process
        val messenger = Messenger(Handler(handlerThread.looper) { msg ->
            if (testDeferred.isCompleted) return@Handler true
            val key = msg.data.keySet().first()
            @Suppress("UNCHECKED_CAST") val value = msg.data[key] as? Set<String>
            val expected = testMap.remove(key) // This is what we should get
            if (expected != value) {
                testDeferred.complete(Exception("Values were not equal! expected: $expected, actual: $value"))
                return@Handler true
            }
            if (testMap.isEmpty()) testDeferred.complete(null)
            return@Handler true
        })

        val serviceIntent = Intent(application, AlternateProcessService::class.java).apply {
            putExtra(MESSENGER_KEY, messenger)
        }
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit(true) { putStringSet(k, v) }
        }

        runBlocking {
            withTimeout(1000) {
                testDeferred.await()
            }
        }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testOldDataIsNotReinserted() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)

        sharedPreferences.edit(true) { putString("test", "test") }

        assertTrue("Test insert failed") { sharedPreferences.contains("test") }

        val serviceIntent = Intent(application, MassInputService::class.java)
        serviceRule.startService(serviceIntent)

        // Give the service enough time to setup
        Thread.sleep(1000)

        assertTrue("Alternate service did not insert any data!") { sharedPreferences.all.size > 1 }

        sharedPreferences.edit(true) { remove("test") }

        assertFalse("Shared preferences still contains old data!") { sharedPreferences.contains("test") }

        // Give the preferences time make any in-flight commits
        Thread.sleep(1000)

        // Check again to ensure data was not re-inserted
        assertFalse("Shared preferences still contains old data!") { sharedPreferences.contains("test") }
    }

    @Test
    fun testDataChangesNotifiesAcrossProcesses() = runBlocking {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)

        // Start simple change notify test
        val simpleTestString = "simple${Random.nextInt(0, Int.MAX_VALUE)}"

        sharedPreferences.edit(true) { putString(TEST_SIMPLE_KEY, "blah") } // Pre-populate the data

        val simpleKeyChangedCompletableDeferred = CompletableDeferred<String?>()

        val simpleChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            assertTrue("Wrong Harmony object was returned") { sharedPreferences == prefs }
            assertTrue("Wrong key was emitted") { key == TEST_SIMPLE_KEY }
            simpleKeyChangedCompletableDeferred.complete(prefs.getString(TEST_SIMPLE_KEY, null)) // This should emit because the data changed
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(simpleChangeListener)

        val serviceIntent = Intent(application, ClearDataCommitService::class.java).apply {
            putExtra(TEST_SIMPLE_KEY, simpleTestString) // Pass a different string
        }
        serviceRule.startService(serviceIntent)

        withTimeout(1000) {
            val simpleResponse = simpleKeyChangedCompletableDeferred.await()
            assertEquals(simpleTestString, simpleResponse, "Simple change listener failed to emit the correct key!")
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(simpleChangeListener)
    }

    @Test
    @Ignore("Known issue with current implementation. https://github.com/pablobaxter/Harmony/issues/13")
    fun testClearedDataChangesNotifiesAcrossProcesses() = runBlocking {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE)

        // Start clear data + simple change notify test
        val clearDataTestString = "clearData${Random.nextInt(0, Int.MAX_VALUE)}"

        sharedPreferences.edit(true) { putString(TEST_CLEAR_DATA_KEY, clearDataTestString) } // Pre-populate the prefs with known data

        val clearDataKeyChangedCompletableDeferred = CompletableDeferred<String?>()

        val clearDataChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            assertTrue("Wrong Harmony object was returned") { sharedPreferences == prefs }
            assertTrue("Wrong key was emitted") { key == TEST_CLEAR_DATA_KEY } // We expect the change listener to emit, even though we have the same string. Prefs were cleared.
            clearDataKeyChangedCompletableDeferred.complete(prefs.getString(TEST_CLEAR_DATA_KEY, null))
        }

        sharedPreferences.registerOnSharedPreferenceChangeListener(clearDataChangeListener)

        val serviceIntent = Intent(application, ClearDataCommitService::class.java).apply {
            putExtra(TEST_CLEAR_DATA_KEY, clearDataTestString) // Pass the same string
        }
        serviceRule.startService(serviceIntent)

        withTimeout(1000) {
            val clearDataResponse = clearDataKeyChangedCompletableDeferred.await()
            assertEquals(clearDataTestString, clearDataResponse, "Cleared data change listener failed to emit the correct key!")
        }

        sharedPreferences.unregisterOnSharedPreferenceChangeListener(clearDataChangeListener)
    }
}
