package com.wllcom.quicomguide.di

import android.content.Context
import com.wllcom.quicomguide.data.local.AppDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideMaterialDao(db: AppDatabase) = db.materialDao()

    @Provides
    fun provideMaterialQueryDao(db: AppDatabase) = db.materialQueryDao()

    @Provides
    fun provideCourseDao(db: AppDatabase) = db.courseDao()

    @Provides
    fun provideGroupDao(db: AppDatabase) = db.groupDao()
}