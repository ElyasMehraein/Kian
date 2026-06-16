package com.ely.kian.ui.screens.wallet.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.KianApp
import com.ely.kian.R
import com.ely.kian.data.local.entities.ProductCategory
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.theme.KianColors

@Composable
fun BalanceRow(
    item: BalanceItem,
    myCategories: List<ProductCategory>,
    onProducerClick: (String) -> Unit,
    formatAssetRef: (String) -> String,
    onToggleShowcase: (String, Boolean) -> Unit,
    onEdit: (BalanceItem) -> Unit,
    colors: KianColors
) {
    var producerName by remember { mutableStateOf(formatAssetRef(item.producer)) }
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    
    LaunchedEffect(item.producer) {
        val profile = app.container.userProfileDao.getProfile(item.producer)
        if (profile != null) {
            producerName = profile.displayName ?: profile.name ?: producerName
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        shape = RoundedCornerShape(20.dp),
        color = colors.panel,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Column(modifier = Modifier.clickable { onEdit(item) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name, 
                            fontSize = 20.sp, 
                            fontWeight = FontWeight.ExtraBold, 
                            color = colors.ink
                        )
                        if (item.description.isNotEmpty()) {
                            Text(
                                text = item.description,
                                fontSize = 14.sp,
                                color = colors.muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                    
                    Surface(
                        color = colors.accent,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = item.amount.toString(), 
                                fontSize = 24.sp, 
                                fontWeight = FontWeight.Black, 
                                color = Color.White
                            )
                            Text(
                                text = item.unit.lowercase(), 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                
                if (item.categories.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item.categories.forEach { categoryId ->
                            val categoryName = myCategories.find { it.id == categoryId }?.name ?: categoryId
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.accent.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "#$categoryName",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accent,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.tap_to_edit),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.accent,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = colors.line.copy(alpha = 0.5f)
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(CircleShape)
                        .clickable { onProducerClick(item.producer) }
                        .padding(end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(colors.line, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            producerName.take(1).uppercase(),
                            color = colors.ink,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.producer),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.muted,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = producerName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.ink
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = item.isShowcase,
                        onCheckedChange = { onToggleShowcase(item.assetRef, it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.accent,
                            uncheckedThumbColor = colors.muted,
                            uncheckedTrackColor = colors.line
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                    Text(
                        text = stringResource(R.string.showcase),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.muted
                    )
                }
                
                IconButton(onClick = { }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Details", tint = colors.muted)
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
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(100.dp),
        color = colors.panel.copy(alpha = 0.5f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.line.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(message, color = colors.muted, fontSize = 14.sp)
        }
    }
}
