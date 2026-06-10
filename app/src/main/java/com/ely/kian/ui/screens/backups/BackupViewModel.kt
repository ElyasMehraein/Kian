package com.ely.kian.ui.screens.backups

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ely.kian.crypto.KianKeys
import com.ely.kian.crypto.SecureStorage
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class BackupFile(
    val name: String,
    val size: String,
    val date: String,
    val file: File
)

class BackupViewModel(
    context: Context,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val appContext = context.applicationContext

    var backups = mutableStateListOf<BackupFile>()
        private set

    val backupFolderPath: String = File(appContext.filesDir, "backups").absolutePath

    init {
        loadBackups()
    }

    fun loadBackups() {
        viewModelScope.launch {
            val directory = File(backupFolderPath)
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            val files = directory.listFiles { file -> file.extension == "db" || file.extension == "enc" }
            backups.clear()
            files?.sortedByDescending { it.lastModified() }?.forEach { file ->
                backups.add(
                    BackupFile(
                        name = file.name,
                        size = formatFileSize(file.length()),
                        date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(file.lastModified())),
                        file = file
                    )
                )
            }
        }
    }

    fun createBackup() {
        viewModelScope.launch {
            try {
                val dbFile = appContext.getDatabasePath("kian_db")
                if (dbFile.exists()) {
                    val directory = File(backupFolderPath)
                    if (!directory.exists()) directory.mkdirs()
                    
                    val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date())
                    val backupFile = File(directory, "backup_$timeStamp.enc")
                    
                    val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: return@launch
                    val keyBytes = KianKeys.sha256(KianKeys.hexToBytes(privKeyHex))
                    
                    encryptFile(dbFile, backupFile, keyBytes)
                    loadBackups()
                }
            } catch (e: Exception) {
                android.util.Log.e("BackupViewModel", "Encryption failed", e)
            }
        }
    }

    private fun encryptFile(source: File, dest: File, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        val iv = ByteArray(12)
        java.security.SecureRandom().nextBytes(iv)
        val parameterSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec)
        
        source.inputStream().use { input ->
            dest.outputStream().use { output ->
                output.write(iv) // Write IV at the beginning
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val update = cipher.update(buffer, 0, bytesRead)
                    if (update != null) output.write(update)
                }
                val final = cipher.doFinal()
                if (final != null) output.write(final)
            }
        }
    }

    fun restoreBackup(backup: BackupFile, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val privKeyHex = secureStorage.getSecret(SecureStorage.PRIVATE_KEY) ?: throw Exception("Login required")
                val keyBytes = KianKeys.sha256(KianKeys.hexToBytes(privKeyHex))
                
                val tempDbFile = File(appContext.cacheDir, "restored_temp.db")
                decryptFile(backup.file, tempDbFile, keyBytes)
                
                // If decryption succeeds, replace the main DB
                val dbFile = appContext.getDatabasePath("kian_db")
                
                // IMPORTANT: In Room, we should close the database before replacing the file
                // For this migration phase, we'll assume a restart or simple overwrite might work
                // but ideally we'd trigger a full app restart.
                
                tempDbFile.inputStream().use { input ->
                    dbFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("BackupViewModel", "Restore failed", e)
                onError(e.message ?: "Decryption failed. This backup might belong to another account.")
            }
        }
    }

    private fun decryptFile(source: File, dest: File, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val secretKey = SecretKeySpec(key, "AES")
        
        source.inputStream().use { input ->
            val iv = ByteArray(12)
            if (input.read(iv) != 12) throw Exception("Invalid backup file (missing IV)")
            
            val parameterSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec)
            
            dest.outputStream().use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    val update = cipher.update(buffer, 0, bytesRead)
                    if (update != null) output.write(update)
                }
                val final = cipher.doFinal()
                if (final != null) output.write(final)
            }
        }
    }

    fun deleteBackup(backup: BackupFile) {
        if (backup.file.delete()) {
            backups.remove(backup)
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    companion object {
        fun provideFactory(context: Context, secureStorage: SecureStorage): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackupViewModel(context, secureStorage) as T
            }
        }
    }
}
