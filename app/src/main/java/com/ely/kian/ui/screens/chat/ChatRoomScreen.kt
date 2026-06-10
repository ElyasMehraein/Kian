package com.ely.kian.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.theme.KianTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    peerPubkey: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onCart: () -> Unit
) {
    val kianColors = KianTheme.colors
    var text by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf(false) }
    
    val messages by viewModel.getMessages(peerPubkey).collectAsState(initial = emptyList())
    var peerName by remember { mutableStateOf(peerPubkey.take(8)) }

    LaunchedEffect(peerPubkey) {
        viewModel.markAsRead(peerPubkey)
        val profile = viewModel.getProfile(peerPubkey)
        profile?.let {
            peerName = it.displayName ?: it.name ?: peerPubkey.take(8)
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Conversation") },
            text = { Text("This will delete the chat history for both you and the recipient. Are you sure?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteConversation(peerPubkey)
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("Delete Everywhere", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = kianColors.canvas,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { /* Show profile */ }
                    ) {
                        InitialAvatar(name = peerName, size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = peerName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                            Text(text = "View profile", fontSize = 12.sp, color = kianColors.ink.copy(alpha = 0.5f))
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = kianColors.ink
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCart) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart, 
                            contentDescription = "Cart", 
                            tint = kianColors.ink.copy(alpha = 0.5f)
                        )
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep, 
                            contentDescription = "Delete Chat", 
                            tint = Color.Red.copy(alpha = 0.6f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kianColors.canvas)
            )
        },
        bottomBar = {
            ChatComposer(
                text = text,
                onTextChange = { text = it },
                onSend = { 
                    viewModel.sendMessage(peerPubkey, text)
                    text = "" 
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            reverseLayout = false
        ) {
            items(messages) { message ->
                // Mark incoming messages as read when they appear
                if (message.sender == peerPubkey && message.status != "read") {
                    LaunchedEffect(message.id) {
                        viewModel.markMessageAsRead(peerPubkey, message.id)
                    }
                }

                MessageBubble(
                    content = message.content,
                    isMine = message.sender != peerPubkey,
                    status = message.status
                )
            }
        }
    }
}

@Composable
fun MessageBubble(content: String, isMine: Boolean, status: String) {
    val kianColors = KianTheme.colors
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    ))
                    .background(if (isMine) kianColors.ink else kianColors.panel)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = content,
                    color = if (isMine) kianColors.canvas else kianColors.ink,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
            
            if (isMine) {
                val statusIcon = when (status) {
                    "sending" -> Icons.Default.Schedule
                    "sent" -> Icons.Default.Check
                    "delivered" -> Icons.Default.DoneAll
                    "read" -> Icons.Default.DoneAll
                    else -> Icons.Default.Check
                }
                val statusColor = if (status == "read") kianColors.accent else kianColors.ink.copy(alpha = 0.4f)
                
                Icon(
                    imageVector = statusIcon,
                    contentDescription = status,
                    modifier = Modifier.size(12.dp).padding(top = 2.dp),
                    tint = statusColor
                )
            }
        }
    }
}

@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val kianColors = KianTheme.colors
    Surface(
        tonalElevation = 2.dp,
        color = kianColors.canvas,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { }) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Send Token", tint = kianColors.ink.copy(alpha = 0.5f))
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.LocalShipping, contentDescription = "Send Product", tint = kianColors.ink.copy(alpha = 0.5f))
            }
            
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = { Text("Message...") },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = kianColors.line,
                    unfocusedBorderColor = kianColors.line,
                    focusedTextColor = kianColors.ink,
                    unfocusedTextColor = kianColors.ink,
                    cursorColor = kianColors.accent
                ),
                maxLines = 4
            )
            
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) kianColors.accent else kianColors.ink.copy(alpha = 0.3f)
                )
            }
        }
    }
}
