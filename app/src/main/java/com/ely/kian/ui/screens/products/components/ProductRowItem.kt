package com.ely.kian.ui.screens.products.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ely.kian.R
import com.ely.kian.data.local.entities.Product
import com.ely.kian.ui.theme.KianColors
import kotlinx.serialization.json.Json

@Composable
fun ProductRow(
    product: Product,
    categoryName: String?,
    onEdit: (Product) -> Unit,
    onDelete: (Product) -> Unit,
    onToggleShowcase: (Product, Boolean) -> Unit,
    colors: KianColors
) {
    val imageUrls = remember(product.images) {
        try { Json.decodeFromString<List<String>>(product.images) } catch (e: Exception) { emptyList<String>() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, RoundedCornerShape(16.dp))
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(modifier = Modifier.clickable { onEdit(product) }) {
            if (imageUrls.size == 1) {
                AsyncImage(
                    model = imageUrls.first(),
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.line),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else if (imageUrls.size > 1) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(imageUrls) { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = product.name,
                            modifier = Modifier
                                .width(280.dp)
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.line),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Text(text = product.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.ink)
            Text(
                text = product.description ?: stringResource(R.string.no_description),
                fontSize = 14.sp,
                color = colors.muted,
                modifier = Modifier.padding(top = 6.dp)
            )
            
            if (categoryName != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = colors.accent.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = stringResource(R.string.tap_to_edit),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.accent,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = product.isShowcase,
                    onCheckedChange = { onToggleShowcase(product, it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.canvas,
                        checkedTrackColor = colors.accent,
                        uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.line
                    ),
                    modifier = Modifier.scale(0.8f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.showcase),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.ink
                )
            }

            Button(
                onClick = { onDelete(product) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.danger.copy(alpha = 0.1f),
                    contentColor = colors.danger
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(stringResource(R.string.delete_product), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
