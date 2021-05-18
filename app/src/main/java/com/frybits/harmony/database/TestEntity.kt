package com.frybits.harmony.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.util.UUID

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
