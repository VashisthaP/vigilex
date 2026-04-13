package com.vigilex.core.di

import android.content.Context
import androidx.room.Room
import com.vigilex.core.data.local.AppDatabase
import com.vigilex.core.data.local.PendingEventDao
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "vigilex.db")
            .fallbackToDestructiveMigration() // dev convenience; swap for migrations in prod
            .build()

    @Provides
    fun providePendingEventDao(db: AppDatabase): PendingEventDao = db.pendingEventDao()
}
