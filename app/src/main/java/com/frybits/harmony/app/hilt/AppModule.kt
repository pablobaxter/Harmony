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