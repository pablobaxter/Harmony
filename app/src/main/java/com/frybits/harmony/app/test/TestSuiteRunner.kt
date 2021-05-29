package com.frybits.harmony.app.test

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.merge
import java.util.UUID

private const val LOG_TAG = "TestSuiteRunner"

class TestSuiteRunner(
    private val testRunners: List<TestRunner>,
    private val testRunnerIterations: Int,
    private val testId: UUID = UUID.randomUUID(),
    private val timestamp: Long = System.currentTimeMillis()
) {

    private val internalLogFlow = MutableSharedFlow<LogEvent>()
    @ExperimentalCoroutinesApi
    val logFlow = testRunners.mapTo(arrayListOf(internalLogFlow)) { it.logFlow }
        .merge()
        .buffer(capacity = Channel.UNLIMITED)

    suspend fun runTests() {
        logEvent(Log.INFO, LOG_TAG, "running tests")
        testRunners.forEach { runner ->
            logEvent(Log.INFO, LOG_TAG, "starting tests for ${runner.source}")
            repeat(testRunnerIterations) {
                logEvent(Log.INFO, LOG_TAG, "running test #$it of ${runner.source}")
                runner.runTest()
                logEvent(Log.INFO, LOG_TAG, "stopping test #$it of ${runner.source}")
            }
            logEvent(Log.INFO, LOG_TAG, "ending tests for ${runner.source}")
        }
    }

    suspend fun getResults(): TestSuiteData {
        logEvent(Log.INFO, LOG_TAG, "getting test results")
        return TestSuiteData(
            testSuite = TestSuite(
                id = testId,
                timestamp = timestamp
            ),
            testEntityWithDataList = testRunners.map { it.getTestResults(testId) }
        )
    }

    private suspend fun logEvent(priority: Int, tag: String, message: String) {
        internalLogFlow.emit(
            LogEvent(
                priority = priority,
                tag = tag,
                message = message
            )
        )
    }
}
