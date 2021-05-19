package com.frybits.harmony.app.repository

import com.frybits.harmony.app.database.HarmonyDatabase
import com.frybits.harmony.app.test.TestSuiteData
import com.frybits.harmony.app.test.toModel
import com.frybits.harmony.app.test.toRelation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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