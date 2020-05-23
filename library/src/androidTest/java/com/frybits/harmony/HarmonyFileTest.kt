package com.frybits.harmony

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Created by Pablo Baxter (Github: pablobaxter)
 */

private const val HARMONY_PREFS_FOLDER = "harmony_prefs"
private const val PREFS_DATA = "prefs.data"
private const val PREFS_BACKUP = "prefs.backup"

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

@RunWith(AndroidJUnit4::class)
class HarmonyFileTest {

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
}
