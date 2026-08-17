package com.utub.di

import android.content.Context
import androidx.room.Room
import com.utub.data.db.QueueDao
import com.utub.data.db.RecentPlayDao
import com.utub.data.db.SearchHistoryDao
import com.utub.data.db.UTubDatabase
import com.utub.data.prefs.SettingsRepository
import com.utub.extractor.StreamExtractor
import com.utub.extractor.newpipe.NewPipeStreamExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideStreamExtractor(): StreamExtractor = NewPipeStreamExtractor()

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): UTubDatabase =
        Room.databaseBuilder(context, UTubDatabase::class.java, "utub.db").build()

    @Provides fun provideQueueDao(db: UTubDatabase): QueueDao = db.queueDao()
    @Provides fun provideRecentPlayDao(db: UTubDatabase): RecentPlayDao = db.recentPlayDao()
    @Provides fun provideSearchHistoryDao(db: UTubDatabase): SearchHistoryDao = db.searchHistoryDao()

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): SettingsRepository =
        SettingsRepository(context)
}
