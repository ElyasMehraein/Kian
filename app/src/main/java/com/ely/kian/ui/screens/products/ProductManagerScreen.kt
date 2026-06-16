package com.ely.kian.ui.screens.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ely.kian.R
import com.ely.kian.data.local.entities.Product
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.ScreenHeader
import com.ely.kian.ui.screens.products.components.*
import com.ely.kian.ui.theme.KianTheme
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagerScreen(
    viewModel: ProductViewModel,
    onNavigateToCategories: () -> Unit
) {
    val kianColors = KianTheme.colors
    val products by viewModel.products.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val pubkey = viewModel.pubkey

    var showBottomSheet by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var imageUrls by remember { mutableStateOf("") }
    var selectedCategoryIds by remember { mutableStateOf<List<String>>(emptyList()) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(editingProduct) {
        if (editingProduct != null) {
            name = editingProduct!!.name
            description = editingProduct!!.description ?: ""
            imageUrls = try {
                Json.decodeFromString<List<String>>(editingProduct!!.images).joinToString("\n")
            } catch (e: Exception) { "" }
            selectedCategoryIds = try {
                Json.decodeFromString<List<String>>(editingProduct!!.categories)
            } catch (e: Exception) { emptyList() }
        } else {
            name = ""
            description = ""
            imageUrls = ""
            selectedCategoryIds = emptyList()
        }
    }

    Scaffold(
        containerColor = kianColors.canvas,
        contentWindowInsets = WindowInsets(0.dp),
        floatingActionButton = {
            if (pubkey != null) {
                FloatingActionButton(
                    onClick = { 
                        editingProduct = null
                        showBottomSheet = true 
                    },
                    containerColor = kianColors.ink,
                    contentColor = kianColors.canvas,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_product))
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ScreenHeader(
                title = stringResource(R.string.manage_products),
                subtitle = if (pubkey != null) stringResource(R.string.manage_products_desc) else stringResource(R.string.create_keys_first)
            )

            CategoryFilterBar(
                categories = categories,
                selectedPath = viewModel.selectedFilterPath,
                onPathChange = { viewModel.selectedFilterPath = it },
                colors = kianColors
            )

            val filteredProducts = remember(products, viewModel.selectedFilterPath, categories) {
                val selectedLeaf = viewModel.selectedFilterPath.lastOrNull()
                if (selectedLeaf == null) {
                    products
                } else {
                    val allowedIds = getBranchIds(categories, selectedLeaf.id)
                    products.filter { product ->
                        val pCats = try { Json.decodeFromString<List<String>>(product.categories) } catch (e: Exception) { emptyList() }
                        pCats.any { it in allowedIds }
                    }
                }
            }

            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (filteredProducts.isEmpty()) {
                    item {
                        Text(
                            text = if (products.isEmpty()) stringResource(R.string.no_products_yet) else stringResource(R.string.no_products_category),
                            fontSize = 15.sp,
                            color = kianColors.muted
                        )
                    }
                }
                items(filteredProducts) { product ->
                    ProductRow(
                        product = product,
                        categoryName = remember(product, categories) {
                            val pCats = try { Json.decodeFromString<List<String>>(product.categories) } catch (e: Exception) { emptyList() }
                            val lastCatId = pCats.lastOrNull()
                            categories.find { it.id == lastCatId }?.name
                        },
                        onEdit = {
                            editingProduct = it
                            showBottomSheet = true
                        },
                        onDelete = { viewModel.deleteProduct(it) },
                        onToggleShowcase = { product, isShowcase -> viewModel.toggleShowcase(product, isShowcase) },
                        colors = kianColors
                    )
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
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
                                text = if (editingProduct != null) stringResource(R.string.edit_product) else stringResource(R.string.create_product),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = kianColors.ink
                            )
                            Text(
                                text = if (editingProduct != null) stringResource(R.string.update_details) else stringResource(R.string.add_new_product),
                                fontSize = 14.sp,
                                color = kianColors.muted
                            )
                        }
                        IconButton(
                            onClick = { showBottomSheet = false },
                            modifier = Modifier.background(kianColors.panel, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp), tint = kianColors.ink)
                        }
                    }

                    DraftCategoryPicker(
                        categories = categories,
                        selectedIds = selectedCategoryIds,
                        onChange = { selectedCategoryIds = it },
                        onManageCategories = onNavigateToCategories,
                        colors = kianColors
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text(stringResource(R.string.product_name), color = kianColors.muted) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(color = kianColors.ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = kianColors.line,
                            focusedBorderColor = kianColors.accent,
                            unfocusedContainerColor = kianColors.panel,
                            focusedContainerColor = kianColors.panel
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        placeholder = { Text(stringResource(R.string.description), color = kianColors.muted) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(color = kianColors.ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = kianColors.line,
                            focusedBorderColor = kianColors.accent,
                            unfocusedContainerColor = kianColors.panel,
                            focusedContainerColor = kianColors.panel
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = imageUrls,
                        onValueChange = { imageUrls = it },
                        placeholder = { Text(stringResource(R.string.hosted_images_desc), color = kianColors.muted) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 88.dp),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(color = kianColors.ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = kianColors.line,
                            focusedBorderColor = kianColors.accent,
                            unfocusedContainerColor = kianColors.panel,
                            focusedContainerColor = kianColors.panel
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KianButton(
                            text = stringResource(R.string.category_management),
                            onClick = onNavigateToCategories,
                            modifier = Modifier.weight(1f),
                            backgroundColor = kianColors.accentSoft,
                            contentColor = kianColors.accent
                        )
                        KianButton(
                            text = if (editingProduct != null) stringResource(R.string.update_product) else stringResource(R.string.create_product),
                            onClick = {
                                viewModel.saveProduct(editingProduct?.id, name, description, imageUrls, selectedCategoryIds)
                                showBottomSheet = false
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
