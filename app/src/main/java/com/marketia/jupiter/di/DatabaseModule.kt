package com.marketia.jupiter.di

import android.content.Context
import androidx.room.Room
import com.marketia.jupiter.data.db.JupiterDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext ctx: Context): JupiterDatabase =
        Room.databaseBuilder(ctx, JupiterDatabase::class.java, "jupiter.db").build()

    @Provides fun provideStatDao(db: JupiterDatabase) = db.statDao()
    @Provides fun provideHabitDao(db: JupiterDatabase) = db.habitDao()
    @Provides fun provideMissionDao(db: JupiterDatabase) = db.missionDao()
    @Provides fun provideProgressDao(db: JupiterDatabase) = db.progressDao()
}
