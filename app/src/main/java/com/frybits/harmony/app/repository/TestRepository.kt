package com.frybits.harmony.app.repository

import com.frybits.harmony.app.database.HarmonyDatabase
import com.frybits.harmony.app.test.TestSuiteData
import com.frybits.harmony.app.test.toModel
import com.frybits.harmony.app.test.toRelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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

@Singleton
class TestRepository @Inject constructor(
    private val harmonyDatabase: HarmonyDatabase
    ) {

    private val testDao = harmonyDatabase.getTestDao()

    suspend fun storeTestSuite(testSuiteData: TestSuiteData) {
        testDao.insertAll(testSuiteData.toRelation())
    }

    suspend fun getAllTestSuites(): List<TestSuiteData> {
        return testDao.getAllTestWithData().map { it.toModel() }
    }

    suspend fun clearAllItems() {
        withContext(Dispatchers.IO) {
            harmonyDatabase.clearAllTables()
        }
    }
}