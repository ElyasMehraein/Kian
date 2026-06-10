package com.ely.kian.ui.screens.chats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.theme.KianTheme

data class Conversation(
    val id: String,
    val name: String,
    val lastMessage: String,
    val time: String,
    val unreadCount: Int = 0
)

val mockConversations = listOf(
    Conversation("1", "Alice", "See you tomorrow at the market!", "10:30 AM", 2),
    Conversation("2", "Bob", "Thanks for the honey!", "Yesterday"),
    Conversation("3", "Charlie", "Is the coffee still available?", "Monday"),
)

@Composable
fun ChatsScreen() {
    val kianColors = KianTheme.colors
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
            items(mockConversations) { conversation ->
                ConversationItem(conversation)
            }
        }
    }
}

@Composable
fun ConversationItem(conversation: Conversation) {
    val kianColors = KianTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Navigate to chat room */ }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InitialAvatar(name = conversation.name, size = 56.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = conversation.name, fontWeight = FontWeight.Bold, color = kianColors.ink, fontSize = 16.sp)
                    Text(text = conversation.time, fontSize = 12.sp, color = kianColors.ink.copy(alpha = 0.5f))
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = conversation.lastMessage,
                    fontSize = 14.sp,
                    color = if (conversation.unreadCount > 0) kianColors.ink else kianColors.ink.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = if (conversation.unreadCount > 0) FontWeight.Medium else FontWeight.Normal
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = kianColors.line)
    }
}
