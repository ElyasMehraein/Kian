package com.ely.kian.ui.screens.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.KianTheme

data class ProductInfo(
    val id: String,
    val name: String,
    val description: String,
    val price: String,
    val image: String? = null
)

data class ReviewInfo(
    val author: String,
    val rating: Int,
    val comment: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileScreen(
    pubkey: String,
    onBack: () -> Unit,
    onChat: () -> Unit,
    onCart: () -> Unit
) {
    val kianColors = KianTheme.colors
    val mockProducts = listOf(
        ProductInfo("1", "Organic Honey", "Pure, raw honey from local bees.", "15 Units"),
        ProductInfo("2", "Beeswax Candle", "Hand-rolled natural beeswax.", "10 Units")
    )
    val mockReviews = listOf(
        ReviewInfo("Alice", 5, "Great honey, fast delivery!"),
        ReviewInfo("Bob", 4, "Really good quality.")
    )

    Scaffold(
        containerColor = kianColors.canvas,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            tint = kianColors.ink
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onCart) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart, 
                            contentDescription = "Cart", 
                            tint = kianColors.ink.copy(alpha = 0.5f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            contentPadding = PaddingValues(20.dp)
        ) {
            item {
                InitialAvatar(name = "Merchant", size = 88.dp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = "Merchant Name", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                Text(text = pubkey.take(16) + "...", fontSize = 13.sp, color = kianColors.ink.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Providing the best local organic products in the region.", 
                    fontSize = 15.sp, 
                    lineHeight = 24.sp, 
                    color = kianColors.ink
                )
                
                Row(modifier = Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    KianButton(text = "Start messaging", onClick = onChat)
                    KianButton(
                        text = "Write review", 
                        onClick = {}, 
                        type = com.ely.kian.ui.components.ButtonType.Secondary
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "Products", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(mockProducts) { product ->
                ProductCard(product)
                Spacer(modifier = Modifier.height(10.dp))
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Reviews", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = kianColors.ink)
                Spacer(modifier = Modifier.height(12.dp))
            }

            items(mockReviews) { review ->
                ReviewCard(review)
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
fun ProductCard(product: ProductInfo) {
    val kianColors = KianTheme.colors
    var inCart by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(12.dp))
            .border(1.dp, kianColors.line, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)).background(kianColors.line))
        Spacer(modifier = Modifier.height(12.dp))
        Text(text = product.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
        Text(text = product.description, fontSize = 14.sp, color = kianColors.ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
        Text(text = "Requirement: ${product.price}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = kianColors.accent, modifier = Modifier.padding(top = 4.dp))
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Button(
            onClick = { inCart = !inCart },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (inCart) Color(0xFFECFDF5) else kianColors.ink,
                contentColor = if (inCart) Color(0xFF065F46) else kianColors.canvas
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            modifier = Modifier.align(Alignment.Start)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = if (inCart) Icons.Default.CheckCircle else Icons.Default.AddShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = if (inCart) "Added to cart" else "Add to cart", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ReviewCard(review: ReviewInfo) {
    val kianColors = KianTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(kianColors.panel, RoundedCornerShape(12.dp))
            .border(1.dp, kianColors.line, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Text(text = "Rating: ${review.rating}/5", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kianColors.ink)
        Text(text = review.comment, fontSize = 14.sp, color = kianColors.ink.copy(alpha = 0.6f), modifier = Modifier.padding(top = 4.dp))
    }
}
