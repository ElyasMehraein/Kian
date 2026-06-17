package com.ely.kian.ui.screens.vouchers

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.R
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.ui.theme.KianTheme

private const val MAX_LEVEL = 5

@Composable
fun VoucherCategoriesScreen(
    viewModel: VoucherViewModel,
    onNavigateBack: () -> Unit
) {
    val kianColors = KianTheme.colors
    val categories by viewModel.myCategories.collectAsState()
    var rootName by remember { mutableStateOf("") }
    var draftParent by remember { mutableStateOf<VoucherCategory?>(null) }
    var childName by remember { mutableStateOf("") }
    var alertDialogInfo by remember { mutableStateOf<Pair<String, String>?>(null) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            if (event is VoucherViewModel.UiEvent.Alert) {
                alertDialogInfo = event.title to event.message
            }
        }
    }

    val roots = remember(categories) {
        categories.filter { it.parentId == null }.sortedBy { it.name }
    }

    Scaffold(
        containerColor = kianColors.canvas
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.category_management),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink
                    )
                    Text(
                        text = stringResource(R.string.category_mgmt_desc),
                        fontSize = 14.sp,
                        color = kianColors.muted,
                        lineHeight = 20.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Surface(
                    onClick = onNavigateBack,
                    shape = CircleShape,
                    color = kianColors.panel,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(22.dp), tint = kianColors.ink)
                    }
                }
            }

            // Add Root Section
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = kianColors.panel),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.add_root_category),
                        fontWeight = FontWeight.SemiBold,
                        color = kianColors.ink,
                        fontSize = 16.sp
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = rootName,
                            onValueChange = { rootName = it },
                            placeholder = { Text(stringResource(R.string.new_root_cat_placeholder), color = kianColors.muted) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = kianColors.accent,
                                unfocusedBorderColor = kianColors.line,
                                focusedContainerColor = kianColors.canvas,
                                unfocusedContainerColor = kianColors.canvas
                            ),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (rootName.isNotBlank()) {
                                    viewModel.saveCategory(rootName, null)
                                    rootName = ""
                                }
                            },
                            modifier = Modifier.height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = kianColors.ink,
                                contentColor = kianColors.canvas
                            )
                        ) {
                            Text(stringResource(R.string.add), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Draft Subcategory Section
            draftParent?.let { parent ->
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = kianColors.infoSoft),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.add_subcat_under, parent.name),
                            fontWeight = FontWeight.SemiBold,
                            color = kianColors.info,
                            fontSize = 14.sp
                        )
                        Row(
                            modifier = Modifier.padding(top = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = childName,
                                onValueChange = { childName = it },
                                placeholder = { Text(stringResource(R.string.subcat_name_placeholder), color = kianColors.muted) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = kianColors.info,
                                    unfocusedBorderColor = kianColors.line,
                                    focusedContainerColor = kianColors.canvas,
                                    unfocusedContainerColor = kianColors.canvas
                                ),
                                singleLine = true
                            )
                            Button(
                                onClick = {
                                    if (childName.isNotBlank()) {
                                        viewModel.saveCategory(childName, parent)
                                        childName = ""
                                        draftParent = null
                                    }
                                },
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = kianColors.ink,
                                    contentColor = kianColors.canvas
                                )
                            ) {
                                Text(stringResource(R.string.save), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // Categories Tree
            if (roots.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = kianColors.panel),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)) {
                        Text(
                            text = stringResource(R.string.no_categories_yet),
                            fontWeight = FontWeight.SemiBold,
                            color = kianColors.ink,
                            fontSize = 16.sp
                        )
                        Text(
                            text = stringResource(R.string.no_categories_desc),
                            fontSize = 14.sp,
                            color = kianColors.muted,
                            lineHeight = 24.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    roots.forEach { item ->
                        CategoryNode(
                            categories = categories,
                            item = item,
                            onAddChild = { draftParent = it },
                            onDelete = { viewModel.deleteCategory(it) },
                            colors = kianColors
                        )
                    }
                }
            }
        }
    }

    alertDialogInfo?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { alertDialogInfo = null },
            title = { Text(title, color = kianColors.ink) },
            text = { Text(message, color = kianColors.ink) },
            containerColor = kianColors.panel,
            confirmButton = {
                TextButton(onClick = { alertDialogInfo = null }) {
                    Text(stringResource(R.string.confirm), color = kianColors.accent)
                }
            }
        )
    }
}

@Composable
fun CategoryNode(
    categories: List<VoucherCategory>,
    item: VoucherCategory,
    onAddChild: (VoucherCategory) -> Unit,
    onDelete: (VoucherCategory) -> Unit,
    colors: com.ely.kian.ui.theme.KianColors
) {
    val children = remember(categories, item.id) {
        categories.filter { it.parentId == item.id }.sortedBy { it.name }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Node Header
        Surface(
            color = colors.panel,
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.infoSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.level.toString(),
                        fontWeight = FontWeight.Bold,
                        color = colors.info,
                        fontSize = 14.sp
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.ink
                    )
                    Text(
                        text = if (children.isNotEmpty()) stringResource(R.string.subcategories_count, children.size)
                               else stringResource(R.string.no_subcategories),
                        fontSize = 12.sp,
                        color = colors.muted
                    )
                }

                if (item.level < MAX_LEVEL) {
                    Surface(
                        onClick = { onAddChild(item) },
                        shape = CircleShape,
                        color = colors.accentSoft,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add, 
                                contentDescription = null, 
                                modifier = Modifier.size(20.dp), 
                                tint = colors.accent
                            )
                        }
                    }
                }

                Surface(
                    onClick = { onDelete(item) },
                    shape = CircleShape,
                    color = colors.danger.copy(alpha = 0.1f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(20.dp), tint = colors.danger)
                    }
                }
            }
        }

        // Children
        if (children.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(start = 22.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                children.forEachIndexed { index, child ->
                    val isLast = index == children.size - 1
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // Connector
                        Box(modifier = Modifier.width(14.dp).fillMaxHeight()) {
                            // Vertical Line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .then(if (isLast) Modifier.height(32.dp) else Modifier.fillMaxHeight())
                                    .background(colors.line)
                                    .align(Alignment.TopStart)
                            )
                            // Horizontal Line
                            Box(
                                modifier = Modifier
                                    .padding(top = 32.dp)
                                    .width(14.dp)
                                    .height(1.dp)
                                    .background(colors.line)
                            )
                            // Arrow Tip (Diamond shape)
                            Box(
                                modifier = Modifier
                                    .padding(top = 30.5.dp)
                                    .size(4.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp)
                                    .rotate(45f)
                                    .background(colors.line)
                            )
                        }

                        // Child Node
                        Box(modifier = Modifier.padding(bottom = 8.dp).weight(1f)) {
                            CategoryNode(
                                categories = categories,
                                item = child,
                                onAddChild = onAddChild,
                                onDelete = onDelete,
                                colors = colors
                            )
                        }
                    }
                }
            }
        }
    }
}
