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
import androidx.compose.ui.graphics.Color
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

import androidx.compose.ui.res.stringResource
import com.ely.kian.R

@Composable
fun HomeScreen(
    onMerchantClick: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.provideFactory(
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao,
            (LocalContext.current.applicationContext as KianApp).container.reviewDao,
            (LocalContext.current.applicationContext as KianApp).container.secureStorage
        )
    )
) {
    val kianColors = KianTheme.colors
    val merchants by viewModel.merchants.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val userGeohash by viewModel.userGeohash.collectAsState()
    
    val sortOptions = listOf(
        "Nearest" to R.string.nearest,
        "Top Rated" to R.string.top_rated,
        "Verified" to R.string.verified
    )

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        ScreenHeader(title = stringResource(R.string.merchants))

        // Sort Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            items(sortOptions) { (key, labelId) ->
                KianChip(
                    text = stringResource(labelId),
                    selected = selectedSort == key,
                    onClick = { viewModel.setSort(key) }
                )
            }
        }

        if (selectedSort == "Top Rated") {
            Text(
                text = stringResource(R.string.sort_top_rated_desc),
                fontSize = 12.sp,
                color = kianColors.ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (selectedSort == "Verified") {
            Text(
                text = stringResource(R.string.sort_verified_desc),
                fontSize = 12.sp,
                color = kianColors.ink.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (selectedSort == "Nearest") {
            if (userGeohash == null) {
                Text(
                    text = stringResource(R.string.nearest_no_location),
                    fontSize = 12.sp,
                    color = kianColors.muted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            } else {
                Text(
                    text = stringResource(R.string.nearest_desc),
                    fontSize = 12.sp,
                    color = kianColors.ink.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = kianColors.accent)
            }
        } else if (merchants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.no_merchants_yet), color = kianColors.ink.copy(alpha = 0.5f))
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
                        name = merchant.profile.displayName ?: merchant.profile.name ?: stringResource(R.string.unknown),
                        bio = merchant.profile.about ?: stringResource(R.string.no_bio_yet),
                        rating = ratingText,
                        distance = if (merchant.distanceKm != null) {
                            if (merchant.distanceKm < 1) "${(merchant.distanceKm * 1000).toInt()} m"
                            else "${"%.1f".format(merchant.distanceKm)} km"
                        } else stringResource(R.string.distance_unknown),
                        pictureUrl = merchant.profile.picture,
                        onClick = { onMerchantClick(merchant.pubkey) }
                    )
                }
            }
        }
    }
}
