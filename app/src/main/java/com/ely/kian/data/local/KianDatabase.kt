package com.ely.kian.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ely.kian.data.local.dao.UserProfileDao
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.data.local.entities.OfflineQueue
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.TokenUtxo
import com.ely.kian.data.local.entities.UserProfile

@Database(
    entities = [
        UserProfile::class,
        Product::class,
        ChatMessage::class,
        TokenUtxo::class,
        OfflineQueue::class
    ],
    version = 1,
    exportSchema = false
)
abstract class KianDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
}
