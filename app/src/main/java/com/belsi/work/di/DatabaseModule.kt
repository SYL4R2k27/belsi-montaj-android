package com.belsi.work.di

import android.content.Context
import androidx.room.Room
import com.belsi.work.data.local.database.AppDatabase
import com.belsi.work.data.local.database.dao.*
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideShiftDao(database: AppDatabase): ShiftDao {
        return database.shiftDao()
    }

    @Provides
    @Singleton
    fun providePhotoDao(database: AppDatabase): PhotoDao {
        return database.photoDao()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideUserCacheDao(database: AppDatabase): UserCacheDao {
        return database.userDao()
    }

    @Provides
    @Singleton
    fun provideToolDao(database: AppDatabase): ToolDao {
        return database.toolDao()
    }

    @Provides
    @Singleton
    fun provideTaskDao(database: AppDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    @Singleton
    fun provideMessengerThreadDao(database: AppDatabase): MessengerThreadDao {
        return database.messengerThreadDao()
    }

    @Provides
    @Singleton
    fun provideMessengerMessageDao(database: AppDatabase): MessengerMessageDao {
        return database.messengerMessageDao()
    }
}
