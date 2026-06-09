package com.ely.kian.ui.screens.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.theme.Canvas
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line
import com.ely.kian.ui.theme.Panel

data class CartItem(
    val productId: String,
    val name: String,
    val price: String,
    val quantity: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    onBack: () -> Unit,
    onCheckout: () -> Unit
) {
    val mockCartItems = listOf(
        CartItem("1", "Organic Honey", "15 Units", 1),
        CartItem("2", "Beeswax Candle", "10 Units", 2)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Shopping Cart", fontWeight = FontWeight.Bold, color = Ink) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Canvas)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = Canvas,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Requirements", fontSize = 18.sp, color = Color.Gray)
                        Text(text = "35 Units", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Ink)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    KianButton(
                        text = "Checkout",
                        onClick = onCheckout,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mockCartItems) { item ->
                CartItemRow(item)
            }
        }
    }
}

@Composable
fun CartItemRow(item: CartItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(Line))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = item.name, fontWeight = FontWeight.Bold, color = Ink)
            Text(text = "Qty: ${item.quantity}", fontSize = 12.sp, color = Color.Gray)
        }
        Text(text = "$${item.price}", fontWeight = FontWeight.Bold, color = Ink)
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = { }) {
            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Red)
        }
    }
}
