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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
    val myPubkey by viewModel.myPubkey.collectAsState()
    val searchQuery = viewModel.searchQuery
    val selectedCat = viewModel.selectedCategoryId

    var isGridView by remember { mutableStateOf(false) }
    var editingToken by remember { mutableStateOf<BalanceItem?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showMintDialog by remember { mutableStateOf(false) }
    var alertDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is VoucherViewModel.UiEvent.Alert -> {
                    alertDialogInfo = event.title to event.message
                }
                is VoucherViewModel.UiEvent.AlertRes -> {
                    alertDialogInfo = context.getString(event.title) to context.getString(event.message)
                }
            }
        }
    }

    val filteredBalances = remember(balances, searchQuery, selectedCat) {
        balances.filter { item ->
            val matchesQuery = item.name.contains(searchQuery, ignoreCase = true) || 
                             item.description.contains(searchQuery, ignoreCase = true)
            val matchesCat = selectedCat == null || item.categories.contains(selectedCat)
            matchesQuery && matchesCat
        }
    }

    val breadcrumbPath = remember(selectedCat, myCategories) {
        val path = mutableListOf<com.ely.kian.data.local.entities.VoucherCategory>()
        var current = myCategories.find { it.id == selectedCat }
        while (current != null) {
            path.add(0, current)
            current = myCategories.find { it.id == current.parentId }
        }
        path
    }

    val currentOptions = remember(selectedCat, myCategories) {
        myCategories.filter { it.parentId == selectedCat }.sortedBy { it.name }
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

                // Breadcrumb & Categories
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Level Path (Breadcrumb)
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            IconButton(
                                onClick = onNavigateToCategories,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(kianColors.panel, CircleShape)
                            ) {
                                Icon(
                                    Icons.Default.Category,
                                    contentDescription = "Manage Categories",
                                    tint = kianColors.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        item {
                            TextButton(
                                onClick = { viewModel.selectCategory(null) },
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = if (selectedCat == null) kianColors.accent else kianColors.muted
                                )
                            ) {
                                Text(stringResource(R.string.all), fontWeight = if (selectedCat == null) FontWeight.Bold else FontWeight.Normal)
                            }
                        }

                        items(breadcrumbPath) { cat ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = kianColors.line,
                                    modifier = Modifier.size(16.dp)
                                )
                                TextButton(
                                    onClick = { viewModel.selectCategory(cat.id) },
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = if (selectedCat == cat.id) kianColors.accent else kianColors.muted
                                    )
                                ) {
                                    Text(cat.name, fontWeight = if (selectedCat == cat.id) FontWeight.Bold else FontWeight.Normal)
                                }
                            }
                        }
                    }

                    // Children Options (The Next Level)
                    if (currentOptions.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(currentOptions) { cat ->
                                FilterChip(
                                    label = cat.name,
                                    selected = false,
                                    onClick = { viewModel.selectCategory(cat.id) },
                                    colors = kianColors
                                )
                            }
                        }
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
            isProducer = editingToken!!.producer == myPubkey,
            onDismiss = { showEditSheet = false },
            onSave = { _, _, selectedCats ->
                viewModel.updateVoucherCategories(editingToken!!.assetRef, selectedCats)
                showEditSheet = false
            },
            onBurn = {
                viewModel.burnToken(editingToken!!.assetRef)
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

    alertDialogInfo?.let { info ->
        AlertDialog(
            onDismissRequest = { alertDialogInfo = null },
            title = { Text(info.first) },
            text = { Text(info.second) },
            confirmButton = {
                TextButton(onClick = { alertDialogInfo = null }) {
                    Text(stringResource(R.string.confirm))
                }
            },
            containerColor = kianColors.panel,
            titleContentColor = kianColors.ink,
            textContentColor = kianColors.ink
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
