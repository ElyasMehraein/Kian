package com.ely.kian.di

import android.content.Context
import androidx.room.Room
import com.ely.kian.data.local.KianDatabase
import com.ely.kian.data.local.dao.*
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
    fun provideDatabase(@ApplicationContext context: Context): KianDatabase {
        return Room.databaseBuilder(
            context,
            KianDatabase::class.java,
            "kian_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideKeyDao(db: KianDatabase): KeyDao = db.keyDao()

    @Provides
    fun provideUserProfileDao(db: KianDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideProductDao(db: KianDatabase): ProductDao = db.productDao()

    @Provides
    fun provideTokenDao(db: KianDatabase): TokenDao = db.tokenDao()

    @Provides
    fun provideReviewDao(db: KianDatabase): ReviewDao = db.reviewDao()

    @Provides
    fun provideOfflineQueueDao(db: KianDatabase): OfflineQueueDao = db.offlineQueueDao()

    @Provides
    fun provideRelayDao(db: KianDatabase): RelayDao = db.relayDao()
}
