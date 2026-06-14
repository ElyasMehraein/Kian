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
import com.ely.kian.ui.components.ScreenHeader
import com.ely.kian.ui.theme.KianTheme

@Composable
fun HomeScreen(
    onMerchantClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao,
            (LocalContext.current.applicationContext as KianApp).container.secureStorage
        )
    )
) {
    val kianColors = KianTheme.colors
    val merchants by viewModel.merchants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    
    val sortOptions = listOf("All", "Nearest", "Top Rated", "Verified")

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ScreenHeader(title = "Merchants")

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
                    onClick = { viewModel.setSort(option) }
                )
            }
        }

        if (selectedSort == "Verified") {
            Text(
                text = "Based on follows by you and people you follow",
                fontSize = 12.sp,
                color = kianColors.ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
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
                    val ratingText = if (selectedSort == "Verified") {
                        "${merchant.title} (${merchant.mutualFollows} follows)"
                    } else {
                        "${merchant.title} (${merchant.socialRating})"
                    }
                    
                    MerchantCard(
                        name = merchant.profile.displayName ?: merchant.profile.name ?: "Unknown",
                        bio = merchant.profile.about ?: "No bio yet.",
                        rating = ratingText,
                        distance = if (merchant.distanceKm != null) {
                            if (merchant.distanceKm < 1) "${(merchant.distanceKm * 1000).toInt()} m"
                            else "${"%.1f".format(merchant.distanceKm)} km"
                        } else "Distance unknown",
                        pictureUrl = merchant.profile.picture,
                        onClick = { onMerchantClick(merchant.pubkey) }
                    )
                }
            }
        }
    }
}
