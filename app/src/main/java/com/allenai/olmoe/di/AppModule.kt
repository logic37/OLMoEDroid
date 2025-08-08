package com.allenai.olmoe.di

import android.content.Context
import com.allenai.olmoe.data.database.AppDatabase
import com.allenai.olmoe.data.repository.ChatRepository
import com.allenai.olmoe.domain.usecase.ModelDownloadUseCase
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }
    
    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase) = database.chatDao()
    
    @Provides
    @Singleton
    fun provideChatRepository(chatDao: com.allenai.olmoe.data.dao.ChatDao): ChatRepository {
        return ChatRepository(chatDao)
    }
    
    @Provides
    @Singleton
    fun provideModelDownloadUseCase(@ApplicationContext context: Context): ModelDownloadUseCase {
        return ModelDownloadUseCase(context)
    }
} 