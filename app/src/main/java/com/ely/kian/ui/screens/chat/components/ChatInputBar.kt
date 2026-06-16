package com.ely.kian.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.ui.theme.KianColors

@Composable
fun ChatInput(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onProductClick: () -> Unit,
    onTokenClick: () -> Unit,
    colors: KianColors
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
            IconButton(onClick = onProductClick) {
                Icon(Icons.Default.Inventory2, contentDescription = "Send Product", tint = colors.accent)
            }
            
            IconButton(onClick = onTokenClick) {
                Icon(Icons.Default.ConfirmationNumber, contentDescription = "Send Token", tint = colors.accent)
            }

            TextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.type_message), color = colors.ink.copy(alpha = 0.5f)) },
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

@Composable
fun ReplyPreview(message: ChatMessage, colors: KianColors, onCancel: () -> Unit) {
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
                    text = if (message.isMine) stringResource(R.string.replying_to_yourself) else stringResource(R.string.replying_to, message.pubkey.take(8)),
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
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cancel), tint = colors.ink.copy(alpha = 0.4f))
            }
        }
    }
}
