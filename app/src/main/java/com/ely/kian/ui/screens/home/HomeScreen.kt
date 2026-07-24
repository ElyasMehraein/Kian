package com.ely.kian.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ely.kian.KianApp
import com.ely.kian.R
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianChip
import com.ely.kian.ui.components.MerchantCard
import com.ely.kian.ui.theme.KianTheme
import kotlinx.coroutines.delay

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
    val isSearchActive by viewModel.isSearchActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val sortOptions = listOf(
        "Nearest" to R.string.nearest,
        "Online" to R.string.online_merchants,
        "Top Rated" to R.string.top_rated,
        "Verified" to R.string.verified
    )

    val focusRequester = remember { FocusRequester() }

    // Animate Header Search Bar Width over 600ms
    val searchBarWidthFraction by animateFloatAsState(
        targetValue = if (isSearchActive) 1f else 0f,
        animationSpec = tween(600),
        label = "SearchBarWidthAnimation"
    )

    // Animate Overlay Panel Expansion Progress (0 to 1) over 600ms
    val isOverlayVisible = isSearchActive && searchQuery.isNotEmpty()
    val overlayHeightFraction by animateFloatAsState(
        targetValue = if (isOverlayVisible) 1f else 0f,
        animationSpec = tween(600),
        label = "OverlayHeightAnimation"
    )

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val maxWidth = maxWidth
        val maxHeight = maxHeight

        // Main Home Column (Header + Home Content)
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                // Title and Subtitle
                if (searchBarWidthFraction < 0.8f) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .alpha(1f - (searchBarWidthFraction / 0.8f))
                    ) {
                        Text(
                            text = stringResource(R.string.merchants),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = kianColors.ink,
                            lineHeight = 30.sp
                        )
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            fontSize = 13.sp,
                            color = kianColors.muted,
                            lineHeight = 16.sp
                        )
                    }
                }

                // Search Icon (Visible when search is NOT active)
                if (!isSearchActive && searchBarWidthFraction == 0f) {
                    IconButton(
                        onClick = { viewModel.setIsSearchActive(true) },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.search),
                            tint = kianColors.ink
                        )
                    }
                }

                // Smoothly Expanding Search TextField (Width grows over 0.6 second)
                if (searchBarWidthFraction > 0f) {
                    val animatedWidth = (maxWidth - 96.dp) * searchBarWidthFraction

                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 56.dp)
                            .width(animatedWidth)
                    ) {
                        TextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = {
                                Text(
                                    stringResource(R.string.search_hint),
                                    color = kianColors.muted,
                                    fontSize = 14.sp
                                )
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = kianColors.panel,
                                unfocusedContainerColor = kianColors.panel,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = kianColors.ink,
                                unfocusedTextColor = kianColors.ink
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.search),
                                    tint = kianColors.muted
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { viewModel.setIsSearchActive(false) }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = kianColors.muted
                                    )
                                }
                            }
                        )
                    }
                }

                // Focus request after animation finishes (500ms)
                LaunchedEffect(isSearchActive) {
                    if (isSearchActive) {
                        delay(500)
                        focusRequester.requestFocus()
                    }
                }
            }

            // Sort Chips
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
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

            if (selectedSort == "Online") {
                Text(
                    text = stringResource(R.string.sort_online_desc),
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
                            isOnline = merchant.isOnline,
                            onClick = { onMerchantClick(merchant.pubkey) }
                        )
                    }
                }
            }
        }

        // Search Results Overlay Panel (Expands Vertically downwards over 600ms)
        if (overlayHeightFraction > 0f) {
            val animatedHeight = (maxHeight - 64.dp) * overlayHeightFraction

            Surface(
                modifier = Modifier
                    .padding(top = 64.dp)
                    .fillMaxWidth()
                    .height(animatedHeight)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = kianColors.canvas.copy(alpha = 0.96f),
                shadowElevation = 8.dp,
                tonalElevation = 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    val currentSearchResults = searchResults
                    if (currentSearchResults == null) {
                        // Loading State
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(
                                color = kianColors.accent,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(text = stringResource(R.string.searching), color = kianColors.muted)
                        }
                    } else if (currentSearchResults.isEmpty()) {
                        Text(
                            text = stringResource(R.string.no_results_found),
                            color = kianColors.muted,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 24.dp)
                        ) {
                            items(currentSearchResults) { profile ->
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = profile.displayName ?: profile.name ?: "Unknown",
                                            fontWeight = FontWeight.Bold,
                                            color = kianColors.ink
                                        )
                                    },
                                    supportingContent = {
                                        Column {
                                            if (!profile.nip05.isNullOrEmpty()) {
                                                Text(
                                                    text = profile.nip05,
                                                    color = kianColors.accent,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            Text(
                                                text = profile.pubkey,
                                                color = kianColors.muted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!profile.about.isNullOrEmpty()) {
                                                Text(
                                                    text = profile.about,
                                                    color = kianColors.muted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    },
                                    leadingContent = {
                                        InitialAvatar(
                                            name = profile.displayName ?: profile.name ?: "U",
                                            pictureUrl = profile.picture,
                                            size = 40.dp
                                        )
                                    },
                                    colors = ListItemDefaults.colors(
                                        containerColor = kianColors.panel,
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onMerchantClick(profile.pubkey) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}