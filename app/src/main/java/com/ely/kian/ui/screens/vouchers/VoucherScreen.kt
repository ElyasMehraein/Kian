package com.ely.kian.ui.screens.vouchers

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ely.kian.R
import com.ely.kian.KianApp
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.screens.vouchers.components.*
import com.ely.kian.ui.theme.KianTheme

@Composable
fun VoucherScreen(
    onSendToken: () -> Unit, 
    onProducerClick: (String) -> Unit,
    onNavigateToCategories: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: VoucherViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = VoucherViewModel.provideFactory(
            app.container.voucherRepository,
            app.container.keyDao
        )
    )

    val balances by viewModel.balances.collectAsState()
    val pending by viewModel.pending.collectAsState()
    val myCategories by viewModel.myCategories.collectAsState()
    val activityFilter = viewModel.activityFilter

    var editingToken by remember { mutableStateOf<BalanceItem?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMintDialog by remember { mutableStateOf(false) }

    val filteredPending = remember(pending, activityFilter) {
        if (activityFilter == "all") pending
        else pending.filter { it.status == activityFilter }
    }

    val kianColors = KianTheme.colors

    Scaffold(
        containerColor = kianColors.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showMintDialog = true },
                containerColor = kianColors.accent,
                contentColor = kianColors.ink
            ) {
                Icon(Icons.Default.Add, contentDescription = "Mint Havaleh")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                VoucherHeader(
                    balancesCount = balances.size, 
                    colors = kianColors,
                    onNavigateToCategories = onNavigateToCategories
                )
            }

            item {
                SectionHeader(stringResource(R.string.token_balances), Icons.Default.ConfirmationNumber, kianColors)
            }

            if (balances.isEmpty()) {
                item { EmptyState(stringResource(R.string.no_assets_found), kianColors) }
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
                        },
                        colors = kianColors
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(24.dp))
                SectionHeader(stringResource(R.string.transfer_activity), Icons.Default.History, kianColors)
                
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
                item { EmptyState(stringResource(R.string.no_activity_filter), kianColors) }
            } else {
                items(filteredPending) { item ->
                    PendingRow(item, formatAssetRef = viewModel::formatAssetRef, colors = kianColors)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
            
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }

    if (showEditSheet && editingToken != null) {
        VoucherEditBottomSheet(
            token = editingToken!!,
            allCategories = myCategories,
            onDismiss = { showEditSheet = false },
            onSave = { _, _, selectedCats ->
                viewModel.updateTokenDetails(editingToken!!.assetRef, editingToken!!.name, editingToken!!.description, selectedCats)
                showEditSheet = false
            },
            colors = kianColors
        )
    }

    if (showMintDialog) {
        MintVoucherDialog(
            onDismiss = { showMintDialog = false },
            onConfirm = { name, desc, images, qty, unit ->
                viewModel.mintToken(name, desc, images, qty, unit)
                showMintDialog = false
            },
            colors = kianColors
        )
    }
}

@Composable
fun MintVoucherDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long, String) -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var images by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("واحد") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.panel,
        titleContentColor = colors.ink,
        textContentColor = colors.ink,
        title = { Text(stringResource(R.string.add_product)) }, 
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.product_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.ink.copy(alpha = 0.3f),
                        focusedLabelColor = colors.accent,
                        unfocusedLabelColor = colors.ink.copy(alpha = 0.5f),
                        cursorColor = colors.accent
                    )
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text(stringResource(R.string.description)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.ink.copy(alpha = 0.3f),
                        focusedLabelColor = colors.accent,
                        unfocusedLabelColor = colors.ink.copy(alpha = 0.5f),
                        cursorColor = colors.accent
                    )
                )
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = qty,
                        onValueChange = { if (it.all { char -> char.isDigit() }) qty = it },
                        label = { Text(stringResource(R.string.set_quantity)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.ink.copy(alpha = 0.3f),
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.ink.copy(alpha = 0.5f),
                            cursorColor = colors.accent
                        )
                    )
                    OutlinedTextField(
                        value = unit,
                        onValueChange = { unit = it },
                        label = { Text("واحد") }, 
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accent,
                            unfocusedBorderColor = colors.ink.copy(alpha = 0.3f),
                            focusedLabelColor = colors.accent,
                            unfocusedLabelColor = colors.ink.copy(alpha = 0.5f),
                            cursorColor = colors.accent
                        )
                    )
                }

                OutlinedTextField(
                    value = images,
                    onValueChange = { images = it },
                    label = { Text(stringResource(R.string.hosted_images_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accent,
                        unfocusedBorderColor = colors.ink.copy(alpha = 0.3f),
                        focusedLabelColor = colors.accent,
                        unfocusedLabelColor = colors.ink.copy(alpha = 0.5f),
                        cursorColor = colors.accent
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, desc, images, qty.toLongOrNull() ?: 1L, unit) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.ink),
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = colors.ink)) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
