package com.frybits.harmony.test

import android.app.ActivityManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.Parcelable
import android.os.Process
import androidx.core.content.edit
import com.frybits.harmony.getHarmonySharedPreferences
import java.io.Serializable
import kotlin.random.Random
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

internal const val PREF_NAME = "prefName"
internal const val ALTERNATE_PROCESS_NAME = ":alternate"
internal const val MESSENGER_KEY = "messenger"
internal const val TEST_SIMPLE_KEY = "testSimple"
internal const val TEST_CLEAR_DATA_KEY = "testClearedData"
internal const val TRANSACTION_SIZE = 4 * 1024L
internal const val TRANSACTION_BATCH_SIZE = 250

abstract class HarmonyService : Service() {

    protected lateinit var testPrefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        assertTrue("Service is not running in alternate process!") { getServiceProcess().endsWith(ALTERNATE_PROCESS_NAME) }

        testPrefs = getHarmonySharedPreferences(PREF_NAME, TRANSACTION_SIZE, TRANSACTION_BATCH_SIZE)
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

class AlternateProcessService : HarmonyService() {

    private val sharedPreferenceChangeListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            val value = prefs.all[key]
            messenger.send(Message.obtain().apply {
                data = bundleOf(key to value)
            })
        }

    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        testPrefs.registerOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            messenger = intent.getParcelableExtra(MESSENGER_KEY)!!
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        testPrefs.unregisterOnSharedPreferenceChangeListener(sharedPreferenceChangeListener)
    }
}

class MassInputService : HarmonyService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            repeat(10_000) {
                testPrefs.edit { putString("$it", "${Random.nextLong()}") }
            }
        }
        return START_NOT_STICKY
    }
}

class ClearDataApplyService : HarmonyService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.hasExtra(TEST_SIMPLE_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_SIMPLE_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit { putString(TEST_SIMPLE_KEY, testString) }
            }

            if (intent.hasExtra(TEST_CLEAR_DATA_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_CLEAR_DATA_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit {
                    clear()
                    putString(TEST_CLEAR_DATA_KEY, testString)
                }
            }
        }
        return START_NOT_STICKY
    }
}

class ClearDataCommitService : HarmonyService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.hasExtra(TEST_SIMPLE_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_SIMPLE_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit(true) { putString(TEST_SIMPLE_KEY, testString) }
            }

            if (intent.hasExtra(TEST_CLEAR_DATA_KEY)) {
                val testString =
                    intent.getStringExtra(TEST_CLEAR_DATA_KEY) ?: throw IllegalArgumentException("No test string passed!")
                testPrefs.edit(true) {
                    clear()
                    putString(TEST_CLEAR_DATA_KEY, testString)
                }
            }
        }
        return START_NOT_STICKY
    }
}

// The Android `bundleOf()` doesn't allow for `null` keys, so had to recreate it to allow for it.
private fun bundleOf(vararg pairs: Pair<String?, Any?>): Bundle = Bundle(pairs.size).apply {
    for ((key, value) in pairs) {
        when (value) {
            null -> putString(key, null) // Any nullable type will suffice.

            // Scalars
            is Boolean -> putBoolean(key, value)
            is Byte -> putByte(key, value)
            is Char -> putChar(key, value)
            is Double -> putDouble(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is Short -> putShort(key, value)

            // References
            is Bundle -> putBundle(key, value)
            is CharSequence -> putCharSequence(key, value)
            is Parcelable -> putParcelable(key, value)

            // Scalar arrays
            is BooleanArray -> putBooleanArray(key, value)
            is ByteArray -> putByteArray(key, value)
            is CharArray -> putCharArray(key, value)
            is DoubleArray -> putDoubleArray(key, value)
            is FloatArray -> putFloatArray(key, value)
            is IntArray -> putIntArray(key, value)
            is LongArray -> putLongArray(key, value)
            is ShortArray -> putShortArray(key, value)

            // Reference arrays
            is Array<*> -> {
                val componentType = value::class.java.componentType!!
                @Suppress("UNCHECKED_CAST") // Checked by reflection.
                when {
                    Parcelable::class.java.isAssignableFrom(componentType) -> {
                        putParcelableArray(key, value as Array<Parcelable>)
                    }
                    String::class.java.isAssignableFrom(componentType) -> {
                        putStringArray(key, value as Array<String>)
                    }
                    CharSequence::class.java.isAssignableFrom(componentType) -> {
                        putCharSequenceArray(key, value as Array<CharSequence>)
                    }
                    Serializable::class.java.isAssignableFrom(componentType) -> {
                        putSerializable(key, value)
                    }
                    else -> {
                        val valueType = componentType.canonicalName
                        throw IllegalArgumentException(
                            "Illegal value array type $valueType for key \"$key\""
                        )
                    }
                }
            }

            // Last resort. Also we must check this after Array<*> as all arrays are serializable.
            is Serializable -> putSerializable(key, value)
        }
    }
}
