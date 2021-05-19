package com.frybits.harmony.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [TestSuiteEntity::class, TestEntity::class, TestDataEntity::class], version = 1)
@TypeConverters(TestConverters::class)
abstract class HarmonyDatabase : RoomDatabase() {
    abstract fun getTestDao(): TestDao
}