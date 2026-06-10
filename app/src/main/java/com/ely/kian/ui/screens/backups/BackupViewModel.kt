package com.ely.kian.ui.screens.backups

import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class BackupFile(
    val name: String,
    val size: String,
    val date: String,
    val file: File
)

class BackupViewModel(
    context: Context
) : ViewModel() {
    private val appContext = context.applicationContext

    var backups = mutableStateListOf<BackupFile>()
        private set

    val backupFolderPath: String = "${appContext.getExternalFilesDir(null)}/backups"

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
            val dbFile = appContext.getDatabasePath("kian_db")
            if (dbFile.exists()) {
                val directory = File(backupFolderPath)
                if (!directory.exists()) directory.mkdirs()
                
                val timeStamp = SimpleDateFormat("yyyy_MM_dd_HHmm", Locale.getDefault()).format(Date())
                val backupFile = File(directory, "backup_$timeStamp.db")
                
                dbFile.inputStream().use { input ->
                    backupFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                loadBackups()
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
        fun provideFactory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return BackupViewModel(context) as T
            }
        }
    }
}
