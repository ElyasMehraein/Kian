package com.ely.kian.ui.screens.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.KianTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherCategoriesScreen(
    viewModel: VoucherViewModel,
    onNavigateBack: () -> Unit
) {
    val kianColors = KianTheme.colors
    val categories by viewModel.myCategories.collectAsState()
    var selectedPath by remember { mutableStateOf<List<VoucherCategory>>(emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newCategoryName by remember { mutableStateOf("") }

    val currentParent = selectedPath.lastOrNull()
    val displayCategories = remember(categories, selectedPath) {
        categories.filter { it.parentId == currentParent?.id }.sortedBy { it.name }
    }

    Scaffold(
        containerColor = kianColors.canvas,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.category_mgmt), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (selectedPath.isNotEmpty()) {
                            selectedPath = selectedPath.dropLast(1)
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kianColors.ink)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kianColors.canvas)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (selectedPath.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .background(kianColors.panel, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.all),
                        modifier = Modifier.clickable { selectedPath = emptyList() },
                        color = kianColors.accent,
                        fontSize = 13.sp
                    )
                    selectedPath.forEach { cat ->
                        Text(" > ", color = kianColors.muted, fontSize = 13.sp)
                        Text(
                            text = cat.name,
                            modifier = Modifier.clickable { 
                                val index = selectedPath.indexOf(cat)
                                selectedPath = selectedPath.take(index + 1)
                            },
                            color = kianColors.accent,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (displayCategories.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.no_categories_yet), color = kianColors.muted)
                        }
                    }
                }

                items(displayCategories) { category ->
                    VoucherCategoryItem(
                        category = category,
                        childCount = categories.count { it.parentId == category.id },
                        onSelect = { selectedPath = selectedPath + it },
                        onDelete = { viewModel.deleteCategory(it) },
                        colors = kianColors
                    )
                }
            }

            KianButton(
                text = if (currentParent == null) stringResource(R.string.add_root_category) 
                       else stringResource(R.string.add),
                onClick = { showAddDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            )
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = kianColors.panel,
            title = { Text(stringResource(R.string.add_root_category)) },
            text = {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = { newCategoryName = it },
                    placeholder = { Text(stringResource(R.string.new_root_cat_placeholder)) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newCategoryName.isNotBlank()) {
                            viewModel.saveCategory(newCategoryName, currentParent)
                            newCategoryName = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.save), color = kianColors.accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text(stringResource(R.string.cancel), color = kianColors.muted)
                }
            }
        )
    }
}

@Composable
fun VoucherCategoryItem(
    category: VoucherCategory,
    childCount: Int,
    onSelect: (VoucherCategory) -> Unit,
    onDelete: (VoucherCategory) -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    Surface(
        onClick = { onSelect(category) },
        color = colors.panel,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, fontWeight = FontWeight.Bold, color = colors.ink, fontSize = 16.sp)
                Text(
                    stringResource(R.string.subcategories_count, childCount),
                    fontSize = 12.sp,
                    color = colors.muted
                )
            }
            
            IconButton(onClick = { onDelete(category) }) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red.copy(alpha = 0.6f))
            }
            
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = colors.muted)
        }
    }
}
