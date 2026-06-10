package com.ely.kian.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_keys",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSecret(key: String, value: String) {
        sharedPreferences.edit().putString(key, value).apply()
    }

    fun getSecret(key: String): String? {
        return sharedPreferences.getString(key, null)
    }

    fun deleteSecret(key: String) {
        sharedPreferences.edit().remove(key).apply()
    }

    fun clearAll() {
        sharedPreferences.edit().clear().apply()
    }

    companion object {
        const val PRIVATE_KEY = "keys_privkey"
        const val MNEMONIC = "keys_mnemonic"
    }
}
