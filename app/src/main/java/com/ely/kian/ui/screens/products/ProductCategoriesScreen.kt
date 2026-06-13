package com.ely.kian.ui.screens.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.ScreenHeader
import com.ely.kian.ui.theme.KianTheme

@Composable
fun ProductCategoriesScreen(
    viewModel: ProductViewModel,
    onNavigateBack: () -> Unit
) {
    val kianColors = KianTheme.colors
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    
    var rootName by remember { mutableStateOf("") }
    var draftParent by remember { mutableStateOf<ProductCategory?>(null) }
    var childName by remember { mutableStateOf("") }

    val roots = remember(categories) { categories.filter { it.parentId == null }.sortedBy { it.name } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kianColors.canvas)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenHeader(
            title = "Category management",
            subtitle = "Build your category tree up to 5 levels deep, then use it on the products page.",
            onBack = onNavigateBack
        )

        // Add root category
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(kianColors.panel, RoundedCornerShape(24.dp))
                .border(1.dp, kianColors.line, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Text("Add root category", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
            Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = rootName,
                    onValueChange = { rootName = it },
                    placeholder = { Text("New root category name", color = kianColors.muted) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = LocalTextStyle.current.copy(color = kianColors.ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = kianColors.line,
                        focusedBorderColor = kianColors.accent,
                        unfocusedContainerColor = kianColors.canvas,
                        focusedContainerColor = kianColors.canvas
                    )
                )
                KianButton(
                    text = "Add",
                    onClick = {
                        if (rootName.isNotBlank()) {
                            viewModel.saveCategory(rootName, null)
                            rootName = ""
                        }
                    },
                    modifier = Modifier.width(IntrinsicSize.Min)
                )
            }
        }

        if (draftParent != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(kianColors.accentSoft.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                    .border(1.dp, kianColors.accentSoft, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text("Add subcategory under ${draftParent?.name}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kianColors.accent)
                Row(modifier = Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = childName,
                        onValueChange = { childName = it },
                        placeholder = { Text("Subcategory name", color = kianColors.muted) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(16.dp),
                        textStyle = LocalTextStyle.current.copy(color = kianColors.ink),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = kianColors.line,
                            focusedBorderColor = kianColors.accent,
                            unfocusedContainerColor = kianColors.canvas,
                            focusedContainerColor = kianColors.canvas
                        )
                    )
                    KianButton(
                        text = "Save",
                        onClick = {
                            if (childName.isNotBlank()) {
                                viewModel.saveCategory(childName, draftParent)
                                childName = ""
                                draftParent = null
                            }
                        },
                        modifier = Modifier.width(IntrinsicSize.Min)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (roots.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(kianColors.panel, RoundedCornerShape(24.dp))
                    .border(1.dp, kianColors.line, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            ) {
                Text("No categories yet", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
                Text(
                    "Start by adding a root category, then grow the tree with subcategories.",
                    fontSize = 14.sp,
                    color = kianColors.muted,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                roots.forEach { root ->
                    CategoryNode(
                        categories = categories,
                        item = root,
                        onAddChild = { draftParent = it },
                        onDelete = { viewModel.deleteCategory(it) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun CategoryNode(
    categories: List<ProductCategory>,
    item: ProductCategory,
    onAddChild: (ProductCategory) -> Unit,
    onDelete: (ProductCategory) -> Unit
) {
    val kianColors = KianTheme.colors
    val children = remember(categories, item.id) { 
        categories.filter { it.parentId == item.id }.sortedBy { it.name }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(kianColors.panel, RoundedCornerShape(24.dp))
                .border(1.dp, kianColors.line, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(32.dp).background(kianColors.accentSoft, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.level.toString(), fontWeight = FontWeight.Bold, color = kianColors.accent)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
                    Text(
                        if (children.isNotEmpty()) "${children.size} subcategories" else "No subcategories yet",
                        fontSize = 12.sp,
                        color = kianColors.muted,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (item.level < 5) {
                    IconButton(
                        onClick = { onAddChild(item) },
                        modifier = Modifier.size(40.dp).background(kianColors.accentSoft.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add child", tint = kianColors.accent, modifier = Modifier.size(20.dp))
                    }
                }
                IconButton(
                    onClick = { onDelete(item) },
                    modifier = Modifier.size(40.dp).background(kianColors.danger.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = kianColors.danger, modifier = Modifier.size(20.dp))
                }
            }
        }
        
        if (children.isNotEmpty()) {
            Row(modifier = Modifier.padding(start = 20.dp).height(IntrinsicSize.Min)) {
                Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(kianColors.line))
                Spacer(modifier = Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                    children.forEach { child ->
                        CategoryNode(categories, child, onAddChild, onDelete)
                    }
                }
            }
        }
    }
}
