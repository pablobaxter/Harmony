package com.frybits.harmony.app.test

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
import com.frybits.harmony.app.ACK_EVENT
import com.frybits.harmony.app.ITERATIONS_KEY
import com.frybits.harmony.app.LOG_EVENT
import com.frybits.harmony.app.LOG_KEY
import com.frybits.harmony.app.PREFS_NAME_KEY
import com.frybits.harmony.app.REMOTE_MESSENGER_KEY
import com.frybits.harmony.app.RESULTS_EVENT
import com.frybits.harmony.app.RESULTS_KEY
import com.frybits.harmony.app.USE_ENCRYPTION_KEY
import com.frybits.harmony.getHarmonySharedPreferences
import com.frybits.harmony.secure.getEncryptedHarmonySharedPreferences
import com.tencent.mmkv.MMKV
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
import net.grandcentrix.tray.TrayPreferences
import java.util.UUID
import kotlin.system.measureNanoTime

sealed class TestRunner(
    protected val testRunnerId: UUID = UUID.randomUUID(),
    private val iterations: Int,
    private val isAsync: Boolean,
    private val isEncrypted: Boolean
) {

    private val runTestMutex = Mutex()
    private val _logFlow = MutableSharedFlow<LogEvent>()
    val logFlow = _logFlow.asSharedFlow()
    protected val testData = arrayListOf<TestData>()

    abstract val source: String

    protected abstract suspend fun awaitUntilReady()

    suspend fun runTest() {
        runTestMutex.withLock {
            logEvent(Log.INFO, LOG_TAG, "resetting test")
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
    }

    protected abstract suspend fun reset()

    protected abstract suspend fun awaitResults()

    protected abstract fun write(key: String, value: String, isAsync: Boolean)

    protected abstract fun read(key: String)

    suspend fun getTestResults(parentId: UUID): TestEntityWithData {
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
            repeat(iterations) { testNum ->
                ensureActive()
                val value = SystemClock.elapsedRealtimeNanos().toString()
                val writeTime = measureNanoTime { write(testKeyArray[testNum], value, isAsync) }
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
                    measureNanoTime { read(testKeyArray[testNum]) }
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

    companion object {
        private const val LOG_TAG = "TestRunner"
    }
}

abstract class SharedPreferencesTestRunner(
    iterations: Int,
    isAsync: Boolean,
    isEncrypted: Boolean
) : TestRunner(iterations = iterations, isAsync = isAsync, isEncrypted = isEncrypted) {

    protected abstract val sharedPreferences: SharedPreferences

    protected abstract val editor: SharedPreferences.Editor

    override fun write(key: String, value: String, isAsync: Boolean) {
        editor.putString(key, value)
        if (isAsync) {
            editor.apply()
        } else {
            editor.commit()
        }
    }

    override fun read(key: String) {
        sharedPreferences.getString(key, null)
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
            "$prefsName-encrypted",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    override val editor: SharedPreferences.Editor = sharedPreferences.edit()

    override val source: String = TestSource.SHARED_PREFS

    override suspend fun awaitUntilReady() {
        return
    }

    override suspend fun reset() {
        withContext(Dispatchers.IO) {
            editor.clear().commit()
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
                val log = msg.data.let {
                    it.classLoader = LogEvent::class.java.classLoader
                    return@let it.getParcelable<LogEvent>(LOG_KEY) ?: return@Handler true
                }
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
            "$prefsName-encrypted",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } else {
        context.getHarmonySharedPreferences(prefsName)
    }

    override val editor: SharedPreferences.Editor = sharedPreferences.edit()

    override val source: String = TestSource.HARMONY

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

    override suspend fun reset() {
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

class MMKVTestRunner(
    private val context: Context,
    private val prefsName: String,
    private val iterations: Int,
    isAsync: Boolean,
    private val isEncrypted: Boolean
) : TestRunner(iterations = iterations, isAsync = isAsync, isEncrypted = isEncrypted) {

    override val source: String = TestSource.MMKV

    init {
        MMKV.initialize(context)
    }

    private val mmkv = if (isEncrypted) {
        requireNotNull(MMKV.mmkvWithID("$prefsName-encrypted", MMKV.MULTI_PROCESS_MODE, MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)))
    } else {
        requireNotNull(MMKV.mmkvWithID(prefsName, MMKV.MULTI_PROCESS_MODE))
    }

    private val isReadyIndicator = Mutex(true)
    private val gotResultsIndicator = Mutex(true)
    private val incomingMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            ACK_EVENT -> isReadyIndicator.unlock()
            LOG_EVENT -> {
                val log = msg.data.let {
                    it.classLoader = LogEvent::class.java.classLoader
                    return@let it.getParcelable<LogEvent>(LOG_KEY) ?: return@Handler true
                }
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

    override suspend fun awaitUntilReady() {
        delay(1000) // Prevent thrashing of the service
        context.startService(Intent(context, MMKVRemoteTestRunnerService::class.java).apply {
            putExtra(PREFS_NAME_KEY, prefsName)
            putExtra(USE_ENCRYPTION_KEY, isEncrypted)
            putExtra(ITERATIONS_KEY, iterations)
            putExtra(REMOTE_MESSENGER_KEY, incomingMessenger)
        })
        isReadyIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "mmkv is ready")
        }
    }

    override suspend fun reset() {
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
            mmkv.clearAll()
        }
    }

    override suspend fun awaitResults() {
        delay(3000) // Give time for results to populate on other process
        context.stopService(Intent(context, MMKVRemoteTestRunnerService::class.java))
        gotResultsIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "got all mmkv results")
        }
    }

    override fun write(key: String, value: String, isAsync: Boolean) {
        mmkv.encode(key, value)
    }

    override fun read(key: String) {
        mmkv.decodeString(key)
    }

    companion object {
        private const val LOG_TAG = "MMKVTestRunner"
    }
}

class TraySharedPrefsRunner(
    private val context: Context,
    private val prefsName: String,
    private val iterations: Int,
    isAsync: Boolean,
    private val isEncrypted: Boolean
) : TestRunner(iterations = iterations, isAsync = isAsync, isEncrypted = isEncrypted) {

    private val trayPreferences = AppPreferences(context, prefsName)
    private val isReadyIndicator = Mutex(true)
    private val gotResultsIndicator = Mutex(true)
    private val incomingMessenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            ACK_EVENT -> isReadyIndicator.unlock()
            LOG_EVENT -> {
                val log = msg.data.let {
                    it.classLoader = LogEvent::class.java.classLoader
                    return@let it.getParcelable<LogEvent>(LOG_KEY) ?: return@Handler true
                }
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

    override val source: String = TestSource.TRAY

    override suspend fun awaitUntilReady() {
        delay(1000) // Prevent thrashing of the service
        context.startService(Intent(context, TrayRemoteTestRunnerService::class.java).apply {
            putExtra(PREFS_NAME_KEY, prefsName)
            putExtra(USE_ENCRYPTION_KEY, isEncrypted)
            putExtra(ITERATIONS_KEY, iterations)
            putExtra(REMOTE_MESSENGER_KEY, incomingMessenger)
        })
        isReadyIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "tray is ready")
        }
    }

    override suspend fun reset() {
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
            trayPreferences.clear()
        }
    }

    override suspend fun awaitResults() {
        delay(3000) // Give time for results to populate on other process
        context.stopService(Intent(context, TrayRemoteTestRunnerService::class.java))
        gotResultsIndicator.withLock {
            logEvent(Log.INFO, LOG_TAG, "got all tray results")
        }
    }

    override fun write(key: String, value: String, isAsync: Boolean) {
        trayPreferences.put(key, value)
    }

    override fun read(key: String) {
        trayPreferences.getString(key)
    }

    companion object {
        private const val LOG_TAG = "TraySharedPrefsRunner"
    }

}

class AppPreferences(context: Context, module: String) : TrayPreferences(context, module, 1)