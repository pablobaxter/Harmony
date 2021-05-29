package com.frybits.harmony.app.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
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

data class TestSuiteDataRelation(
    @Embedded val testSuite: TestSuiteEntity,
    @Relation(
        entity = TestEntity::class,
        parentColumn = "id",
        entityColumn = "parentTestSuiteId"
    )
    val testEntityWithDataList: List<TestEntityWithDataRelation>
)

data class TestEntityWithDataRelation(
    @Embedded val entity: TestEntity,
    @Relation(
        parentColumn = "testEntityId",
        entityColumn = "parentTestEntityId"
    )
    val testDataList: List<TestDataEntity>
)

@Entity
data class TestSuiteEntity(
    @PrimaryKey val id: UUID,
    val timestamp: Long
)

@Entity
data class TestEntity(
    @PrimaryKey val testEntityId: UUID,
    val parentTestSuiteId: UUID,
    val numIterations: Int,
    val isAsync: Boolean,
    val isEncrypted: Boolean,
    val source: String
)

@Entity
data class TestDataEntity(
    @PrimaryKey(autoGenerate = true) val testDataId: Long = 0L,
    val parentTestEntityId: UUID,
    val testDataType: String,
    val results: LongArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TestDataEntity) return false

        if (testDataId != other.testDataId) return false
        if (parentTestEntityId != other.parentTestEntityId) return false
        if (testDataType != other.testDataType) return false
        if (!results.contentEquals(other.results)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = testDataId.hashCode()
        result = 31 * result + parentTestEntityId.hashCode()
        result = 31 * result + testDataType.hashCode()
        result = 31 * result + results.contentHashCode()
        return result
    }
}
