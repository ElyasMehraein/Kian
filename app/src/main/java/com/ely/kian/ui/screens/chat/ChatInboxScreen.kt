package com.ely.kian.ui.screens.chat

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.data.local.entities.Conversation
import com.ely.kian.ui.theme.KianTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ChatInboxScreen(
    viewModel: ChatViewModel,
    onConversationClick: (String) -> Unit
) {
    val conversations by viewModel.conversations.collectAsState()
    val kianColors = KianTheme.colors
    var showStartChatDialog by remember { mutableStateOf(false) }
    var conversationToDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = kianColors.canvas,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showStartChatDialog = true },
                containerColor = kianColors.accent,
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Chat")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            Text(
                text = "Messages",
                style = MaterialTheme.typography.headlineMedium,
                color = kianColors.ink,
                modifier = Modifier.padding(16.dp)
            )
            
            if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text("No messages yet", color = kianColors.ink.copy(alpha = 0.5f))
                }
            } else {
                LazyColumn {
                    items(conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation, 
                            colors = kianColors,
                            onClick = { onConversationClick(conversation.contactPubkey) },
                            onLongClick = { conversationToDelete = conversation.contactPubkey }
                        )
                        HorizontalDivider(color = kianColors.panel, thickness = 0.5.dp)
                    }
                }
            }
        }
    }

    if (conversationToDelete != null) {
        AlertDialog(
            onDismissRequest = { conversationToDelete = null },
            title = { Text("Delete Conversation") },
            text = { Text("Are you sure you want to delete this conversation? This will also request to delete your messages on Nostr relays.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(conversationToDelete!!)
                        conversationToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { conversationToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showStartChatDialog) {
        var pubkeyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showStartChatDialog = false },
            title = { Text("Start Chat") },
            text = {
                TextField(
                    value = pubkeyInput,
                    onValueChange = { pubkeyInput = it },
                    placeholder = { Text("Enter Pubkey (hex)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (pubkeyInput.length == 64) {
                            showStartChatDialog = false
                            onConversationClick(pubkeyInput)
                        }
                    }
                ) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartChatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ConversationItem(
    conversation: Conversation,
    colors: com.ely.kian.ui.theme.KianColors,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(16.dp)
    ) {
        // Avatar Placeholder
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = colors.panel,
            modifier = Modifier.size(50.dp)
        ) {}
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = conversation.contactPubkey.take(8) + "...",
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                )
                Text(
                    text = formatTimestamp(conversation.lastTimestamp),
                    fontSize = 12.sp,
                    color = colors.ink.copy(alpha = 0.6f)
                )
            }
            
            Text(
                text = conversation.lastMessage,
                maxLines = 1,
                fontSize = 14.sp,
                color = colors.ink.copy(alpha = 0.7f),
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp * 1000)
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(date)
}
