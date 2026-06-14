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
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
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
        factory = WalletViewModel.provideFactory(
            app.container.tokenRepository,
            app.container.productRepository,
            app.container.keyDao
        )
    )

    val balances by viewModel.balances.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val myCategories by viewModel.myCategories.collectAsState()
    val activityFilter = viewModel.activityFilter

    var editingToken by remember { mutableStateOf<BalanceItem?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }

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
                        title = stringResource(R.string.my_wallet),
                        subtitle = stringResource(R.string.wallet_desc),
                        modifier = Modifier.weight(1f)
                    )
                    
                    Surface(
                        shape = CircleShape,
                        color = kianColors.panel,
                        modifier = Modifier.padding(end = 20.dp).size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.QrCodeScanner, contentDescription = stringResource(R.string.scan), tint = kianColors.ink)
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
                        Text(stringResource(R.string.total_assets), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        Text(
                            text = stringResource(R.string.categories_count, balances.size), 
                            color = Color.White, 
                            fontSize = 28.sp, 
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Info, contentDescription = null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.utxo_secured), 
                                color = Color.White.copy(alpha = 0.8f), 
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.token_balances), Icons.Default.Token)
            }

            if (balances.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_assets_found)) }
            } else {
                items(balances) { item ->
                    BalanceRow(
                        item = item,
                        myCategories = myCategories,
                        onProducerClick = onProducerClick,
                        formatAssetRef = viewModel::formatAssetRef,
                        onToggleShowcase = { assetRef, isShowcase -> viewModel.toggleShowcase(assetRef, isShowcase) },
                        onEdit = { 
                            editingToken = it
                            showEditSheet = true
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.transfer_activity), Icons.Default.History)
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                ) {
                    val filters = listOf(
                        "all" to R.string.all,
                        "waiting_mint" to R.string.pending,
                        "fulfilled" to R.string.completed,
                        "rejected" to R.string.failed
                    )
                    items(filters) { (id, labelId) ->
                        FilterChip(
                            label = stringResource(labelId),
                            selected = activityFilter == id,
                            onClick = { viewModel.setFilter(id) },
                            colors = kianColors
                        )
                    }
                }
            }

            if (filteredPending.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_activity_filter)) }
            } else {
                items(filteredPending) { item ->
                    PendingRow(item, formatAssetRef = viewModel::formatAssetRef)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showEditSheet && editingToken != null) {
        TokenEditBottomSheet(
            token = editingToken!!,
            allCategories = myCategories,
            onDismiss = { showEditSheet = false },
            onSave = { name, desc, selectedCats ->
                viewModel.updateTokenDetails(editingToken!!.assetRef, name, desc, selectedCats)
                showEditSheet = false
            }
        )
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
    myCategories: List<com.ely.kian.data.local.entities.ProductCategory>,
    onProducerClick: (String) -> Unit,
    formatAssetRef: (String) -> String,
    onToggleShowcase: (String, Boolean) -> Unit,
    onEdit: (BalanceItem) -> Unit
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = kianColors.panel,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.clickable { onEdit(item) }) {
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
                        item.categories.forEach { categoryId ->
                            val categoryName = myCategories.find { it.id == categoryId }?.name ?: categoryId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = kianColors.accent.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "#$categoryName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kianColors.accent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.tap_to_edit),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.accent,
                    modifier = Modifier.padding(top = 12.dp)
                )
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
                            text = stringResource(R.string.producer),
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
                        text = stringResource(R.string.showcase),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokenEditBottomSheet(
    token: BalanceItem,
    allCategories: List<com.ely.kian.data.local.entities.ProductCategory>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit
) {
    val kianColors = KianTheme.colors
    var name by remember { mutableStateOf(token.name) }
    var description by remember { mutableStateOf(token.description) }
    var selectedCategoryIds by remember { mutableStateOf(token.categories) }
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = kianColors.canvas,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.edit_token),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink
                    )
                    Text(
                        text = stringResource(R.string.edit_token_desc),
                        fontSize = 14.sp,
                        color = kianColors.muted
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(kianColors.panel, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp), tint = kianColors.ink)
                }
            }

            TokenCategoryPicker(
                categories = allCategories,
                selectedIds = selectedCategoryIds,
                onChange = { selectedCategoryIds = it }
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text(stringResource(R.string.token_name), color = kianColors.muted) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = kianColors.line,
                    focusedBorderColor = kianColors.accent,
                    unfocusedContainerColor = kianColors.panel,
                    focusedContainerColor = kianColors.panel,
                    focusedTextColor = kianColors.ink,
                    unfocusedTextColor = kianColors.ink
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                placeholder = { Text(stringResource(R.string.description), color = kianColors.muted) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = kianColors.line,
                    focusedBorderColor = kianColors.accent,
                    unfocusedContainerColor = kianColors.panel,
                    focusedContainerColor = kianColors.panel,
                    focusedTextColor = kianColors.ink,
                    unfocusedTextColor = kianColors.ink
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            com.ely.kian.ui.components.KianButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(name, description, selectedCategoryIds) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun TokenCategoryPicker(
    categories: List<com.ely.kian.data.local.entities.ProductCategory>,
    selectedIds: List<String>,
    onChange: (List<String>) -> Unit
) {
    val kianColors = KianTheme.colors
    val selectedPath = remember(categories, selectedIds) {
        val path = mutableListOf<com.ely.kian.data.local.entities.ProductCategory>()
        var currentId = selectedIds.lastOrNull()
        while (currentId != null) {
            val cat = categories.find { it.id == currentId }
            if (cat != null) {
                path.add(0, cat)
                currentId = cat.parentId
            } else {
                currentId = null
            }
        }
        path
    }

    Column(modifier = Modifier.padding(bottom = 10.dp)) {
        Text(stringResource(R.string.category), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink, modifier = Modifier.padding(bottom = 8.dp))

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(kianColors.panel)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_categories_desc),
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    lineHeight = 20.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        com.ely.kian.ui.components.KianChip(
                            text = stringResource(R.string.no_category),
                            selected = selectedPath.isEmpty(),
                            onClick = { onChange(emptyList()) }
                        )
                    }
                    items(selectedPath) { category ->
                        val index = selectedPath.indexOf(category)
                        com.ely.kian.ui.components.KianChip(
                            text = category.name,
                            selected = true,
                            onClick = { onChange(selectedPath.take(index + 1).map { it.id }) }
                        )
                    }
                }
                
                val parentId = selectedPath.lastOrNull()?.id
                val options = categories.filter { it.parentId == parentId }.sortedBy { it.name }
                if (options.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options) { category ->
                            com.ely.kian.ui.components.KianChip(
                                text = category.name,
                                selected = false,
                                onClick = { onChange((selectedPath + category).map { it.id }) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PendingRow(item: PendingItem, formatAssetRef: (String) -> String) {
    val kianColors = KianTheme.colors
    
    val (containerColor, borderColor, textColor, metaColor, labelId, detailId) = when (item.status) {
        "fulfilled" -> HexColorPairIds(kianColors.successSoft, kianColors.success, kianColors.ink, kianColors.success, R.string.completed, R.string.completed_detail)
        "rejected" -> HexColorPairIds(kianColors.danger.copy(alpha = 0.1f), kianColors.danger, kianColors.ink, kianColors.danger, R.string.failed, R.string.rejected_detail)
        "offline" -> HexColorPairIds(kianColors.panel, kianColors.muted, kianColors.ink, kianColors.muted, R.string.queued_offline, R.string.queued_offline_desc)
        else -> HexColorPairIds(kianColors.warningSoft, kianColors.warning, kianColors.ink, kianColors.warning, R.string.pending, R.string.waiting_issuer_desc)
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
                    text = stringResource(labelId).uppercase(), 
                    fontSize = 10.sp, 
                    fontWeight = FontWeight.Black, 
                    color = metaColor,
                    letterSpacing = 1.sp
                )
            }
            
            Text(
                text = stringResource(R.string.pending_row_detail, item.amount, stringResource(detailId)),
                fontSize = 14.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 4.dp)
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = borderColor.copy(alpha = 0.1f))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = kianColors.muted, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.to_recipient, formatAssetRef(item.recipient)), 
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

private data class HexColorPairIds(
    val container: Color,
    val border: Color,
    val text: Color,
    val meta: Color,
    val labelId: Int,
    val detailId: Int
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
