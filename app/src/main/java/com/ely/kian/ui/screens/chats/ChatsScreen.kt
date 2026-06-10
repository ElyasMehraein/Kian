package com.ely.kian.ui.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.theme.KianTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape

@Composable
fun ChatsScreen(
    viewModel: com.ely.kian.ui.screens.chat.ChatViewModel,
    onChatClick: (String) -> Unit
) {
    val kianColors = KianTheme.colors
    val conversations by viewModel.conversations.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Chats",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        )

        LazyColumn {
            items(conversations) { conversation ->
                ConversationItem(
                    name = conversation.peerName,
                    lastMessage = conversation.lastMessage ?: "",
                    time = conversation.lastMessageAt?.toString() ?: "", // In a real app, format this date
                    unreadCount = conversation.unreadCount,
                    onClick = { onChatClick(conversation.peerPubkey) }
                )
            }
        }
    }
}

@Composable
fun ConversationItem(
    name: String,
    lastMessage: String,
    time: String,
    unreadCount: Int,
    onClick: () -> Unit
) {
    val kianColors = KianTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(name = name, size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = name, fontWeight = FontWeight.Bold, color = kianColors.ink, fontSize = 16.sp)
                    Text(text = time, fontSize = 12.sp, color = kianColors.ink.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = lastMessage,
                    fontSize = 14.sp,
                    color = if (unreadCount > 0) kianColors.ink else kianColors.ink.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
            if (unreadCount > 0) {
                Box(
                    modifier = Modifier.background(kianColors.accent, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(text = unreadCount.toString(), color = kianColors.canvas, fontSize = 10.sp)
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = kianColors.line)
    }
}
