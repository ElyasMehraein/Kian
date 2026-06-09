package com.ely.kian.ui.screens.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import com.ely.kian.ui.theme.Accent
import com.ely.kian.ui.theme.Canvas
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line
import com.ely.kian.ui.theme.Panel

data class Message(
    val id: String,
    val sender: String,
    val content: String,
    val timestamp: Long,
    val isMine: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    peerPubkey: String,
    onBack: () -> Unit,
    onCart: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val mockMessages = listOf(
        Message("1", "peer", "Hi there!", 1000, false),
        Message("2", "me", "Hello! Is the honey still available?", 2000, true),
        Message("3", "peer", "Yes, it is. How many would you like?", 3000, false)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(name = "Peer", size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(text = "Peer Name", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
                            Text(text = "View profile", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onCart) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Cart", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            ChatComposer(
                text = text,
                onTextChange = { text = it },
                onSend = { text = "" }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(mockMessages) { message ->
                MessageBubble(message)
            }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (message.isMine) 16.dp else 4.dp,
                    bottomEnd = if (message.isMine) 4.dp else 16.dp
                ))
                .background(if (message.isMine) Ink else Panel)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = message.content,
                color = if (message.isMine) Color.White else Ink,
                fontSize = 15.sp,
                lineHeight = 22.sp
            )
        }
    }
}

@Composable
fun ChatComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Surface(
        tonalElevation = 2.dp,
        color = Canvas,
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
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Send Token", tint = Color.Gray)
            }
            IconButton(onClick = { }) {
                Icon(Icons.Default.LocalShipping, contentDescription = "Send Product", tint = Color.Gray)
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
                    focusedBorderColor = Line,
                    unfocusedBorderColor = Line,
                    focusedTextColor = Ink,
                    unfocusedTextColor = Ink
                ),
                maxLines = 4
            )
            
            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = if (text.isNotBlank()) Accent else Color.Gray
                )
            }
        }
    }
}
