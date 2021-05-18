package com.frybits.harmony.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class TestDao {

    @Transaction
    open suspend fun insertAll(vararg testSuiteData: TestSuiteDataRelation) {
        testSuiteData.forEach { test ->
            insertAllTestSuite(test.testSuite)
            insertAllTestEntityWithData(test.testEntityWithDataList)
        }
    }

    @Transaction
    open suspend fun insertAllTestEntityWithData(testEntityWithDataList: List<TestEntityWithDataRelation>) {
        testEntityWithDataList.forEach { test ->
            insertTestEntity(test.entity)
            insertAllTestData(test.testDataList)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllTestSuite(vararg testSuite: TestSuiteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertTestEntity(testEntity: TestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insertAllTestData(testDataList: List<TestDataEntity>)

    @Transaction
    open suspend fun delete(testSuiteData: TestSuiteDataRelation) {
        delete(testSuiteData.testSuite)
        testSuiteData.testEntityWithDataList.forEach {
            delete(it.entity)
            delete(it.testDataList)
        }
    }

    @Delete
    abstract suspend fun delete(testSuite: TestSuiteEntity)

    @Delete
    abstract suspend fun delete(testEntity: TestEntity)

    @Delete
    abstract suspend fun delete(testDataList: List<TestDataEntity>)

    @Query("SELECT * FROM testsuiteentity")
    abstract suspend fun getAllTestWithData(): List<TestSuiteDataRelation>
}