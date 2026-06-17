package com.ely.kian.ui.screens.vouchers.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ely.kian.KianApp
import com.ely.kian.R
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.theme.KianColors

@Composable
fun BalanceRow(
    item: BalanceItem,
    myCategories: List<VoucherCategory>,
    onProducerClick: (String) -> Unit,
    formatAssetRef: (String) -> String,
    onEdit: (BalanceItem) -> Unit,
    onToggleShowcase: (Boolean) -> Unit,
    colors: KianColors
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(16.dp),
        color = colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line.copy(alpha = 0.3f)),
        onClick = { /* Go to details */ }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Image or Icon
            Surface(
                modifier = Modifier.size(64.dp),
                shape = RoundedCornerShape(12.dp),
                color = colors.canvas
            ) {
                if (item.images.isNotEmpty()) {
                    AsyncImage(
                        model = item.images.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = colors.accent.copy(alpha = 0.3f))
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.description.ifEmpty { stringResource(R.string.no_description) },
                    fontSize = 12.sp,
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "x${item.amount}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.accent
                    )
                    
                    if (item.categories.isNotEmpty()) {
                        val catName = myCategories.find { it.id == item.categories.first() }?.name ?: ""
                        if (catName.isNotEmpty()) {
                            Text(
                                text = "#$catName",
                                fontSize = 11.sp,
                                color = colors.muted
                            )
                        }
                    }
                }
            }

            // Actions
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggleShowcase(!item.isShowcase) }) {
                    Icon(
                        if (item.isShowcase) Icons.Default.Storefront else Icons.Outlined.Storefront,
                        contentDescription = null,
                        tint = if (item.isShowcase) colors.accent else colors.muted,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(onClick = { onEdit(item) }) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = colors.muted, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
fun VoucherGridItem(
    item: BalanceItem,
    onEdit: (BalanceItem) -> Unit,
    onToggleShowcase: (Boolean) -> Unit,
    colors: KianColors
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.panel,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line.copy(alpha = 0.3f)),
        onClick = { /* Details */ }
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                if (item.images.isNotEmpty()) {
                    AsyncImage(
                        model = item.images.first(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(colors.accent.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = colors.accent.copy(alpha = 0.2f), modifier = Modifier.size(40.dp))
                    }
                }
                
                // Amount Badge
                Surface(
                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
                    color = colors.accent,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = item.amount.toString(),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }

                // Showcase Toggle
                IconButton(
                    onClick = { onToggleShowcase(!item.isShowcase) },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                ) {
                    Surface(shape = CircleShape, color = colors.canvas.copy(alpha = 0.7f), modifier = Modifier.size(32.dp)) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (item.isShowcase) Icons.Default.Storefront else Icons.Outlined.Storefront,
                                contentDescription = null,
                                tint = if (item.isShowcase) colors.accent else colors.ink,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
            
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.description.ifEmpty { "..." },
                        fontSize = 11.sp,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEdit(item) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = colors.muted, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, colors: KianColors) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = colors.ink
        )
    }
}

@Composable
fun EmptyState(message: String, colors: KianColors) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.ConfirmationNumber, contentDescription = null, tint = colors.line, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = colors.muted, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}
