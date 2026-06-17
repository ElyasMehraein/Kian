package com.ely.kian.ui.screens.vouchers.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.theme.KianColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoucherEditBottomSheet(
    token: BalanceItem,
    allCategories: List<VoucherCategory>,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>) -> Unit,
    colors: KianColors
) {
    var selectedCategoryIds by remember { mutableStateOf(token.categories) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.canvas,
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
                        text = stringResource(R.string.edit_categories),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    Text(
                        text = stringResource(R.string.edit_token_desc),
                        fontSize = 14.sp,
                        color = colors.muted
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.background(colors.panel, CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close), modifier = Modifier.size(20.dp), tint = colors.ink)
                }
            }

            VoucherCategoryPicker(
                categories = allCategories,
                selectedIds = selectedCategoryIds,
                onChange = { selectedCategoryIds = it },
                colors = colors
            )

            Spacer(modifier = Modifier.height(16.dp))

            Surface(
                color = colors.panel,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = token.name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.ink
                    )
                    if (token.description.isNotEmpty()) {
                        Text(
                            text = token.description,
                            fontSize = 14.sp,
                            color = colors.muted,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            KianButton(
                text = stringResource(R.string.save_changes),
                onClick = { onSave(token.name, token.description, selectedCategoryIds) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun VoucherCategoryPicker(
    categories: List<VoucherCategory>,
    selectedIds: List<String>,
    onChange: (List<String>) -> Unit,
    colors: KianColors
) {
    val selectedPath = remember(categories, selectedIds) {
        val path = mutableListOf<VoucherCategory>()
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
        Text(stringResource(R.string.category), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = colors.ink, modifier = Modifier.padding(bottom = 8.dp))

        if (categories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.panel)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.create_categories_desc),
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
                            onClick = { onChange(emptyList()) }
                        )
                    }
                    items(selectedPath) { category ->
                        val index = selectedPath.indexOf(category)
                        KianChip(
                            text = category.name,
                            selected = true,
                            onClick = { onChange(selectedPath.take(index + 1).map { it.id }) }
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
                                onClick = { onChange((selectedPath + category).map { it.id }) }
                            )
                        }
                    }
                }
            }
        }
    }
}
