package com.ely.kian.ui.screens.pending

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import com.ely.kian.ui.components.ButtonType
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.theme.KianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingEventsScreen(
    viewModel: PendingEventsViewModel,
    onBack: () -> Unit
) {
    val pendingEvents by viewModel.pendingEvents.collectAsState()
    val kianColors = KianTheme.colors
    val clipboardManager = LocalClipboardManager.current
    var manualInput by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pending_events)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
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
            // Manual Input Section
            Card(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = kianColors.panel)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.manual_event_import), style = MaterialTheme.typography.titleMedium, color = kianColors.ink)
                    Spacer(modifier = Modifier.height(8.dp))
                    KianInput(
                        value = manualInput,
                        onValueChange = { manualInput = it },
                        placeholder = stringResource(R.string.paste_event_json),
                        singleLine = false,
                        modifier = Modifier.heightIn(max = 120.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
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

            Divider(color = kianColors.line, modifier = Modifier.padding(vertical = 8.dp))

            // Pending Events List
            Text(
                stringResource(R.string.queue_for_sending),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                color = kianColors.ink
            )

            if (pendingEvents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                    Text(stringResource(R.string.no_pending_events), color = kianColors.muted)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                    items(pendingEvents) { item ->
                        PendingEventRow(item, onCopy = {
                            clipboardManager.setText(AnnotatedString(item.rawJson))
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun PendingEventRow(item: PendingEventItem, onCopy: () -> Unit) {
    val kianColors = KianTheme.colors
    Card(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = kianColors.panel),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(item.category, style = MaterialTheme.typography.titleSmall, color = kianColors.accent)
                Text(
                    item.relayUrl.removePrefix("ws://").removePrefix("wss://").take(20), 
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
            Spacer(modifier = Modifier.height(4.dp))
            Text("Kind: ${item.kind} | ID: ${item.id.take(8)}", style = MaterialTheme.typography.bodySmall, color = kianColors.muted)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                KianButton(
                    text = stringResource(R.string.copy_json),
                    onClick = onCopy,
                    type = ButtonType.Soft
                )
            }
        }
    }
}
