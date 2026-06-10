package com.ely.kian.ui.screens.wallet

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
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
import com.ely.kian.KianApp
import com.ely.kian.data.local.entities.TokenUtxo
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.PendingItem
import com.ely.kian.ui.theme.KianTheme
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun WalletScreen(onSendToken: () -> Unit, onProducerClick: (String) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: WalletViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = WalletViewModel.provideFactory(app.container.tokenRepository)
    )

    val balances by viewModel.balances.collectAsState()
    val utxos by viewModel.utxos.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val activityFilter = viewModel.activityFilter

    val filteredPending = remember(pending, activityFilter) {
        if (activityFilter == "all") pending
        else pending.filter { it.status == activityFilter }
    }

    val kianColors = KianTheme.colors

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(kianColors.canvas),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp)
        ) {
            item {
                Text(
                    text = "Wallet",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = kianColors.ink,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
            }

            item {
                Text(
                    text = "Token balances",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.ink,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (balances.isEmpty()) {
                item {
                    Text(
                        text = "No token balances yet.",
                        fontSize = 15.sp,
                        color = kianColors.muted,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            } else {
                items(balances) { item ->
                    BalanceRow(item, onProducerClick = onProducerClick, formatAssetRef = viewModel::formatAssetRef)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }

            item {
                Text(
                    text = "Token transfer activity",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.ink,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    val filters = listOf(
                        "all" to "All",
                        "waiting_mint" to "Waiting",
                        "fulfilled" to "Completed",
                        "rejected" to "Rejected",
                        "offline" to "Offline"
                    )
                    items(filters) { (id, label) ->
                        SelectorChip(
                            label = label,
                            active = activityFilter == id,
                            onClick = { viewModel.setFilter(id) }
                        )
                    }
                }
            }

            if (filteredPending.isEmpty()) {
                item {
                    Text(
                        text = "No token transfer activity for this filter yet.",
                        fontSize = 15.sp,
                        color = kianColors.muted,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            } else {
                items(filteredPending) { item ->
                    PendingRow(item, formatAssetRef = viewModel::formatAssetRef)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }

            item {
                Text(
                    text = "Spendable token entries",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.ink,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            if (utxos.isEmpty()) {
                item {
                    Text(
                        text = "No spendable token entries yet.",
                        fontSize = 15.sp,
                        color = kianColors.muted,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                }
            } else {
                items(utxos) { item ->
                    UtxoRow(item, label = viewModel.formatAssetRef(item.assetRef), formatAssetRef = viewModel::formatAssetRef)
                    Spacer(modifier = Modifier.height(10.dp))
                }
                item { Spacer(modifier = Modifier.height(14.dp)) }
            }
            
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }

        ExtendedFloatingActionButton(
            onClick = onSendToken,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 20.dp),
            containerColor = kianColors.ink,
            contentColor = kianColors.canvas,
            shape = RoundedCornerShape(28.dp),
            icon = { Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(20.dp)) },
            text = { Text("Send Token", fontWeight = FontWeight.SemiBold) }
        )
    }
}

@Composable
fun BalanceRow(
    item: BalanceItem,
    onProducerClick: (String) -> Unit,
    formatAssetRef: (String) -> String
) {
    val kianColors = KianTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = kianColors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            if (item.images.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(128.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(kianColors.line)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Text(text = item.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
            Text(
                text = item.description.ifEmpty { "No description" },
                fontSize = 14.sp,
                color = kianColors.muted,
                lineHeight = 20.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            if (item.categories.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.categories.take(3).forEach { category ->
                        Surface(
                            shape = CircleShape,
                            color = kianColors.line.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = category,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = kianColors.muted,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Row(
                modifier = Modifier.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(text = item.amount.toString(), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                Text(text = item.unit, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kianColors.muted)
            }
            
            Surface(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { onProducerClick(item.producer) },
                shape = CircleShape,
                color = kianColors.infoSoft
            ) {
                Text(
                    text = "Producer: ${formatAssetRef(item.producer)}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun UtxoRow(item: TokenUtxo, label: String, formatAssetRef: (String) -> String) {
    val kianColors = KianTheme.colors
    val sdf = remember { SimpleDateFormat("MMM d, yyyy, h:mm a", Locale.getDefault()) }
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
            Text(
                text = "${item.amount} • ${formatAssetRef(item.assetRef)}",
                fontSize = 14.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Issued ${sdf.format(Date(item.createdAt * 1000))}",
                fontSize = 12.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun PendingRow(item: PendingItem, formatAssetRef: (String) -> String) {
    val kianColors = KianTheme.colors
    
    val (containerColor, borderColor, textColor, metaColor, label, detail) = when (item.status) {
        "fulfilled" -> HexColorPair(kianColors.successSoft, kianColors.success, kianColors.ink, kianColors.success, "Completed", "completed after issuer confirmation")
        "rejected" -> HexColorPair(kianColors.danger.copy(alpha = 0.1f), kianColors.danger, kianColors.ink, kianColors.danger, "Rejected", "rejected because another transfer was approved")
        "offline" -> HexColorPair(kianColors.panel, kianColors.muted, kianColors.ink, kianColors.muted, "Queued offline", "queued until a relay connection is available")
        else -> HexColorPair(kianColors.warningSoft, kianColors.warning, kianColors.ink, kianColors.warning, "Waiting for issuer", "waiting for token issuer confirmation")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = formatAssetRef(item.assetRef), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = textColor)
            Text(
                text = "${item.amount} $detail",
                fontSize = 14.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(text = label, fontSize = 12.sp, color = metaColor, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Recipient: ${formatAssetRef(item.recipient)}", fontSize = 12.sp, color = metaColor, modifier = Modifier.padding(top = 4.dp))
            Text(text = "Activity id: ${formatAssetRef(item.eventId)}", fontSize = 12.sp, color = metaColor, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private data class HexColorPair(
    val container: Color,
    val border: Color,
    val text: Color,
    val meta: Color,
    val label: String,
    val detail: String
)

@Composable
fun SelectorChip(label: String, active: Boolean, onClick: () -> Unit) {
    val kianColors = KianTheme.colors
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = CircleShape,
        color = if (active) kianColors.accentSoft else kianColors.canvas,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (active) kianColors.accent else kianColors.line)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
            color = if (active) kianColors.accent else kianColors.muted,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}
