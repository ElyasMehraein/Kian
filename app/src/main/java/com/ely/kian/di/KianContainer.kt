package com.ely.kian.di

import android.content.Context
import androidx.room.Room
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.KianDatabase

class KianContainer(context: Context) {
    val secureStorage = SecureStorage(context)

    val database: KianDatabase by lazy {
        Room.databaseBuilder(
            context,
            KianDatabase::class.java,
            "kian_db"
        ).fallbackToDestructiveMigration().build()
    }

    val keyDao get() = database.keyDao()
    val userProfileDao get() = database.userProfileDao()
    val productDao get() = database.productDao()
    val tokenDao get() = database.tokenDao()
    val chatDao get() = database.chatDao()
    val reviewDao get() = database.reviewDao()
    val offlineQueueDao get() = database.offlineQueueDao()
    val relayDao get() = database.relayDao()
}
