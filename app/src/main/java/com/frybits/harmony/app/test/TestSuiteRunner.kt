package com.frybits.harmony.app.test

import android.util.Log
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.merge
import java.util.UUID

/*
 *  Copyright 2021 Pablo Baxter
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
