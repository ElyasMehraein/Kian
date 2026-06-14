package com.ely.kian.crypto

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class SecureStorage(context: Context) {
    private val sharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                "secure_keys_v2",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            android.util.Log.e("SecureStorage", "Failed to init encrypted prefs, falling back", e)
            context.getSharedPreferences("secure_keys_fallback", Context.MODE_PRIVATE)
        }
    }

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

    fun saveLanguage(language: String) {
        sharedPreferences.edit().putString("language", language).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString("language", "en") ?: "en"
    }

    companion object {
        const val PRIVATE_KEY = "keys_privkey"
        const val MNEMONIC = "keys_mnemonic"
    }
}
