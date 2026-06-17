package com.ely.kian.ui.screens.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.KianApp
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.screens.vouchers.components.*
import com.ely.kian.ui.theme.KianTheme

@OptIn(ExperimentalMaterial3Api::class)
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
    val myCategories by viewModel.myCategories.collectAsState()
    val searchQuery = viewModel.searchQuery
    val selectedCat = viewModel.selectedCategoryId

    var isGridView by remember { mutableStateOf(false) }
    var editingToken by remember { mutableStateOf<BalanceItem?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMintDialog by remember { mutableStateOf(false) }

    val filteredBalances = remember(balances, searchQuery, selectedCat) {
        balances.filter { item ->
            val matchesQuery = item.name.contains(searchQuery, ignoreCase = true) || 
                             item.description.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCat == null || item.categories.contains(selectedCat)
            matchesQuery && matchesCat
        }
    }

    val kianColors = KianTheme.colors

    Scaffold(
        containerColor = kianColors.canvas,
        topBar = {
            Column(modifier = Modifier.background(kianColors.canvas)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.vouchers),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = kianColors.ink,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                // Search & View Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    TextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearch(it) },
                        placeholder = { Text(stringResource(R.string.scan), color = kianColors.muted) },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = kianColors.muted) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = kianColors.panel,
                            unfocusedContainerColor = kianColors.panel,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = kianColors.accent
                        ),
                        singleLine = true
                    )
                    
                    Surface(
                        onClick = { isGridView = !isGridView },
                        color = kianColors.panel,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(52.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isGridView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = null,
                                tint = kianColors.ink
                            )
                        }
                    }
                }

                // Quick Categories
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Surface(
                            onClick = onNavigateToCategories,
                            shape = CircleShape,
                            color = kianColors.panel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, kianColors.line)
                        ) {
                            Icon(
                                Icons.Default.Category,
                                contentDescription = null,
                                tint = kianColors.muted,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .size(20.dp)
                            )
                        }
                    }

                    item {
                        FilterChip(
                            label = stringResource(R.string.all),
                            selected = selectedCat == null,
                            onClick = { viewModel.selectCategory(null) },
                            colors = kianColors
                        )
                    }

                    items(myCategories.filter { it.parentId == null }) { cat ->
                        FilterChip(
                            label = cat.name,
                            selected = selectedCat == cat.id,
                            onClick = { viewModel.selectCategory(cat.id) },
                            colors = kianColors
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showMintDialog = true },
                containerColor = kianColors.accent,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Mint Havaleh")
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            if (filteredBalances.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    EmptyState(stringResource(R.string.no_assets_found), kianColors)
                }
            } else {
                if (isGridView) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filteredBalances) { balanceItem ->
                            VoucherGridItem(
                                item = balanceItem,
                                onEdit = { 
                                    editingToken = it
                                    showEditSheet = true
                                },
                                onToggleShowcase = { viewModel.toggleAssetShowcase(balanceItem.assetRef, it) },
                                colors = kianColors
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(filteredBalances) { balanceItem ->
                            BalanceRow(
                                item = balanceItem,
                                myCategories = myCategories,
                                onProducerClick = onProducerClick,
                                formatAssetRef = viewModel::formatAssetRef,
                                onEdit = { 
                                    editingToken = it
                                    showEditSheet = true
                                },
                                onToggleShowcase = { viewModel.toggleAssetShowcase(balanceItem.assetRef, it) },
                                colors = kianColors
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }
            }
        }
    }

    if (showEditSheet && editingToken != null) {
        VoucherEditBottomSheet(
            token = editingToken!!,
            allCategories = myCategories,
            onDismiss = { showEditSheet = false },
            onSave = { _, _, selectedCats ->
                viewModel.updateVoucherCategories(editingToken!!.assetRef, selectedCats)
                showEditSheet = false
            },
            colors = kianColors
        )
    }

    if (showMintDialog) {
        MintVoucherDialog(
            onDismiss = { showMintDialog = false },
            onConfirm = { name, desc, images, qty ->
                viewModel.mintToken(name, desc, images, qty)
                showMintDialog = false
            },
            colors = kianColors
        )
    }
}

@Composable
fun MintVoucherDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Long) -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var images by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }

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
                
                OutlinedTextField(
                    value = qty,
                    onValueChange = { if (it.all { char -> char.isDigit() }) qty = it },
                    label = { Text(stringResource(R.string.set_quantity)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                onClick = { onConfirm(name, desc, images, qty.toLongOrNull() ?: 1L) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = Color.White),
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
