package com.frybits.harmony

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Process
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.Exception
import kotlin.random.Random
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val PREF_NAME = "prefName"
private const val ALTERNATE_PROCESS_NAME = ":alternate"
private const val MESSENGER_KEY = "messenger"

@RunWith(AndroidJUnit4::class)
class HarmonyProcessTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun setup() {
        // Context of the app under test.
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        appContext.getHarmonySharedPreferences(PREF_NAME).edit(true) { clear() }

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
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt(),
            "test-${Random.nextInt()}" to Random.nextInt()
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putInt(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Long() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong(),
            "test-${Random.nextInt()}" to Random.nextLong()
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putLong(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Float() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat(),
            "test-${Random.nextInt()}" to Random.nextFloat()
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putFloat(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_Boolean() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean(),
            "test-${Random.nextInt()}" to Random.nextBoolean()
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putBoolean(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_String() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}",
            "test-${Random.nextInt()}" to "${Random.nextInt()}"
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putString(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testMultiProcessPreferences_StringSet() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        // 5 entries to test
        val testMap = mutableMapOf(
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet(),
            "test-${Random.nextInt()}" to Array(5) { "${Random.nextInt()}" }.toSet()
        )

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

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)
        testMap.forEach { (k, v) ->
            sharedPreferences.edit { putStringSet(k, v) }
        }

        runBlocking { testDeferred.await() }?.let { throw it }
        assertTrue("Test Map was not empty!") { testMap.isEmpty() }
    }

    @Test
    fun testOldDataIsNotReinserted() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val sharedPreferences = application.getHarmonySharedPreferences(PREF_NAME)

        sharedPreferences.edit { putString("test", "test") }

        assertTrue("Test insert failed") { sharedPreferences.contains("test") }

        val serviceIntent = Intent(application, MassInputService::class.java)
        serviceRule.startService(serviceIntent)

        Thread.sleep(100)

        assertTrue("Alternate service did not insert any data!") { sharedPreferences.all.size > 1 }

        sharedPreferences.edit { remove("test") }

        // Give the service enough time to setup
        Thread.sleep(1000)

        assertFalse("Shared preferences still contains old data!") { sharedPreferences.contains("test") }
    }
}

class AlternateProcessService : Service() {

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            val value = prefs.all[key]
            messenger.send(Message.obtain().apply {
                data = bundleOf(key to value)
            })
        }

    private lateinit var testPrefs: SharedPreferences
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        assertTrue("Service is not running in alternate process!") { getServiceProcess().endsWith(ALTERNATE_PROCESS_NAME) }

        testPrefs = getHarmonySharedPreferences(PREF_NAME)
        testPrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            messenger = intent.getParcelableExtra(MESSENGER_KEY)!!
        }
        return START_NOT_STICKY
    }

    // Binder cannot be null. Returning NoOp instead
    override fun onBind(intent: Intent?): IBinder? = Binder()

    override fun onDestroy() {
        super.onDestroy()
        testPrefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    private fun getServiceProcess(): String {
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return ""
    }
}

class MassInputService : Service() {

    private lateinit var testPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        assertTrue("Service is not running in alternate process!") { getServiceProcess().endsWith(ALTERNATE_PROCESS_NAME) }

        testPrefs = getHarmonySharedPreferences(PREF_NAME)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            repeat(10_000) {
                testPrefs.edit { putString("$it", "${Random.nextLong()}") }
            }
        }
        return START_NOT_STICKY
    }

    // Binder cannot be null. Returning NoOp instead
    override fun onBind(intent: Intent?): IBinder? = Binder()

    private fun getServiceProcess(): String {
        val pid = Process.myPid()
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (processInfo in manager.runningAppProcesses) {
            if (processInfo.pid == pid) {
                return processInfo.processName
            }
        }
        return ""
    }
}
