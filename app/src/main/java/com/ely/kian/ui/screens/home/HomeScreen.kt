package com.ely.kian.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ely.kian.KianApp
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.components.MerchantCard
import com.ely.kian.ui.theme.KianTheme

@Composable
fun HomeScreen(
    onMerchantClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao
        )
    )
) {
    val kianColors = KianTheme.colors
    val merchants by viewModel.merchants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
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
            color = kianColors.ink,
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

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = kianColors.accent)
            }
        } else if (merchants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "No merchants discovered yet.", color = kianColors.ink.copy(alpha = 0.5f))
            }
        } else {
            // Merchant List
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(merchants) { merchant ->
                    MerchantCard(
                        name = merchant.profile.displayName ?: merchant.profile.name ?: "Unknown",
                        bio = merchant.profile.about ?: "No bio yet.",
                        rating = "${merchant.title} (${merchant.socialRating})",
                        distance = if (merchant.distanceKm != null) "${merchant.distanceKm} km" else "Distance unknown",
                        onClick = { onMerchantClick(merchant.pubkey) }
                    )
                }
            }
        }
    }
}
