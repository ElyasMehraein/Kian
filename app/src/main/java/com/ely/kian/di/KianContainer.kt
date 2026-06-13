package com.ely.kian.di

import android.content.Context
import androidx.room.Room
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.KianDatabase
import com.ely.kian.data.remote.EventProcessor
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.repository.ProductRepository
import com.ely.kian.data.repository.TokenRepository
import com.ely.kian.data.repository.ChatRepository

class KianContainer(private val context: Context) {
    val secureStorage = SecureStorage(context)
    val relayPoolManager = RelayPoolManager()

    val database: KianDatabase by lazy {
        try {
            buildDatabase()
        } catch (e: Exception) {
            // Development hack: If schema changed at same version, wipe and rebuild
            context.deleteDatabase("kian_db")
            buildDatabase()
        }
    }

    private fun buildDatabase(): KianDatabase {
        return Room.databaseBuilder(
            context,
            KianDatabase::class.java,
            "kian_db"
        )
        .fallbackToDestructiveMigration()
        .build()
    }

    val keyDao get() = database.keyDao()
    val userProfileDao get() = database.userProfileDao()
    val productDao get() = database.productDao()
    val tokenDao get() = database.tokenDao()
    val reviewDao get() = database.reviewDao()
    val offlineQueueDao get() = database.offlineQueueDao()
    val relayDao get() = database.relayDao()

    val productRepository: ProductRepository by lazy {
        ProductRepository(
            productDao = productDao,
            secureStorage = secureStorage,
            syncManagerProvider = { nostrSyncManager }
        )
    }

    val tokenRepository: TokenRepository by lazy {
        TokenRepository(
            keyDao = keyDao,
            tokenDao = tokenDao,
            productDao = productDao,
            relayDao = relayDao,
            offlineQueueDao = offlineQueueDao,
            secureStorage = secureStorage,
            syncManagerProvider = { nostrSyncManager }
        )
    }

    val chatRepository: ChatRepository by lazy {
        ChatRepository(
            chatDao = database.chatDao(),
            relayDao = database.relayDao(),
            offlineQueueDao = database.offlineQueueDao(),
            secureStorage = secureStorage,
            nostrSyncManager = nostrSyncManager
        )
    }

    val eventProcessor: EventProcessor by lazy {
        EventProcessor(
            secureStorage = secureStorage,
            productRepository = productRepository,
            tokenRepository = tokenRepository,
            userProfileDao = userProfileDao,
            chatRepository = chatRepository
        )
    }

    val nostrSyncManager by lazy {
        NostrSyncManager(
            relayPool = relayPoolManager,
            userProfileDao = userProfileDao,
            relayDao = relayDao,
            eventProcessorProvider = { eventProcessor }
        )
    }
}
