package com.ely.kian.ui.screens.merchant.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ely.kian.R
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.theme.KianTheme

@Composable
fun ShowcaseTokenCard(
    token: BalanceItem,
    showAddToCart: Boolean = true,
    onAddToCart: (Long, Offset, String?) -> Unit = { _, _, _ -> }
) {
    val kianColors = KianTheme.colors
    var quantity by remember { mutableLongStateOf(1L) }
    var itemPosition by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(24.dp))
            .border(1.dp, kianColors.line, RoundedCornerShape(24.dp))
            .padding(16.dp)
            .onGloballyPositioned { itemPosition = it.positionInWindow() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = token.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                Text(
                    text = token.description,
                    fontSize = 14.sp,
                    color = kianColors.ink.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Surface(
                color = kianColors.accent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(start = 12.dp)
            ) {
                Text(
                    text = token.amount.toString(),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
        
        if (token.images.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = token.images.first(),
                contentDescription = token.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(kianColors.line),
                contentScale = ContentScale.Crop
            )
        }

        if (showAddToCart) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(kianColors.line.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { if (quantity > 1) quantity-- },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("-", fontWeight = FontWeight.Bold, color = kianColors.ink)
                    }
                    Text(
                        text = quantity.toString(),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        fontWeight = FontWeight.Bold,
                        color = kianColors.ink
                    )
                    IconButton(
                        onClick = { if (quantity < token.amount) quantity++ },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Text("+", fontWeight = FontWeight.Bold, color = kianColors.ink)
                    }
                }

                Button(
                    onClick = { onAddToCart(quantity, itemPosition, token.images.firstOrNull()) },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = kianColors.ink,
                        contentColor = kianColors.canvas
                    ),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.AddShoppingCart, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text(text = stringResource(R.string.add_to_cart), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
