package com.ely.kian.ui.screens.pending

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.ui.components.ButtonType
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.components.util.setText
import com.ely.kian.ui.theme.KianTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingEventsScreen(
    viewModel: PendingEventsViewModel,
    onBack: () -> Unit
) {
    val filteredEvents by viewModel.filteredEvents.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val categoryCounts by viewModel.categoryCounts.collectAsState()
    
    val kianColors = KianTheme.colors
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var manualInput by remember { mutableStateOf("") }
    var isImportExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.pending_events),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kianColors.canvas,
                    titleContentColor = kianColors.ink,
                    navigationIconContentColor = kianColors.ink
                )
            )
        },
        containerColor = kianColors.canvas
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            
            // Category Filter Chips Section
            Text(
                text = stringResource(R.string.filter_events),
                style = MaterialTheme.typography.labelLarge,
                color = kianColors.muted,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                items(PendingCategoryType.entries) { category ->
                    val isSelected = selectedCategory == category
                    val count = categoryCounts[category] ?: 0
                    val label = stringResource(category.labelResId)

                    Surface(
                        onClick = { viewModel.selectCategory(category) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) kianColors.accent else kianColors.panel,
                        contentColor = if (isSelected) Color.White else kianColors.ink,
                        tonalElevation = if (isSelected) 4.dp else 0.dp,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = category.icon, fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (isSelected) Color.White.copy(alpha = 0.25f) else kianColors.line)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = count.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) Color.White else kianColors.ink,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Collapsible Manual Input Section
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = kianColors.panel),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isImportExpanded = !isImportExpanded },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.manual_event_import),
                            style = MaterialTheme.typography.titleSmall,
                            color = kianColors.ink
                        )
                        Icon(
                            imageVector = if (isImportExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = kianColors.muted
                        )
                    }

                    AnimatedVisibility(visible = isImportExpanded) {
                        Column(modifier = Modifier.padding(top = 10.dp)) {
                            KianInput(
                                value = manualInput,
                                onValueChange = { manualInput = it },
                                placeholder = stringResource(R.string.paste_event_json),
                                singleLine = false,
                                modifier = Modifier.heightIn(max = 100.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            KianButton(
                                text = stringResource(R.string.process_event),
                                onClick = {
                                    viewModel.processManualEvent(manualInput)
                                    manualInput = ""
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = manualInput.isNotBlank()
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = kianColors.line, modifier = Modifier.padding(vertical = 8.dp))

            // Pending Events List
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.queue_for_sending),
                    style = MaterialTheme.typography.titleSmall,
                    color = kianColors.ink,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "${filteredEvents.size} ${stringResource(R.string.pending_events)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = kianColors.muted
                )
            }

            if (filteredEvents.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(selectedCategory.icon, fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.no_pending_events),
                            style = MaterialTheme.typography.bodyMedium,
                            color = kianColors.muted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(filteredEvents, key = { "${it.id}_${it.relayUrl}" }) { item ->
                        PendingEventRow(
                            item = item,
                            onCopy = { scope.launch { clipboard.setText(item.rawJson) } },
                            onProcess = { viewModel.processManualEvent(item.rawJson) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PendingEventRow(
    item: PendingEventItem,
    onCopy: () -> Unit,
    onProcess: () -> Unit
) {
    val kianColors = KianTheme.colors

    val badgeColor = when (item.categoryType) {
        PendingCategoryType.TRANSFERS -> Color(0xFF10B981) // Emerald Green
        PendingCategoryType.REQUESTS -> Color(0xFF0EA5E9)  // Sky Blue
        PendingCategoryType.PROFILE -> Color(0xFF8B5CF6)   // Purple
        PendingCategoryType.OTHER -> kianColors.muted
        PendingCategoryType.ALL -> kianColors.accent
    }

    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kianColors.panel),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${item.categoryType.icon} ${item.category}",
                        style = MaterialTheme.typography.titleSmall,
                        color = kianColors.ink,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = item.relayUrl.removePrefix("ws://").removePrefix("wss://").take(22),
                    style = MaterialTheme.typography.bodySmall,
                    color = kianColors.muted
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (item.content.isBlank()) stringResource(R.string.empty_content) else item.content,
                style = MaterialTheme.typography.bodyMedium,
                color = kianColors.ink,
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Kind: ${item.kind} • ID: ${item.id.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = kianColors.muted
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                KianButton(
                    text = stringResource(R.string.process_event),
                    onClick = onProcess,
                    type = ButtonType.Soft,
                    modifier = Modifier.padding(end = 8.dp)
                )
                KianButton(
                    text = stringResource(R.string.copy_json),
                    onClick = onCopy,
                    type = ButtonType.Soft
                )
            }
        }
    }
}
