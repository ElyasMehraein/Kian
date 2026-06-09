package com.ely.kian.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.components.MerchantCard
import com.ely.kian.ui.theme.Ink

data class Merchant(
    val id: String,
    val name: String,
    val bio: String,
    val rating: String,
    val distance: String
)

val mockMerchants = listOf(
    Merchant("1", "Alice's Organics", "Fresh fruits and vegetables from local farms.", "4.9", "0.5 km"),
    Merchant("2", "Bob's Bakery", "Best sourdough in town. Open since 1995.", "4.7", "1.2 km"),
    Merchant("3", "Charlie's Coffee", "Specialty beans and cozy atmosphere.", "4.8", "0.8 km"),
    Merchant("4", "David's Deli", "Authentic sandwiches and cured meats.", "4.6", "2.5 km"),
)

@Composable
fun HomeScreen(onMerchantClick: (String) -> Unit) {
    var selectedSort by remember { mutableStateOf("All") }
    val sortOptions = listOf("All", "Nearest", "Top Rated", "Verified")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        // Header
        Text(
            text = "Merchants",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Ink,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp)
        )

        // Sort Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(sortOptions) { option ->
                KianChip(
                    text = option,
                    selected = selectedSort == option,
                    onClick = { selectedSort = option }
                )
            }
        }

        // Merchant List
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(mockMerchants) { merchant ->
                MerchantCard(
                    name = merchant.name,
                    bio = merchant.bio,
                    rating = merchant.rating,
                    distance = merchant.distance,
                    onClick = { onMerchantClick(merchant.id) }
                )
            }
        }
    }
}
