package com.ely.kian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ely.kian.data.local.dao.*
import com.ely.kian.data.local.entities.*

@Database(
    entities = [
        Key::class,
        Profile::class,
        UserFollow::class,
        Product::class,
        ProductCategory::class,
        TokenDefinition::class,
        TokenUtxo::class,
        Conversation::class,
        Message::class,
        MessageReceipt::class,
        Review::class,
        OfflineQueue::class,
        Relay::class,
        DmInboxRelay::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KianDatabase : RoomDatabase() {
    abstract fun keyDao(): KeyDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun productDao(): ProductDao
    abstract fun tokenDao(): TokenDao
    abstract fun chatDao(): ChatDao
    abstract fun reviewDao(): ReviewDao
    abstract fun offlineQueueDao(): OfflineQueueDao
    abstract fun relayDao(): RelayDao
}
