package com.ely.kian.ui.screens.products.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.theme.KianColors

@Composable
fun DraftCategoryPicker(
    categories: List<ProductCategory>,
    selectedIds: List<String>,
    onChange: (List<String>) -> Unit,
    onManageCategories: () -> Unit,
    colors: KianColors
) {
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
            Text(stringResource(R.string.category), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                stringResource(R.string.manage_categories),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.accentSoft)
                    .clickable { onManageCategories() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.panel)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_categories_first),
                    fontSize = 14.sp,
                    color = colors.muted,
                    lineHeight = 20.sp
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        KianChip(
                            text = stringResource(R.string.no_category),
                            selected = selectedPath.isEmpty(),
                            onClick = { onChange(emptyList()) },
                            backgroundColor = if (selectedPath.isEmpty()) colors.ink else colors.panel,
                            contentColor = if (selectedPath.isEmpty()) colors.canvas else colors.ink
                        )
                    }
                    items(selectedPath) { category ->
                        val index = selectedPath.indexOf(category)
                        KianChip(
                            text = category.name,
                            selected = true,
                            onClick = { onChange(selectedPath.take(index + 1).map { it.id }) },
                            backgroundColor = colors.accentSoft,
                            contentColor = colors.accent
                        )
                    }
                }
                
                val parentId = selectedPath.lastOrNull()?.id
                val options = categories.filter { it.parentId == parentId }.sortedBy { it.name }
                if (options.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(options) { category ->
                            KianChip(
                                text = category.name,
                                selected = false,
                                onClick = { onChange((selectedPath + category).map { it.id }) },
                                backgroundColor = colors.panel,
                                contentColor = colors.ink
                            )
                        }
                    }
                }
            }
        }
    }
}
