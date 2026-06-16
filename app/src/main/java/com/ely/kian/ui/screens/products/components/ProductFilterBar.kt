package com.ely.kian.ui.screens.products.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ely.kian.R
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.theme.KianColors

@Composable
fun CategoryFilterBar(
    categories: List<ProductCategory>,
    selectedPath: List<ProductCategory>,
    onPathChange: (List<ProductCategory>) -> Unit,
    colors: KianColors
) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 12.dp)
        ) {
            item {
                KianChip(
                    text = stringResource(R.string.all),
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
                    backgroundColor = colors.accentSoft,
                    contentColor = colors.accent
                )
            }
        }
        
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
                        backgroundColor = colors.line,
                        contentColor = colors.ink
                    )
                }
            }
        }
    }
}

fun getBranchIds(categories: List<ProductCategory>, rootId: String): List<String> {
    val ids = mutableListOf(rootId)
    val children = categories.filter { it.parentId == rootId }
    for (child in children) {
        ids.addAll(getBranchIds(categories, child.id))
    }
    return ids
}
