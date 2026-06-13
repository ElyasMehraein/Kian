package com.ely.kian.ui.screens.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.components.ScreenHeader
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
    
    // Draft states
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
                    Icon(Icons.Default.Add, contentDescription = "Add Product")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header
            ScreenHeader(
                title = "Manage products",
                subtitle = if (pubkey != null) "Tap the + button to create a product, edit one from the list, or filter by category." else "Create keys first."
            )

            // Category Filter Bar
            CategoryFilterBar(
                categories = categories,
                selectedPath = viewModel.selectedFilterPath,
                onPathChange = { viewModel.selectedFilterPath = it }
            )

            // Product List
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
                            text = if (products.isEmpty()) "No products yet." else "No products found in this category.",
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
                        onDelete = { viewModel.deleteProduct(it) }
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
                                text = if (editingProduct != null) "Edit product" else "Create product",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = kianColors.ink
                            )
                            Text(
                                text = if (editingProduct != null) "Update the details for this product." else "Add a new product to your list.",
                                fontSize = 14.sp,
                                color = kianColors.muted
                            )
                        }
                        IconButton(
                            onClick = { showBottomSheet = false },
                            modifier = Modifier.background(kianColors.panel, CircleShape)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(20.dp), tint = kianColors.ink)
                        }
                    }

                    DraftCategoryPicker(
                        categories = categories,
                        selectedIds = selectedCategoryIds,
                        onChange = { selectedCategoryIds = it },
                        onManageCategories = onNavigateToCategories
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = { Text("Product name", color = kianColors.muted) },
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
                        placeholder = { Text("Description", color = kianColors.muted) },
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
                        placeholder = { Text("Hosted image URLs, one per line", color = kianColors.muted) },
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
                            text = "Category management",
                            onClick = onNavigateToCategories,
                            modifier = Modifier.weight(1f),
                            backgroundColor = kianColors.accentSoft,
                            contentColor = kianColors.accent
                        )
                        KianButton(
                            text = if (editingProduct != null) "Update product" else "Create product",
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

@Composable
fun ProductRow(
    product: Product,
    categoryName: String?,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit
) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(16.dp))
            .border(1.dp, kianColors.line, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.clickable { onEdit(product) }) {
            Text(text = product.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
            Text(
                text = product.description ?: "No description",
                fontSize = 14.sp,
                color = kianColors.muted,
                modifier = Modifier.padding(top = 6.dp)
            )
            if (categoryName != null) {
                Text(
                    text = categoryName.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kianColors.accent,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            Text(
                text = "Tap to edit",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.accent,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = { onDelete(product) },
            colors = ButtonDefaults.buttonColors(
                containerColor = kianColors.danger.copy(alpha = 0.1f),
                contentColor = kianColors.danger
            ),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.height(40.dp)
        ) {
            Text("Delete product", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CategoryFilterBar(
    categories: List<ProductCategory>,
    selectedPath: List<ProductCategory>,
    onPathChange: (List<ProductCategory>) -> Unit
) {
    val kianColors = KianTheme.colors
    
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        // Path View
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            item {
                KianChip(
                    text = "All",
                    selected = selectedPath.isEmpty(),
                    onClick = { onPathChange(emptyList()) }
                )
            }
            items(selectedPath) { category ->
                KianChip(
                    text = category.name,
                    selected = true,
                    onClick = { 
                        val index = selectedPath.indexOf(category)
                        onPathChange(selectedPath.take(index + 1))
                    },
                    backgroundColor = kianColors.accentSoft,
                    contentColor = kianColors.accent
                )
            }
        }
        
        // Next level options
        val parentId = selectedPath.lastOrNull()?.id
        val options = categories.filter { it.parentId == parentId }.sortedBy { it.name }
        
        if (options.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(options) { category ->
                    KianChip(
                        text = category.name,
                        selected = false,
                        onClick = { onPathChange(selectedPath + category) },
                        backgroundColor = kianColors.line,
                        contentColor = kianColors.ink
                    )
                }
            }
        }
    }
}

@Composable
fun DraftCategoryPicker(
    categories: List<ProductCategory>,
    selectedIds: List<String>,
    onChange: (List<String>) -> Unit,
    onManageCategories: () -> Unit
) {
    val kianColors = KianTheme.colors
    val selectedPath = remember(categories, selectedIds) {
        val path = mutableListOf<ProductCategory>()
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Category", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
            Text(
                "Manage categories",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = kianColors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(kianColors.accentSoft)
                    .clickable { onManageCategories() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(kianColors.panel)
                    .padding(12.dp)
            ) {
                Text(
                    "Create categories first, then assign a product into that tree.",
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    lineHeight = 20.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Current path row
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        KianChip(
                            text = "No category",
                            selected = selectedPath.isEmpty(),
                            onClick = { onChange(emptyList()) },
                            backgroundColor = if (selectedPath.isEmpty()) kianColors.ink else kianColors.panel,
                            contentColor = if (selectedPath.isEmpty()) kianColors.canvas else kianColors.ink
                        )
                    }
                    items(selectedPath) { category ->
                        val index = selectedPath.indexOf(category)
                        KianChip(
                            text = category.name,
                            selected = true,
                            onClick = { onChange(selectedPath.take(index + 1).map { it.id }) },
                            backgroundColor = kianColors.accentSoft,
                            contentColor = kianColors.accent
                        )
                    }
                }
                
                // Next level row
                val parentId = selectedPath.lastOrNull()?.id
                val options = categories.filter { it.parentId == parentId }.sortedBy { it.name }
                if (options.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options) { category ->
                            KianChip(
                                text = category.name,
                                selected = false,
                                onClick = { onChange((selectedPath + category).map { it.id }) },
                                backgroundColor = kianColors.panel,
                                contentColor = kianColors.ink
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getBranchIds(categories: List<ProductCategory>, rootId: String): List<String> {
    val ids = mutableListOf(rootId)
    val children = categories.filter { it.parentId == rootId }
    for (child in children) {
        ids.addAll(getBranchIds(categories, child.id))
    }
    return ids
}
