package com.ely.kian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ely.kian.data.local.dao.RelayDao
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.*

@Database(
    entities = [
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
    abstract fun userProfileDao(): UserProfileDao
    abstract fun relayDao(): RelayDao
}
