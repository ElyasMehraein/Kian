package com.ely.kian.ui.screens.backups

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ely.kian.KianApp
import com.ely.kian.ui.theme.KianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: BackupViewModel = viewModel(
        factory = BackupViewModel.provideFactory(context, app.container.secureStorage)
    )
    val kianColors = KianTheme.colors
    
    // To handle success/error messages
    fun showMessage(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup & Recovery", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kianColors.canvas,
                    titleContentColor = kianColors.ink
                )
            )
        },
        containerColor = kianColors.canvas
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "🔐 Backup & Recovery",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink
                    )
                    Text(
                        text = "End-to-End Encrypted Backups",
                        color = kianColors.muted,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Auto Backup Status Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = kianColors.panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .background(kianColors.success, CircleShape)
                        )
                        Column {
                            Text(
                                text = "Local Backup Storage",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = kianColors.ink
                            )
                            Text(
                                text = "Your data is stored locally and securely.",
                                color = kianColors.muted,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Folder Path Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = kianColors.panel),
                    border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Folder, contentDescription = null, tint = kianColors.ink, modifier = Modifier.size(20.dp))
                            Text(text = "Backup Folder", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        
                        Box(
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF020617))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = viewModel.backupFolderPath.replace(context.packageName, "com.ely.kian"),
                                color = Color(0xFF93C5FD),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        
                        Text(
                            text = "To import an external backup, place the file in the directory above. It will automatically appear in the list.",
                            color = kianColors.muted,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            }

            // Create Backup Button
            item {
                Button(
                    onClick = { 
                        viewModel.createBackup()
                        showMessage("Encrypted backup created successfully!")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+ Create New Backup", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }

            item {
                Text(
                    text = "Existing Backups",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = kianColors.ink,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // List of Backups
            if (viewModel.backups.isEmpty()) {
                item {
                    Text(
                        text = "No backups found.",
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        textAlign = TextAlign.Center,
                        color = kianColors.muted
                    )
                }
            } else {
                items(viewModel.backups) { backup ->
                    BackupItem(
                        backup = backup,
                        onRestore = {
                            viewModel.restoreBackup(
                                backup = backup,
                                onSuccess = { 
                                    showMessage("Data restored! Please restart the app.")
                                },
                                onError = { error ->
                                    showMessage("Error: $error")
                                }
                            )
                        },
                        onShare = {
                            try {
                                val authority = "${context.packageName}.fileprovider"
                                val uri = FileProvider.getUriForFile(context, authority, backup.file)
                                
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    // For better compatibility on Android 10+
                                    clipData = android.content.ClipData.newRawUri("", uri)
                                }
                                
                                val chooser = Intent.createChooser(intent, "Share Backup")
                                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(chooser)
                            } catch (e: Exception) {
                                android.util.Log.e("BackupScreen", "Share failed: ${e.message}", e)
                                showMessage("Share failed: ${e.localizedMessage}")
                            }
                        },
                        onDelete = {
                            viewModel.deleteBackup(backup)
                            showMessage("Backup deleted.")
                        }
                    )
                }
            }

            // Warning
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = kianColors.danger.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.danger.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = "⚠️ When restoring, all current application data will be replaced with the data in the backup. Ensure you have the latest backup before proceeding.",
                        modifier = Modifier.padding(16.dp),
                        color = kianColors.danger,
                        fontSize = 14.sp,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun BackupItem(
    backup: BackupFile,
    onRestore: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val kianColors = KianTheme.colors
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kianColors.panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = backup.name, fontWeight = FontWeight.Bold, color = kianColors.ink)
                    Text(text = "${backup.size} • ${backup.date}", color = kianColors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
            
            Row(
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onRestore,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                ) {
                    Text("Restore", fontSize = 13.sp)
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
                ) {
                    Text("Share", fontSize = 13.sp)
                }
                Button(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Delete", fontSize = 13.sp)
                }
            }
        }
    }
}
