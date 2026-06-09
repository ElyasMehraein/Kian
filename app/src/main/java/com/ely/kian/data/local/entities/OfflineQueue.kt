package com.ely.kian.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_queue")
data class OfflineQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventKind: Int,
    val eventContent: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attempts: Int = 0
)
