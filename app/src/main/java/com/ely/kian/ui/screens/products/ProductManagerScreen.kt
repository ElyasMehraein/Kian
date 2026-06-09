package com.ely.kian.ui.screens.products

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.components.KianInput
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line
import com.ely.kian.ui.theme.Panel

data class Product(
    val id: String,
    val name: String,
    val price: String,
    val category: String
)

val mockProducts = listOf(
    Product("1", "Organic Honey", "15.00", "Food"),
    Product("2", "Handmade Soap", "8.00", "Bath"),
    Product("3", "Wool Socks", "12.00", "Clothing"),
    Product("4", "Coffee Beans", "18.00", "Food"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductManagerScreen() {
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Food", "Bath", "Clothing")
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showBottomSheet = true },
                containerColor = Ink,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Product")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .statusBarsPadding()
        ) {
            // Header
            Text(
                text = "Product Manager",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
            )

            // Category Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(categories) { category ->
                    KianChip(
                        text = category,
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category }
                    )
                }
            }

            // Product List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val filteredProducts = if (selectedCategory == "All") mockProducts else mockProducts.filter { it.category == selectedCategory }
                items(filteredProducts) { product ->
                    ProductItem(product)
                }
            }
        }

        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState,
                containerColor = Color.White
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                ) {
                    Text(text = "Add New Product", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Spacer(modifier = Modifier.height(24.dp))
                    // In a real app, we'd have KianInput here
                    Text(text = "Product Name Placeholder", color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "Price Placeholder", color = Color.Gray)
                    Spacer(modifier = Modifier.height(24.dp))
                    KianButton(
                        text = "Create Product",
                        onClick = { showBottomSheet = false },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun ProductItem(product: Product) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Line)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, fontWeight = FontWeight.Bold, color = Ink)
            Text(text = product.category, fontSize = 12.sp, color = Color.Gray)
        }
        Text(text = "$${product.price}", fontWeight = FontWeight.Bold, color = Ink)
    }
}
