package com.frybits.harmony.app.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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

@Database(
    entities = [TestSuiteEntity::class, TestEntity::class, TestDataEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(TestConverters::class)
abstract class HarmonyDatabase : RoomDatabase() {
    abstract fun getTestDao(): TestDao
}
