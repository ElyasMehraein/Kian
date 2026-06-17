package com.ely.kian.ui.screens.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import com.ely.kian.KianApp
import com.ely.kian.ui.theme.KianTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendVoucherScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: SendVoucherViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = SendVoucherViewModel.provideFactory(app.container.voucherRepository)
    )

    val kianColors = KianTheme.colors
    val tokenItems by viewModel.tokenItems
    val quantities = viewModel.quantities

    Scaffold(
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(kianColors.canvas)
                    .border(1.dp, kianColors.line, androidx.compose.ui.graphics.RectangleShape),
                color = kianColors.canvas
            ) {
                Button(
                    onClick = {
                        viewModel.handleSend(
                            onSuccess = { onBack() },
                            onError = { /* Show error */ }
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = kianColors.accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(20.dp),
                    enabled = !viewModel.isSending && quantities.isNotEmpty()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(if (viewModel.isSending) stringResource(R.string.sending) else stringResource(R.string.send_token), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(kianColors.canvas)
                .padding(padding),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(text = stringResource(R.string.send_tokens), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                        Text(text = stringResource(R.string.choose_token_desc), fontSize = 14.sp, color = kianColors.muted)
                    }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(kianColors.panel)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), tint = kianColors.ink)
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.recipient),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = kianColors.muted,
                            letterSpacing = 2.sp
                        )
                        OutlinedTextField(
                            value = viewModel.recipient,
                            onValueChange = { viewModel.recipient = it },
                            placeholder = { Text(stringResource(R.string.recipient_pubkey_hint), color = kianColors.muted) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = kianColors.accent,
                                unfocusedBorderColor = kianColors.line
                            ),
                            singleLine = true
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                Text(
                    text = stringResource(R.string.spendable_entries),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = kianColors.muted,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (tokenItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .background(Color.White)
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = stringResource(R.string.no_spendable_found), color = kianColors.muted, fontSize = 14.sp)
                    }
                }
            } else {
                items(tokenItems) { item ->
                    VoucherSendCard(
                        item = item,
                        quantity = quantities[item.utxo.utxoId] ?: 0L,
                        onUpdateQuantity = { delta ->
                            viewModel.updateQuantity(item.utxo.utxoId, delta, item.utxo.amount)
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
fun VoucherSendCard(
    item: VoucherCardItem,
    quantity: Long,
    onUpdateQuantity: (Long) -> Unit
) {
    val kianColors = KianTheme.colors
    val active = quantity > 0
    val sdf = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (active) kianColors.accent.copy(alpha = 0.5f) else Color.Transparent
        ),
        shadowElevation = 2.dp
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(kianColors.panel)
                    .border(1.dp, kianColors.line, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ConfirmationNumber,
                    contentDescription = null,
                    tint = kianColors.muted,
                    modifier = Modifier.size(30.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
                Text(
                    text = item.description ?: "",
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Text(
                    text = stringResource(R.string.issued_at, sdf.format(Date(item.utxo.createdAt * 1000))),
                    fontSize = 12.sp,
                    color = kianColors.muted,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.available_balance, item.utxo.amount, item.unit),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kianColors.muted
                    )

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(kianColors.panel)
                            .padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onUpdateQuantity(-1) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(Icons.Default.Remove, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        
                        Text(
                            text = quantity.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = kianColors.ink,
                            modifier = Modifier.widthIn(min = 24.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        IconButton(
                            onClick = { onUpdateQuantity(1) },
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}
