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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.KianApp
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.data.repository.PendingItem
import com.ely.kian.ui.components.ScreenHeader as KianScreenHeader
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
    val pending by viewModel.pending.collectAsState()
    val activityFilter = viewModel.activityFilter

    val filteredPending = remember(pending, activityFilter) {
        if (activityFilter == "all") pending
        else pending.filter { it.status == activityFilter }
    }

    val kianColors = KianTheme.colors

    Scaffold(
        containerColor = kianColors.canvas,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    KianScreenHeader(
                        title = "My Wallet",
                        subtitle = "Manage your digital trade assets",
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        shape = CircleShape,
                        color = kianColors.panel,
                        modifier = Modifier.padding(end = 20.dp).size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan", tint = kianColors.ink)
                        }
                    }
                }
            }

            // Summary Section
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 32.dp),
                    color = kianColors.accent,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text("Total Assets", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(
                            text = "${balances.size} Categories", 
                            color = Color.White, 
                            fontSize = 28.sp, 
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "All assets are secured via Nostr UTXOs", 
                                color = Color.White.copy(alpha = 0.8f), 
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader("Token Balances", Icons.Default.Token)
            }

            if (balances.isEmpty()) {
                item { EmptyState("No assets found in your wallet.") }
            } else {
                items(balances) { item ->
                    BalanceRow(
                        item = item,
                        onProducerClick = onProducerClick,
                        formatAssetRef = viewModel::formatAssetRef,
                        onToggleShowcase = { assetRef, isShowcase -> viewModel.toggleShowcase(assetRef, isShowcase) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader("Transfer Activity", Icons.Default.History)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    val filters = listOf(
                        "all" to "All",
                        "waiting_mint" to "Pending",
                        "fulfilled" to "Completed",
                        "rejected" to "Failed"
                    )
                    items(filters) { (id, label) ->
                        FilterChip(
                            label = label,
                            selected = activityFilter == id,
                            onClick = { viewModel.setFilter(id) },
                            colors = kianColors
                        )
                    }
                }
            }

            if (filteredPending.isEmpty()) {
                item { EmptyState("No activity matching this filter.") }
            } else {
                items(filteredPending) { item ->
                    PendingRow(item, formatAssetRef = viewModel::formatAssetRef)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    val kianColors = KianTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp)
    ) {
        Icon(icon, contentDescription = null, tint = kianColors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = kianColors.ink
        )
    }
}

@Composable
fun EmptyState(message: String) {
    val kianColors = KianTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        color = kianColors.panel.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(message, color = kianColors.muted, fontSize = 14.sp)
        }
    }
}

@Composable
fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit, colors: com.ely.kian.ui.theme.KianColors) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.accent else colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) colors.accent else colors.line)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.muted
        )
    }
}

@Composable
fun BalanceRow(
    item: BalanceItem,
    onProducerClick: (String) -> Unit,
    formatAssetRef: (String) -> String,
    onToggleShowcase: (String, Boolean) -> Unit
) {
    val kianColors = KianTheme.colors
    var producerName by remember { mutableStateOf(formatAssetRef(item.producer)) }
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    
    LaunchedEffect(item.producer) {
        val profile = app.container.userProfileDao.getProfile(item.producer)
        if (profile != null) {
            producerName = profile.displayName ?: profile.name ?: producerName
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = kianColors.panel,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name, 
                        fontSize = 20.sp, 
                        fontWeight = FontWeight.ExtraBold, 
                        color = kianColors.ink
                    )
                    if (item.description.isNotEmpty()) {
                        Text(
                            text = item.description,
                            fontSize = 14.sp,
                            color = kianColors.muted,
                            maxLines = 2,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                
                // Asset Badge
                Surface(
                    color = kianColors.accent,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.amount.toString(), 
                            fontSize = 24.sp, 
                            fontWeight = FontWeight.Black, 
                            color = Color.White
                        )
                        Text(
                            text = item.unit.lowercase(), 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            
            if (item.categories.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item.categories.forEach { category ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = kianColors.accent.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = "#$category",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = kianColors.accent,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = kianColors.line.copy(alpha = 0.5f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onProducerClick(item.producer) }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Producer Initial Circle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(kianColors.line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            producerName.take(1).uppercase(),
                            color = kianColors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "PRODUCER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = kianColors.muted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = producerName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = kianColors.ink
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = item.isShowcase,
                        onCheckedChange = { onToggleShowcase(item.assetRef, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = kianColors.accent,
                            uncheckedThumbColor = kianColors.muted,
                            uncheckedTrackColor = kianColors.line
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                    Text(
                        text = "Showcase",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = kianColors.muted
                    )
                }
                
                IconButton(onClick = { /* TODO: Show detailed token history/UTXOs */ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = kianColors.muted)
                }
            }
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
        else -> HexColorPair(kianColors.warningSoft, kianColors.warning, kianColors.ink, kianColors.warning, "Waiting", "waiting for token issuer confirmation")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = item.assetName, 
                    fontSize = 16.sp, 
                    fontWeight = FontWeight.Bold, 
                    color = textColor
                )
                Text(
                    text = label.uppercase(), 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black, 
                    color = metaColor,
                    letterSpacing = 1.sp
                )
            }
            
            Text(
                text = "${item.amount} units • $detail",
                fontSize = 14.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = borderColor.copy(alpha = 0.1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = kianColors.muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "To: ${formatAssetRef(item.recipient)}", 
                    fontSize = 12.sp, 
                    color = kianColors.muted
                )
            }
            
            Row(modifier = Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, tint = kianColors.muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ID: ${formatAssetRef(item.eventId)}", 
                    fontSize = 12.sp, 
                    color = kianColors.muted,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                )
            }
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
