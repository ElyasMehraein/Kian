package com.ely.kian.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.ui.components.util.setText
import com.ely.kian.ui.screens.chat.components.ChatBubbleLayout
import com.ely.kian.ui.theme.KianTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatroomScreen(
    contactPubkey: String,
    viewModel: ChatViewModel,
    onBack: () -> Unit
) {
    val messages by viewModel.getMessages(contactPubkey).collectAsState()
    var textState by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<ChatMessage?>(null) }
    
    var contactName by remember { mutableStateOf(contactPubkey.take(8) + "...") }
    
    val kianColors = KianTheme.colors
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(contactPubkey) {
        val profile = viewModel.getProfile(contactPubkey)
        if (profile != null) {
            contactName = profile.displayName ?: profile.name ?: contactName
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
            viewModel.markAsRead(contactPubkey)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text(contactName, color = kianColors.ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(contactPubkey.take(12) + "...", color = kianColors.muted, fontSize = 11.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = kianColors.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kianColors.canvas)
            )
        },
        bottomBar = {
            Column {
                if (replyingTo != null) {
                    ReplyPreview(replyingTo!!, kianColors) { replyingTo = null }
                }
                ChatInput(
                    text = textState,
                    onTextChange = { textState = it },
                    onSend = {
                        if (textState.isNotBlank()) {
                            viewModel.sendMessage(contactPubkey, textState)
                            textState = ""
                            replyingTo = null
                        }
                    },
                    colors = kianColors
                )
            }
        },
        containerColor = kianColors.canvas
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                var showMenu by remember { mutableStateOf(false) }
                val clipboard = androidx.compose.ui.platform.LocalClipboard.current

                ChatBubbleLayout(
                    isMine = message.isMine,
                    colors = kianColors,
                    onLongClick = { showMenu = true }
                ) {
                    val textColor = if (message.isMine) Color.White else kianColors.ink
                    
                    Column {
                        Text(
                            text = message.content, 
                            color = textColor, 
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )
                        Row(
                            modifier = Modifier.align(Alignment.End).padding(top = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTimestamp(message.createdAt),
                                fontSize = 11.sp,
                                color = textColor.copy(alpha = 0.7f)
                            )
                            if (message.isMine) {
                                Spacer(modifier = Modifier.width(6.dp))
                                val icon = when (message.status) {
                                    "read" -> Icons.Default.DoneAll
                                    "delivered" -> Icons.Default.DoneAll
                                    "sent" -> Icons.Default.Check
                                    else -> Icons.Default.Check // pending or others
                                }
                                val tint = if (message.status == "read") Color(0xFF4ADE80) else textColor.copy(alpha = 0.6f)
                                Icon(
                                    imageVector = icon,
                                    contentDescription = message.status,
                                    modifier = Modifier.size(17.dp),
                                    tint = tint
                                )
                            }
                        }
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(kianColors.panel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reply", color = kianColors.ink) },
                            onClick = {
                                replyingTo = message
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Reply, contentDescription = null, tint = kianColors.ink) }
                        )
                        DropdownMenuItem(
                            text = { Text("Copy", color = kianColors.ink) },
                            onClick = {
                                scope.launch { clipboard.setText(message.content) }
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = kianColors.ink) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            onClick = {
                                viewModel.deleteMessage(message.id)
                                showMenu = false
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReplyPreview(message: ChatMessage, colors: com.ely.kian.ui.theme.KianColors, onCancel: () -> Unit) {
    Surface(
        color = colors.panel,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(32.dp).background(colors.accent, RoundedCornerShape(2.dp)))
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (message.isMine) "Replying to yourself" else "Replying to ${message.pubkey.take(8)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.accent
                )
                Text(
                    text = message.content,
                    fontSize = 13.sp,
                    maxLines = 1,
                    color = colors.ink.copy(alpha = 0.6f)
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Delete, contentDescription = "Cancel", tint = colors.ink.copy(alpha = 0.4f))
            }
        }
    }
}

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    Surface(
        color = colors.canvas,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(8.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a message...", color = colors.ink.copy(alpha = 0.5f)) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = colors.panel,
                    unfocusedContainerColor = colors.panel,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.ink,
                    unfocusedTextColor = colors.ink
                ),
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    disabledContainerColor = colors.panel
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
