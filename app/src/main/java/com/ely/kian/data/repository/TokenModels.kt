package com.ely.kian.data.repository

data class BalanceItem(
    val assetRef: String,
    val amount: Long,
    val description: String,
    val images: List<String>,
    val name: String,
    val producer: String,
    val categories: List<String>,
    val isShowcase: Boolean = false
)

data class PendingItem(
    val eventId: String,
    val utxoId: String,
    val assetRef: String,
    val assetName: String,
    val amount: Long,
    val recipient: String,
    val status: String, // 'pending' | 'completed' | 'failed'
    val type: String,   // 'send' | 'receive' | 'redemption'
    val createdAt: Long
)
