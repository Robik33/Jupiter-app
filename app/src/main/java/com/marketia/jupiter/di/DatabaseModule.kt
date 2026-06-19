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
        Room.databaseBuilder(ctx, JupiterDatabase::class.java, "jupiter.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideSkillDao(db: JupiterDatabase)   = db.skillDao()
    @Provides fun provideLinkDao(db: JupiterDatabase)    = db.linkDao()
    @Provides fun provideProjectDao(db: JupiterDatabase) = db.projectDao()
    @Provides fun provideSystemDao(db: JupiterDatabase)  = db.systemDao()
    @Provides fun provideAgentDao(db: JupiterDatabase)   = db.agentDao()
}
