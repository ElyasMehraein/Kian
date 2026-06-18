package com.ely.kian.di

import android.content.Context
import androidx.room.Room
import com.ely.kian.crypto.SecureStorage
import com.ely.kian.data.local.KianDatabase
import com.ely.kian.data.remote.EventProcessor
import com.ely.kian.data.remote.NostrSyncManager
import com.ely.kian.data.remote.RelayPoolManager
import com.ely.kian.data.repository.VoucherRepository
import com.ely.kian.data.repository.ChatRepository
import com.ely.kian.services.GitHubUpdateManager
import com.ely.kian.util.NotificationHelper
import com.ely.kian.KianApp

class KianContainer(private val context: Context) {
    val secureStorage = SecureStorage(context)
    val relayPoolManager = RelayPoolManager()
    val updateManager = GitHubUpdateManager(context)
    val notificationHelper = NotificationHelper(context)

    private val app = context.applicationContext as KianApp

    val database: KianDatabase by lazy {
        try {
            val db = buildDatabase()
            db.openHelper.writableDatabase
            db
        } catch (e: Exception) {
            android.util.Log.e("KianContainer", "Database integrity check failed, wiping...", e)
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
    val voucherDao get() = database.voucherDao()
    val reviewDao get() = database.reviewDao()
    val offlineQueueDao get() = database.offlineQueueDao()
    val relayDao get() = database.relayDao()

    val voucherRepository: VoucherRepository by lazy {
        VoucherRepository(
            keyDao = keyDao,
            voucherDao = voucherDao,
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
            voucherDao = database.voucherDao(),
            offlineQueueDao = database.offlineQueueDao(),
            userProfileDao = userProfileDao,
            secureStorage = secureStorage,
            nostrSyncManager = nostrSyncManager,
            notificationHelper = notificationHelper,
            isAppInForeground = { app.isAppInForeground }
        )
    }

    val eventProcessor: EventProcessor by lazy {
        EventProcessor(
            secureStorage = secureStorage,
            voucherRepository = voucherRepository,
            userProfileDao = userProfileDao,
            reviewDao = reviewDao,
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
