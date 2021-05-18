package com.frybits.harmony.test

import com.frybits.harmony.database.TestDataEntity
import com.frybits.harmony.database.TestEntity
import com.frybits.harmony.database.TestEntityWithDataRelation
import com.frybits.harmony.database.TestSuiteDataRelation
import com.frybits.harmony.database.TestSuiteEntity
import java.util.UUID

data class TestSuite(val id: UUID, val timestamp: Long)

private fun TestSuite.toEntity(): TestSuiteEntity {
    return TestSuiteEntity(
        id = id,
        timestamp = timestamp
    )
}

sealed class TestData(
    val parentTestEntityId: UUID,
    val testDataType: String,
    val results: LongArray
)

private fun TestData.toEntity(): TestDataEntity {
    return TestDataEntity(
        parentTestEntityId = parentTestEntityId,
        testDataType = testDataType,
        results = results
    )
}

class WriteTestData(parentTestEntityId: UUID, results: LongArray) : TestData(parentTestEntityId, "write", results)
class ReadTestData(parentTestEntityId: UUID, results: LongArray) : TestData(parentTestEntityId, "read", results)
class IpcTestData(parentTestEntityId: UUID, results: LongArray) : TestData(parentTestEntityId, "ipc", results)

data class TestRun(
    val testEntityId: UUID,
    val parentTestSuiteId: UUID,
    val numIterations: Int,
    val isAsync: Boolean,
    val isEncrypted: Boolean,
    val source: String
)

private fun TestRun.toEntity(): TestEntity {
    return TestEntity(
        testEntityId = testEntityId,
        parentTestSuiteId = parentTestSuiteId,
        numIterations = numIterations,
        isAsync = isAsync,
        isEncrypted = isEncrypted,
        source = source
    )
}

data class TestSuiteData(
    val testSuite: TestSuite,
    val testEntityWithDataList: List<TestEntityWithData>
)

fun TestSuiteData.toRelation(): TestSuiteDataRelation {
    return TestSuiteDataRelation(
        testSuite = testSuite.toEntity(),
        testEntityWithDataList = testEntityWithDataList.map { it.toRelation() }
    )
}

data class TestEntityWithData(
    val entity: TestRun,
    val testDataList: List<TestData>
)

private fun TestEntityWithData.toRelation(): TestEntityWithDataRelation {
    return TestEntityWithDataRelation(
        entity = entity.toEntity(),
        testDataList = testDataList.map { it.toEntity() }
    )
}
