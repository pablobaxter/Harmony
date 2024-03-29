package com.frybits.harmony.test

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import android.os.SystemClock
import android.system.Os
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.ServiceTestRule
import com.frybits.harmony.HarmonyLog
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.internal.withFileLock
import com.frybits.harmony.setHarmonyLog
import com.frybits.harmony.withFileLock
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.util.concurrent.Executors
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"
private const val PREFS_DATA = "prefs.data"
private const val PREFS_BACKUP = "prefs.backup"
private const val PREFS_TRANSACTION_DATA = "prefs.transaction.data"

private const val TEST_PREFS = "testPrefs"

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
    byteArrayOf(126, -91, -85, 82, 118, 5, 86, 77, 106, -113, 39, 6, -52, -29, -123, -107, -55, 0, 0, 0, 0, 0, 5, -37, -6, -121, 1, 0, 0, 0, 7, 116, 101, 115, 116, 75, 101, 121, 4, 0, 0, 0, 9, 116, 101, 115, 116, 86, 97, 108, 117, 101, 0, 0, 0, 0, 0, 0, 7, 65, 17, 19)

// Byte array that is the following transaction: UPDATE(null:"test", cleared=false)
private val TEST_NULL_KEY_TRANSACTION_DATA =
    byteArrayOf(126, -38, 33, -55, 56, 127, -87, 74, 97, -95, -111, -49, 53, -58, 66, 122, -45, 0, 0, 0, 77, -69, -4, 30, -15, -8, 1, 0, 0, 0, 0, 4, 0, 0, 0, 4, 116, 101, 115, 116, 0, 0, 0, 0, 0, 0, -128, -103, 14, -83)

// Byte array that will cause an OOM error
private val TEST_OOM_TRANSACTION_DATA =
    byteArrayOf(126, -91, -85, 82, 118, 5, 86, 77, 106, -113, 39, 6, -52, -29, -123, -107, -55, 0, 0, 0, 0, 0, 5, -37, -6, -121, 1, 127, -1, -1, -1)

@RunWith(AndroidJUnit4::class)
class HarmonyFileTest {

    @get:Rule
    val serviceRule = ServiceTestRule()

    @Before
    fun setup() {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        File(appContext.filesDir, HARMONY_PREFS_FOLDER).deleteRecursively()
    }

    @Test
    fun testNullKeyTransaction() {
        // Test Prep
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val sharedPreferences = appContext.getHarmonySharedPreferences(TEST_PREFS)
        sharedPreferences.all // Dummy call to wait for shared prefs to load
        val harmonyFolder = File(appContext.filesDir, HARMONY_PREFS_FOLDER)
        harmonyFolder.mkdirs()
        val prefsFolder = File(harmonyFolder, TEST_PREFS).apply { mkdirs() }
        val transactionFile = File(prefsFolder, PREFS_TRANSACTION_DATA)
        transactionFile.writeBytes(TEST_NULL_KEY_TRANSACTION_DATA)

        assertEquals(TEST_NULL_KEY_TRANSACTION_DATA.size.toLong(), transactionFile.length(), "Transaction data was modified!")

        Thread.sleep(1000) // Give Harmony time to replicate data from external change

        assertEquals("test", sharedPreferences.getString(null, null), "Value was not stored!")
    }

    @Test
    fun testCorruptedFile() {
        // Test Prep
        val appContext = ApplicationProvider.getApplicationContext<Context>()
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
        val appContext = ApplicationProvider.getApplicationContext<Context>()
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
        val appContext = ApplicationProvider.getApplicationContext<Context>()
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
    fun testOOMTransactionFile() {
        // Test Prep
        val appContext = ApplicationProvider.getApplicationContext<Context>()

        val lock = CountDownLatch(1)

        var didGetException = false

        setHarmonyLog(object : HarmonyLog {
            override fun log(priority: Int, msg: String) {
                // Do nothing
            }

            override fun recordException(throwable: Throwable) {
                if (throwable is OutOfMemoryError) {
                    didGetException = true
                    lock.countDown()
                }
            }
        })

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

        transactionFile.appendBytes(TEST_OOM_TRANSACTION_DATA) // Introduce corrupted data that would cause OOM

        // Quick and easy way to ensure we don't wait longer than 1 second for replication or the countdown latch
        val currTime = SystemClock.elapsedRealtime()
        lock.await(1, TimeUnit.SECONDS)
        Thread.sleep((1000 - (SystemClock.elapsedRealtime() - currTime)).coerceAtLeast(0)) // Give Harmony time to replicate data from external change

        assertTrue(didGetException, "Did not receive OOM error!")

        // Data should not be changed
        assertEquals("testValue", sharedPreferences.getString("testKey", null), "Value was not stored!")

        // Transaction file should be cleared
        assertEquals(0L, transactionFile.length(), "Transaction data was not cleared!")
    }

    @Test
    fun testBackupFileRecovery() {
        // Test Prep
        val appContext = ApplicationProvider.getApplicationContext<Context>()
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
        val application = ApplicationProvider.getApplicationContext<Context>()

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
            testFile.withFileLock(true) {}
        }

        Thread.sleep(1000)

        assertTrue("Shared job has not completed, when it should be!") { sharedAsync.isDone }

        val lockedAsync = executor.submit {
            testFile.withFileLock {}
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
        val application = ApplicationProvider.getApplicationContext<Context>()

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
            testFile.withFileLock(true) {}
        }

        Thread.sleep(1000)

        assertFalse("Shared job was completed, when it was not supposed to be yet!") { sharedAsync.isDone }

        val lockedAsync = executor.submit {
            testFile.withFileLock {}
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

    @Test
    fun testBadFileDescriptorLockReleaseCrash() {
        // Setup test
        val application = ApplicationProvider.getApplicationContext<Context>()

        val testFile = File(application.filesDir, TEST_PREFS)

        val randomAccessFile = RandomAccessFile(testFile, "rw")

        // Ensure the FileDescriptor is valid
        assertTrue { randomAccessFile.fd.valid() }

        // This is expected to fail with only an IOException. Any other error is a failure
        val exception = assertFailsWith<IOException> {
            randomAccessFile.withFileLock(shared = true) {
                Os.close(randomAccessFile.fd) // Simulate the file descriptor closing unexpectedly
            }
        }
        assertEquals("Unable to release FileLock!", exception.message)

        // FileDescriptor should be closed here
        assertFalse { randomAccessFile.fd.valid() }

        // Handled safely inside the 'withFileLock', but forcing the close here
        assertFailsWith<IOException> {
            randomAccessFile.close()
        }

        // This should not throw any exception
        randomAccessFile.close()
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
