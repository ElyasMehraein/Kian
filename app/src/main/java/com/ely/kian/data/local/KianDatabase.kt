package com.ely.kian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.*

@Database(
    entities = [
        com.ely.kian.data.local.entities.Key::class,
        com.ely.kian.data.local.entities.Profile::class,
        com.ely.kian.data.local.entities.UserFollow::class,
        com.ely.kian.data.local.entities.Product::class,
        com.ely.kian.data.local.entities.ProductCategory::class,
        com.ely.kian.data.local.entities.TokenDefinition::class,
        com.ely.kian.data.local.entities.TokenUtxo::class,
        com.ely.kian.data.local.entities.Review::class,
        com.ely.kian.data.local.entities.OfflineQueue::class,
        com.ely.kian.data.local.entities.Relay::class,
        com.ely.kian.data.local.entities.DmInboxRelay::class,
        com.ely.kian.data.local.entities.ChatMessage::class,
        com.ely.kian.data.local.entities.Conversation::class
    ],
    version = 2,
    exportSchema = false
)
abstract class KianDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun productDao(): ProductDao
    abstract fun tokenDao(): TokenDao
    abstract fun reviewDao(): ReviewDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun relayDao(): RelayDao
    abstract fun chatDao(): ChatDao
}
