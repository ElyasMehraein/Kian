package com.ely.kian.di

import android.content.Context
import androidx.room.Room
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.KianDatabase
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository

class KianContainer(context: Context) {
    val secureStorage = SecureStorage(context)
    val relayPoolManager = RelayPoolManager()

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

    val chatRepository: ChatRepository by lazy {
        ChatRepository(chatDao, relayDao, relayPoolManager, secureStorage)
    }

    val productRepository: ProductRepository by lazy {
        ProductRepository(productDao, relayPoolManager, secureStorage)
    }

    val tokenRepository: TokenRepository by lazy {
        TokenRepository(keyDao, tokenDao, productDao, chatDao, offlineQueueDao)
    }

    val nostrSyncManager by lazy {
        NostrSyncManager(
            relayPool = relayPoolManager,
            userProfileDao = userProfileDao,
            relayDao = relayDao,
            chatRepository = chatRepository,
            productRepository = productRepository,
            tokenRepository = tokenRepository
        )
    }
}
