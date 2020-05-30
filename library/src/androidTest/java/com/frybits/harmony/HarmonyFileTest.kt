package com.frybits.harmony

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.frybits.harmony.core.withFileLock
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"
private const val PREFS_DATA = "prefs.data"
private const val PREFS_BACKUP = "prefs.backup"

private const val TEST_PREFS = "testPrefs"
private const val ALTERNATE_PROCESS_NAME = ":alternate"

private const val TEST_PREFS_JSON = """
    {
        "metaData": {
            "name": "$TEST_PREFS"
        },
        "data": [
            {
                "type": "string",
                "key": "blah",
                "value": "bar"
            },
            {
                "type": "boolean",
                "key": "isSomething",
                "value": true
            },
            {
                "type": "long",
                "key": "time",
                "value": 4530349853809348080
            },
            {
                "type": "int",
                "key": "count",
                "value": 3
            }
        ]
    }
"""

private val TEST_PREFS_MAP = mapOf<String, Any?>(
    "blah" to "bar",
    "isSomething" to true,
    "time" to 4530349853809348080L,
    "count" to 3
)

@RunWith(AndroidJUnit4::class)
class HarmonyFileTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun setup() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        File(appContext.filesDir, HARMONY_PREFS_FOLDER).deleteRecursively()
    }

    @Test
    fun testCorruptedFile() {
        // Test Prep
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val harmonyFolder = File(appContext.filesDir, HARMONY_PREFS_FOLDER)
        harmonyFolder.mkdirs()
        val prefsFolder = File(harmonyFolder, TEST_PREFS).apply { mkdirs() }
        val prefsDataFile = File(prefsFolder, PREFS_DATA)
        prefsDataFile.writeBytes(Random.nextBytes(1000))

        // Test
        val sharedPreferences = appContext.getHarmonySharedPreferences(TEST_PREFS)
        assertTrue("Shared preferences were not empty!") { sharedPreferences.all.isEmpty() }

        val editor = sharedPreferences.edit()
        val result = editor.putString("testKey", "testValue").commit()
        assertTrue("Key was not stored in shared prefs") { result }
        assertEquals("testValue", sharedPreferences.getString("testKey", null), "Value was not stored!")
    }

    @Test
    fun testBackupFileRecovery() {
        // Test Prep
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val harmonyFolder = File(appContext.filesDir, HARMONY_PREFS_FOLDER)
        harmonyFolder.mkdirs()
        val prefsFolder = File(harmonyFolder, TEST_PREFS).apply { mkdirs() }

        // Create an invalid file
        val prefsDataFile = File(prefsFolder, PREFS_DATA)
        prefsDataFile.writeBytes(Random.nextBytes(1000))

        // Create the backup file
        val backupPrefsDataFile = File(prefsFolder, PREFS_BACKUP)
        backupPrefsDataFile.writeText(TEST_PREFS_JSON)

        // Test
        val sharedPreferences = appContext.getHarmonySharedPreferences(TEST_PREFS)
        assertEquals(TEST_PREFS_MAP, sharedPreferences.all, "Prefs map was not equal!")

        assertFalse("Backup file should not exist!") { backupPrefsDataFile.exists() }
        assertTrue("Prefs file should exist!") { prefsDataFile.exists() }

        val editor = sharedPreferences.edit()
        val committed = editor.putBoolean("someValue", true).commit()

        assertTrue("Data was not committed!") { committed }
        assertFalse("Backup file should not exist!") { backupPrefsDataFile.exists() }
        assertTrue("Prefs file should still exist!") { prefsDataFile.exists() }

        assertEquals(TEST_PREFS_MAP + mapOf("someValue" to true), sharedPreferences.all, "Prefs map was not equal!")
    }

    @Test
    fun testSharedFileLock() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val executor = Executors.newCachedThreadPool()

        val serviceIntent = Intent(application, SharedFileLockService::class.java).apply {
            putExtra("test", "start")
            putExtra("shared", true)
        }
        serviceRule.startService(serviceIntent)

        val testFile = File(application.filesDir, TEST_PREFS)

        // Give service time to setup
        Thread.sleep(1000)

        val sharedAsync = executor.submit {
            testFile.withFileLock(true) {

            }
        }

        Thread.sleep(1000)

        assertTrue("Shared job has not completed, when it should be!") { sharedAsync.isDone }

        val lockedAsync = executor.submit {
            testFile.withFileLock {

            }
        }

        Thread.sleep(1000)

        assertFalse("Locked job was completed, when it was not supposed to be yet!") { lockedAsync.isDone }

        val stopServiceIntent = Intent(application, SharedFileLockService::class.java).apply {
            putExtra("test", "stop")
            putExtra("shared", false)
        }
        serviceRule.startService(stopServiceIntent)

        Thread.sleep(1000)

        assertTrue("Locked job has not completed, when it should be!") { lockedAsync.isDone }
    }

    @Test
    fun testUnsharedFileLock() {
        // Setup test
        val application = InstrumentationRegistry.getInstrumentation().targetContext

        val executor = Executors.newCachedThreadPool()

        val serviceIntent = Intent(application, LockedFileLockService::class.java).apply {
            putExtra("test", "start")
            putExtra("shared", true)
        }
        serviceRule.startService(serviceIntent)

        val testFile = File(application.filesDir, TEST_PREFS)

        // Give service time to setup
        Thread.sleep(1000)

        val sharedAsync = executor.submit {
            testFile.withFileLock(true) {

            }
        }

        Thread.sleep(1000)

        assertFalse("Shared job was completed, when it was not supposed to be yet!") { sharedAsync.isDone }

        val lockedAsync = executor.submit {
            testFile.withFileLock {

            }
        }

        Thread.sleep(1000)

        assertFalse("Locked job was completed, when it was not supposed to be yet!") { lockedAsync.isDone }

        val stopServiceIntent = Intent(application, LockedFileLockService::class.java).apply {
            putExtra("test", "stop")
            putExtra("shared", false)
        }
        serviceRule.startService(stopServiceIntent)

        Thread.sleep(1000)

        assertTrue("Locked job has not completed, when it should be!") { lockedAsync.isDone }
        assertTrue("Shared job has not completed, when it should be!") { sharedAsync.isDone }
    }
}

class SharedFileLockService: FileLockService(true)
class LockedFileLockService: FileLockService(false)

abstract class FileLockService(private val shared: Boolean) : Service() {

    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    override fun onCreate() {
        super.onCreate()
        assertTrue("Service is not running in alternate process!") { getServiceProcess().endsWith(ALTERNATE_PROCESS_NAME) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val action = intent.getStringExtra("test")
            if (action == "start") {
                channel = RandomAccessFile(File(filesDir, TEST_PREFS), "rw").channel
                lock = channel?.lock(0L, Long.MAX_VALUE, shared)
            }
            if (action == "stop") {
                assertNotNull(channel, "FileChannel was null!")
                assertNotNull(lock, "FileLock was null!") {
                    assertTrue("FileLock was not valid!") { it.isValid }
                    assertEquals(shared, it.isShared, "FileLock was not shared!")
                }
                lock?.release()
                channel?.close()
            }
        }
        return START_NOT_STICKY
    }

    // Binder cannot be null. Returning NoOp instead
    override fun onBind(intent: Intent?): IBinder? = Binder()

    override fun onDestroy() {
        super.onDestroy()
        lock?.let {
            if (it.isValid) it.release()
        }
        channel?.close()
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
