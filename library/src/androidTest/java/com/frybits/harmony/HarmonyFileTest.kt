package com.frybits.harmony

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ServiceTestRule
import com.frybits.harmony.internal.withFileLock
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"
private const val PREFS_DATA = "prefs.data"
private const val PREFS_BACKUP = "prefs.backup"
private const val PREFS_TRANSACTION_DATA = "prefs.transaction.data"

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

// Byte array that is the following transaction: UPDATE("testKey":"testValue", cleared=false)
private val TEST_TRANSACTION_DATA =
    byteArrayOf(127, -91, -85, 82, 118, 5, 86, 77, 106, -113, 39, 6, -52, -29, -123, -107, -55, 0, 0, 0, 0, 0, 5, -37, -6, -121, 1, 0, 7, 116, 101, 115, 116, 75, 101, 121, 4, 0, 9, 116, 101, 115, 116, 86, 97, 108, 117, 101, 0, 0, 0, 0, 0, 0, -40, 24, 17, 20)

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
    fun testCorruptedTransactionFile() {
        // Test Prep
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonySharedPreferences(TEST_PREFS)
        sharedPreferences.all // Dummy call to wait for shared prefs to load
        val harmonyFolder = File(appContext.filesDir, HARMONY_PREFS_FOLDER)
        harmonyFolder.mkdirs()
        val prefsFolder = File(harmonyFolder, TEST_PREFS).apply { mkdirs() }
        val transactionFile = File(prefsFolder, PREFS_TRANSACTION_DATA)
        val randomBytes = Random.nextBytes(10)
        transactionFile.writeBytes(randomBytes) // Triggers the file observer

        Thread.sleep(1000)

        // Harmony should clear the bad transaction data that came in by deleting the entire transaction file
        assertEquals(0L, transactionFile.length(), "Transaction file was not cleared correctly")
        assertTrue("Shared preferences were not empty!") { sharedPreferences.all.isEmpty() }

        val tmpFile = File.createTempFile("blah", "foo")
        tmpFile.writeBytes(randomBytes)
        tmpFile.renameTo(transactionFile) // This avoids calling the file observer

        assertEquals(10L, transactionFile.length(), "Transaction file was not updated correctly")

        // Attempt to put a new value in after corrupt data
        sharedPreferences.edit { putString("testKey", "testValue") }

        Thread.sleep(1000) // Sleep to give Harmony chance to cleanup corrupt data

        assertEquals(0L, transactionFile.length(), "Transaction file was not cleared correctly")
        assertNull(sharedPreferences.getString("testKey", null), "Value data was kept!")
    }

    @Test
    fun testPartialCorruptedTransactionFile() {
        // Test Prep
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sharedPreferences = appContext.getHarmonySharedPreferences(TEST_PREFS)
        sharedPreferences.all // Dummy call to wait for shared prefs to load
        val harmonyFolder = File(appContext.filesDir, HARMONY_PREFS_FOLDER)
        harmonyFolder.mkdirs()
        val prefsFolder = File(harmonyFolder, TEST_PREFS).apply { mkdirs() }
        val transactionFile = File(prefsFolder, PREFS_TRANSACTION_DATA)
        transactionFile.writeBytes(TEST_TRANSACTION_DATA)

        assertEquals(TEST_TRANSACTION_DATA.size.toLong(), transactionFile.length(), "Transaction data was modified!")

        Thread.sleep(1000) // Give Harmony time to replicate data from external change

        assertEquals("testValue", sharedPreferences.getString("testKey", null), "Value was not stored!")

        transactionFile.appendBytes(Random.nextBytes(10)) // Introduce corrupted data

        Thread.sleep(1000) // Give Harmony time to replicate data from external change

        // Data should not be changed
        assertEquals("testValue", sharedPreferences.getString("testKey", null), "Value was not stored!")

        // Transaction file should be cleared
        assertEquals(0L, transactionFile.length(), "Transaction data was not cleared!")
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

class SharedFileLockService : FileLockService(true)
class LockedFileLockService : FileLockService(false)

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
