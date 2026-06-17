package com.ely.kian.ui.screens.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.ChatMessage
import com.ely.kian.ui.screens.chat.ChatViewModel
import com.ely.kian.ui.screens.chat.formatTimestamp
import com.ely.kian.ui.theme.KianColors
import kotlinx.serialization.json.*

@Composable
fun MessageContent(
    message: ChatMessage,
    viewModel: ChatViewModel,
    colors: KianColors,
    onActionClick: () -> Unit
) {
    val textColor = if (message.isMine) Color.White else colors.ink
    val metadata = remember(message.metadata) {
        try {
            message.metadata?.let { Json.parseToJsonElement(it).jsonObject }
        } catch (e: Exception) {
            null
        }
    }

    Column {
        if (message.replyTo != null) {
            ReplyBubble(message.replyTo!!, viewModel, colors, message.isMine)
        }

        if (metadata != null) {
            TokenMessageCard(message, metadata, viewModel, colors)
        } else {
            Text(
                text = message.content,
                color = textColor,
                fontSize = 16.sp,
                lineHeight = 22.sp
            )
        }

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
                    "received" -> Icons.Default.CheckCircle
                    else -> Icons.Default.Check
                }
                val tint = if (message.status == "read" || message.status == "received") Color(0xFF4ADE80) else textColor.copy(alpha = 0.6f)
                Icon(
                    imageVector = icon,
                    contentDescription = message.status,
                    modifier = Modifier.size(17.dp),
                    tint = tint
                )
            }
        }
    }
}

@Composable
fun ReplyBubble(replyToId: String, viewModel: ChatViewModel, colors: KianColors, isMine: Boolean) {
    var repliedMessage by remember { mutableStateOf<ChatMessage?>(null) }
    
    LaunchedEffect(replyToId) {
        repliedMessage = viewModel.getMessageById(replyToId)
    }

    repliedMessage?.let { msg ->
        val background = if (isMine) Color.White.copy(alpha = 0.15f) else colors.ink.copy(alpha = 0.05f)
        val accentColor = if (isMine) Color.White.copy(alpha = 0.7f) else colors.accent
        val contentColor = if (isMine) Color.White.copy(alpha = 0.6f) else colors.ink.copy(alpha = 0.6f)

        Surface(
            color = background,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.width(3.dp).height(24.dp).background(accentColor, RoundedCornerShape(2.dp)))
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = if (msg.isMine) stringResource(R.string.you) else msg.pubkey.take(8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor
                    )
                    Text(
                        text = msg.content,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun MessageReactions(reactionsJson: String, colors: KianColors) {
    val reactions = remember(reactionsJson) {
        try {
            Json.decodeFromString<Map<String, List<String>>>(reactionsJson)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    if (reactions.isNotEmpty()) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            reactions.forEach { (emoji, pubkeys) ->
                Row(
                    modifier = Modifier.padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = emoji, fontSize = 16.sp)
                    if (pubkeys.size > 1) {
                        Text(
                            text = pubkeys.size.toString(), 
                            fontSize = 10.sp, 
                            fontWeight = FontWeight.Bold,
                            color = colors.muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TokenMessageCard(
    message: ChatMessage,
    metadata: JsonObject,
    viewModel: ChatViewModel,
    colors: KianColors
) {
    val type = metadata["type"]?.jsonPrimitive?.content ?: ""
    val assetName = metadata["asset_name"]?.jsonPrimitive?.content ?: "Voucher"
    val amount = metadata["amount"]?.jsonPrimitive?.content ?: ""
    val utxoId = metadata["utxo_id"]?.jsonPrimitive?.content
    
    val isMine = message.isMine
    val cardColor = if (isMine) colors.accent.copy(alpha = 0.2f) else colors.panel
    val borderColor = if (isMine) colors.accent.copy(alpha = 0.4f) else colors.line
    
    val myUtxos by viewModel.getUtxos().collectAsState(initial = emptyList())
    val isConfirmed = remember(myUtxos, utxoId) {
        utxoId != null && myUtxos.any { it.prevUtxoId == utxoId }
    }

    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = colors.accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when(type) {
                                "token_mint" -> Icons.Default.CardGiftcard
                                "token_redemption" -> Icons.Default.LocalShipping
                                else -> Icons.Default.Send
                            },
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                Column {
                    Text(
                        text = when(type) {
                            "token_mint" -> stringResource(R.string.genesis_mint)
                            "token_redemption" -> stringResource(R.string.product_redemption)
                            else -> stringResource(R.string.token_transfer)
                        },
                        fontSize = 12.sp,
                        color = colors.muted,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "$amount $assetName",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isMine) Color.White else colors.ink
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            val statusText = when {
                message.status == "received" -> stringResource(R.string.order_completed)
                isConfirmed || message.status == "delivered" -> stringResource(R.string.verified_by_producer)
                type == "token_mint" -> stringResource(R.string.issued)
                type == "token_redemption" -> if (isMine) stringResource(R.string.waiting_for_delivery) else stringResource(R.string.pending_delivery)
                else -> stringResource(R.string.authenticating)
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isConfirmed && message.status != "delivered" && message.status != "received" && type != "token_mint") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 2.dp,
                        color = colors.accent.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    statusText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConfirmed || message.status == "delivered" || message.status == "received" || type == "token_mint") Color(0xFF4ADE80) else colors.muted
                )
            }

            if (type == "token_redemption" && isMine && message.status != "received") {
                Spacer(modifier = Modifier.height(12.dp))
                var showConfirmDialog by remember { mutableStateOf(false) }

                Button(
                    onClick = { showConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(stringResource(R.string.confirm_receipt), color = Color.White, fontSize = 14.sp)
                }

                if (showConfirmDialog) {
                    AlertDialog(
                        onDismissRequest = { showConfirmDialog = false },
                        title = { Text(stringResource(R.string.confirm_delivery)) },
                        text = { Text(stringResource(R.string.confirm_delivery_confirm)) },
                        confirmButton = {
                            TextButton(onClick = {
                                if (utxoId != null) {
                                    viewModel.confirmProductReceipt(message.id, message.contactPubkey, utxoId)
                                }
                                showConfirmDialog = false
                            }) {
                                Text(stringResource(R.string.confirm), color = colors.accent, fontWeight = FontWeight.Bold)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirmDialog = false }) {
                                Text(stringResource(R.string.cancel), color = colors.muted)
                            }
                        },
                        containerColor = colors.panel,
                        titleContentColor = colors.ink,
                        textContentColor = colors.ink
                    )
                }
            }
        }
    }
}
