package com.frybits.harmony.app.hilt

import android.content.Context
import androidx.room.Room
import com.frybits.harmony.app.database.HarmonyDatabase
import com.frybits.harmony.app.database.TestDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

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

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideTestDatabase(@ApplicationContext context: Context): HarmonyDatabase {
        return Room.databaseBuilder(context, HarmonyDatabase::class.java, "harmony-database").build()
    }

    @Provides
    fun provideTestDao(harmonyDatabase: HarmonyDatabase): TestDao {
        return harmonyDatabase.getTestDao()
    }
}