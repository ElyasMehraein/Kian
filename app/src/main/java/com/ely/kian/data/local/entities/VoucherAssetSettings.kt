package com.ely.kian.data.local.entities

import androidx.room.Entity
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "voucher_asset_settings", primaryKeys = ["pubkey", "assetRef"])
data class VoucherAssetSettings(
    val pubkey: String,
    val assetRef: String,
    val isShowcase: Boolean = false
)
