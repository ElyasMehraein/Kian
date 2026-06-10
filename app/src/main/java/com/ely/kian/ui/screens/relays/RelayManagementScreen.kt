package com.ely.kian.ui.screens.relays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.data.local.entities.Relay
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.theme.KianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelayManagementScreen() {
    val kianColors = KianTheme.colors
    
    // Famous test relay and some others as defaults
    var relays by remember { mutableStateOf(listOf(
        Relay("wss://relay.damus.io", true, true),
        Relay("wss://nos.lol", true, true),
        Relay("wss://relay.snort.social", true, true),
        Relay("wss://eden.nostr.land", true, true)
    )) }
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var newRelayUrl by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        containerColor = kianColors.canvas,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = kianColors.ink,
                contentColor = kianColors.canvas,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Relay")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Header
            Text(
                text = "Relay Management",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = kianColors.ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )

            // Overall Status Section
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .fillMaxWidth(),
                color = kianColors.panel,
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF4CAF50), RoundedCornerShape(5.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Connected",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = kianColors.ink
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    KianButton(
                        text = "Reconnect All",
                        onClick = { /* Simulated Reconnect */ },
                        modifier = Modifier.wrapContentWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "Active Relays",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = kianColors.ink,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            )

            // Relay List
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(relays) { relay ->
                    RelayItem(relay, onDelete = {
                        relays = relays.filter { it.url != relay.url }
                    })
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = kianColors.canvas,
                contentColor = kianColors.ink,
                dragHandle = { BottomSheetDefaults.DragHandle(color = kianColors.line) }
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .padding(bottom = 32.dp)
                        .fillMaxWidth()
                ) {
                    Text(
                        text = "Add New Relay",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    KianInput(
                        value = newRelayUrl,
                        onValueChange = { newRelayUrl = it },
                        placeholder = "wss://relay.example.com"
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    KianButton(
                        text = "Add Relay",
                        onClick = {
                            if (newRelayUrl.isNotBlank()) {
                                relays = relays + Relay(newRelayUrl, true, true)
                                newRelayUrl = ""
                                showBottomSheet = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun RelayItem(relay: Relay, onDelete: () -> Unit) {
    val kianColors = KianTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = relay.url,
                fontWeight = FontWeight.Bold,
                color = kianColors.ink,
                fontSize = 15.sp
            )
            Text(
                text = "Online",
                fontSize = 13.sp,
                color = kianColors.accent,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        
        IconButton(
            onClick = onDelete,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = kianColors.danger.copy(alpha = 0.8f)
            )
        ) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Remove"
            )
        }
    }
}
