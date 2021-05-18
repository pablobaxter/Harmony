package com.frybits.harmony.test

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.Messenger
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.frybits.harmony.ACK_EVENT
import com.frybits.harmony.ITERATIONS_KEY
import com.frybits.harmony.LOG_EVENT
import com.frybits.harmony.LOG_KEY
import com.frybits.harmony.PREFS_NAME_KEY
import com.frybits.harmony.REMOTE_MESSENGER_KEY
import com.frybits.harmony.RESULTS_EVENT
import com.frybits.harmony.RESULTS_KEY
import com.frybits.harmony.USE_ENCRYPTION_KEY
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.UUID
import kotlin.system.measureNanoTime

sealed class TestRunner(val testRunnerId: UUID = UUID.randomUUID()) {

    private val runTestMutex = Mutex()
    private val _logFlow = MutableSharedFlow<LogEvent>()
    val logFlow = _logFlow.asSharedFlow()

    abstract val source: String

    suspend fun runTest() {
        runTestMutex.withLock {
            onRunTest()
        }
    }

    protected abstract suspend fun onRunTest()

    protected abstract suspend fun reset()

    abstract suspend fun getTestResults(parentId: UUID): TestEntityWithData

    protected suspend fun logEvent(priority: Int, tag: String, message: String) {
        logEvent(
            LogEvent(
                priority = priority,
                tag = tag,
                message = message
            )
        )
    }

    protected suspend fun logEvent(logEvent: LogEvent) {
        _logFlow.emit(logEvent)
    }
}

abstract class SharedPreferencesTestRunner(
    private val iterations: Int,
    private val isAsync: Boolean,
    private val isEncrypted: Boolean
) : TestRunner() {

    protected abstract val sharedPreferences: SharedPreferences
    protected val testData = arrayListOf<TestData>()

    protected abstract suspend fun awaitUntilReady()

    override suspend fun onRunTest() {
        reset()
        withTimeout(3000) {
            awaitUntilReady()
        }
        withContext(Dispatchers.Default) {
            val testKeyArray = Array(iterations) { it.toString() }
            runWriteTests(testKeyArray)
            runReadTests(testKeyArray)
        }
        awaitResults()
    }

    override suspend fun reset() {
        logEvent(Log.INFO, LOG_TAG, "resetting test")
        onReset()
    }

    protected abstract suspend fun onReset()

    protected abstract suspend fun awaitResults()

    override suspend fun getTestResults(parentId: UUID): TestEntityWithData {
        awaitResults()
        return withContext(Dispatchers.Main) {
            return@withContext TestEntityWithData(
                entity = TestRun(
                    testEntityId = testRunnerId,
                    parentTestSuiteId = parentId,
                    numIterations = iterations,
                    isAsync = isAsync,
                    isEncrypted = isEncrypted,
                    source = source
                ),
                testDataList = testData
            )
        }
    }

    private suspend fun runWriteTests(testKeyArray: Array<String>) {
        logEvent(Log.INFO, LOG_TAG, "starting write tests")
        withContext(Dispatchers.Default) {
            val writeLongArray = LongArray(iterations)

            // Begin write tests
            val editor = sharedPreferences.edit()
            repeat(iterations) { testNum ->
                ensureActive()
                val value = SystemClock.elapsedRealtimeNanos().toString()
                val writeTime = measureNanoTime {
                    editor.putString(testKeyArray[testNum], value)
                    if (isAsync) {
                        editor.apply()
                    } else {
                        editor.commit()
                    }
                }
                writeLongArray[testNum] = writeTime
            }

            // Set write data
            withContext(Dispatchers.Main) {
                testData.add(
                    WriteTestData(
                        parentTestEntityId = testRunnerId,
                        results = writeLongArray
                    )
                )
            }
            logEvent(Log.INFO, LOG_TAG, "avg write: ${writeLongArray.average() / 1_000_000} ms")
        }
        logEvent(Log.INFO, LOG_TAG, "ending write tests")
    }

    private suspend fun runReadTests(testKeyArray: Array<String>) {
        logEvent(Log.INFO, LOG_TAG, "starting read tests")
        withContext(Dispatchers.Default) {
            val readLongArray = LongArray(iterations)

            // Begin read tests
            repeat(iterations) { testNum ->
                ensureActive()
                val readTime =
                    measureNanoTime { sharedPreferences.getString(testKeyArray[testNum], null) }
                readLongArray[testNum] = readTime
            }

            // Set read data
            withContext(Dispatchers.Main) {
                testData.add(
                    ReadTestData(
                        parentTestEntityId = testRunnerId,
                        results = readLongArray
                    )
                )
            }
            logEvent(Log.INFO, LOG_TAG, "avg read: ${readLongArray.average() / 1_000_000} ms")
        }
        logEvent(Log.INFO, LOG_TAG, "ending write tests")
    }

    companion object {
        private const val LOG_TAG = "SharedPreferencesTestRunner"
    }
}

class VanillaSharedPreferencesTestRunner(
    context: Context,
    prefsName: String,
    iterations: Int,
    isAsync: Boolean,
    isEncrypted: Boolean
) : SharedPreferencesTestRunner(iterations, isAsync, isEncrypted) {

    override val sharedPreferences: SharedPreferences = if (isEncrypted) {
        EncryptedSharedPreferences.create(
            prefsName,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    override val source: String = "sharedPrefs"

    override suspend fun awaitUntilReady() {
        return
    }

    override suspend fun onReset() {
        withContext(Dispatchers.IO) {
            sharedPreferences.edit(true) { clear() }
        }
    }

    override suspend fun awaitResults() {
        return
    }
}

class HarmonyTestRunner(
    private val context: Context,
    private val prefsName: String,
    private val iterations: Int,
    isAsync: Boolean,
    private val isEncrypted: Boolean
) : SharedPreferencesTestRunner(iterations, isAsync, isEncrypted) {

    private val isReadyIndicator = Mutex(true)
    private val gotResultsIndicator = Mutex(true)
    private val incomingMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            ACK_EVENT -> isReadyIndicator.unlock()
            LOG_EVENT -> {
                val log = msg.data.getParcelable<LogEvent>(LOG_KEY) ?: return@Handler true
                runBlocking { logEvent(log) }
            }
            RESULTS_EVENT -> {
                val ipcLongArray = msg.data.getLongArray(RESULTS_KEY)
                    ?: throw IllegalArgumentException("No results available")
                if (ipcLongArray.any { it == -1L }) {
                    runBlocking {
                        logEvent(Log.ERROR, LOG_TAG, "IPC did not set all data")
                    }
                }
                testData.add(
                    IpcTestData(
                        parentTestEntityId = testRunnerId,
                        results = ipcLongArray
                    )
                )
                runBlocking {
                    logEvent(
                        LogEvent(
                            priority = Log.INFO,
                            tag = LOG_TAG,
                            message = "avg ipc: ${ipcLongArray.average() / 1_000_000} ms"
                        )
                    )
                }
                gotResultsIndicator.unlock()
            }
        }
        return@Handler true
    })

    override val sharedPreferences: SharedPreferences = if (isEncrypted) {
        context.getEncryptedHarmonySharedPreferences(
            prefsName,
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        context.getHarmonySharedPreferences(prefsName)
    }

    override val source: String = "harmony"

    override suspend fun awaitUntilReady() {
        delay(1000) // Prevent thrashing of the service
        context.startService(Intent(context, HarmonyRemoteTestRunnerService::class.java).apply {
            putExtra(PREFS_NAME_KEY, prefsName)
            putExtra(USE_ENCRYPTION_KEY, isEncrypted)
            putExtra(ITERATIONS_KEY, iterations)
            putExtra(REMOTE_MESSENGER_KEY, incomingMessenger)
        })
        isReadyIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "harmony is ready")
        }
    }

    override suspend fun onReset() {
        try {
            isReadyIndicator.unlock()
        } catch (e: IllegalStateException) {
        } finally {
            isReadyIndicator.lock()
        }

        try {
            gotResultsIndicator.unlock()
        } catch (e: IllegalStateException) {
        } finally {
            gotResultsIndicator.lock()
        }

        withContext(Dispatchers.IO) {
            sharedPreferences.edit(true) { clear() }
        }
    }

    override suspend fun awaitResults() {
        delay(3000) // Give time for results to populate on other process
        context.stopService(Intent(context, HarmonyRemoteTestRunnerService::class.java))
        gotResultsIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "got all harmony results")
        }
    }

    companion object {
        private const val LOG_TAG = "HarmonyTestRunner"
    }
}
