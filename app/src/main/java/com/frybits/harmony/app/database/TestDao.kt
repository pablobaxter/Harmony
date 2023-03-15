package com.frybits.harmony.app.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

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

    @Transaction
    @Query("SELECT * FROM testsuiteentity")
    abstract suspend fun getAllTestWithData(): List<TestSuiteDataRelation>
}
