package com.ely.kian.ui.screens.merchant

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.viewinterop.AndroidView
import com.ely.kian.KianApp
import com.ely.kian.data.local.entities.Product
import com.ely.kian.data.local.entities.Review
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.screens.merchant.components.*
import com.ely.kian.ui.theme.KianTheme
import com.ely.kian.util.Geohash
import com.ely.kian.util.NavigationUtils
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import coil.compose.AsyncImage
import kotlinx.serialization.json.Json
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantProfileScreen(
    pubkey: String,
    onBack: () -> Unit,
    onCart: () -> Unit,
    onEdit: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onFollowersClick: (String) -> Unit = {},
    ownPubkey: String? = null,
    viewModel: MerchantProfileViewModel = viewModel(
        factory = MerchantProfileViewModel.provideFactory(
            pubkey,
            ownPubkey,
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao,
            (LocalContext.current.applicationContext as KianApp).container.productRepository,
            (LocalContext.current.applicationContext as KianApp).container.tokenRepository,
            (LocalContext.current.applicationContext as KianApp).container.reviewDao,
            (LocalContext.current.applicationContext as KianApp).container.nostrSyncManager,
            (LocalContext.current.applicationContext as KianApp).container.secureStorage
        )
    )
) {
    val kianColors = KianTheme.colors
    val profile by viewModel.profile.collectAsState()
    val products by viewModel.products.collectAsState()
    val showcaseTokens by viewModel.showcaseTokens.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val reviews by viewModel.reviews.collectAsState()
    val userReview by viewModel.userReview.collectAsState()
    val isFollowing by viewModel.isFollowing.collectAsState()
    val followerCount by viewModel.followerCount.collectAsState()
    val isOwnProfile = viewModel.isOwnProfile
    val uriHandler = LocalUriHandler.current
    
    var cartIconPosition by remember { mutableStateOf(Offset.Zero) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var selectedFilterPath by remember { mutableStateOf<List<com.ely.kian.data.local.entities.ProductCategory>>(emptyList()) }
    
    // Animation states
    var flyingImage by remember { mutableStateOf<String?>(null) }
    var flyingStart by remember { mutableStateOf(Offset.Zero) }
    var isFlying by remember { mutableStateOf(false) }

    val animProgress = remember { Animatable(0f) }

    LaunchedEffect(isFlying) {
        if (isFlying) {
            animProgress.snapTo(0f)
            animProgress.animateTo(1f, animationSpec = tween(800, easing = FastOutSlowInEasing))
            isFlying = false
            flyingImage = null
        }
    }

    val avgRating = remember(reviews) {
        if (reviews.isEmpty()) "0.0"
        else "%.1f".format(reviews.map { it.rating }.average())
    }

    val filteredProducts = remember(products, selectedFilterPath, categories) {
        val selectedLeaf = selectedFilterPath.lastOrNull()
        if (selectedLeaf == null) products
        else {
            val allowedIds = getBranchIds(categories, selectedLeaf.id)
            products.filter { product ->
                try {
                    val pCats = Json.decodeFromString<List<String>>(product.categories)
                    pCats.any { it in allowedIds }
                } catch (e: Exception) { false }
            }
        }
    }

    val filteredTokens = remember(showcaseTokens, selectedFilterPath, categories) {
        val selectedLeaf = selectedFilterPath.lastOrNull()
        if (selectedLeaf == null) showcaseTokens
        else {
            val allowedIds = getBranchIds(categories, selectedLeaf.id)
            showcaseTokens.filter { token ->
                token.categories.any { it in allowedIds }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = kianColors.canvas,
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                TopAppBar(
                    title = { },
                    navigationIcon = {
                        if (!isOwnProfile) {
                            IconButton(onClick = onBack, modifier = Modifier.background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = kianColors.ink)
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { /* Share */ }, modifier = Modifier.background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = kianColors.ink)
                        }
                        if (!isOwnProfile) {
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = onCart,
                                modifier = Modifier
                                    .background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                    .onGloballyPositioned { cartIconPosition = it.positionInWindow() }
                            ) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = stringResource(R.string.cart), tint = kianColors.ink)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues)
            ) {
                item {
                    // Header / Cover area
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        if (!profile?.banner.isNullOrBlank()) {
                            AsyncImage(
                                model = profile?.banner,
                                contentDescription = "Banner",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(kianColors.accent, kianColors.accentSoft)
                                        )
                                    )
                            )
                        }
                        // Bottom overlay for transition
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .align(Alignment.BottomCenter)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, kianColors.canvas)
                                    )
                                )
                        )
                    }
                    
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .offset(y = (-60).dp)
                    ) {
                        val context = LocalContext.current
                        InitialAvatar(
                            name = profile?.displayName ?: profile?.name ?: stringResource(R.string.merchant), 
                            pictureUrl = profile?.picture, 
                            size = 110.dp,
                            modifier = Modifier.border(4.dp, kianColors.canvas, RoundedCornerShape(55.dp))
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = profile?.displayName ?: profile?.name ?: stringResource(R.string.merchant), 
                                fontSize = 28.sp, 
                                fontWeight = FontWeight.Bold, 
                                color = kianColors.ink
                            )
                            if (!profile?.nip05.isNullOrBlank()) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = Icons.Default.Verified, 
                                    contentDescription = stringResource(R.string.verified), 
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = if (!profile?.name.isNullOrBlank()) "@${profile?.name}" else "@" + pubkey.take(12) + "...", 
                            fontSize = 14.sp, 
                            color = kianColors.muted,
                            fontWeight = FontWeight.Medium
                        )
                        
                        if (!profile?.website.isNullOrBlank()) {
                            Row(
                                modifier = Modifier.padding(top = 10.dp).clickable { uriHandler.openUri(profile?.website!!) },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Language, contentDescription = null, tint = kianColors.accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = profile?.website!!.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                                    fontSize = 14.sp,
                                    color = kianColors.accent,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (!profile?.location.isNullOrBlank() || !profile?.geohash.isNullOrBlank()) {
                            val context = LocalContext.current
                            Row(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .clickable(enabled = !profile?.geohash.isNullOrBlank()) {
                                        profile?.geohash?.let { hash ->
                                            try {
                                                val (lat, lon) = Geohash.decode(hash)
                                                NavigationUtils.openInMaps(
                                                    context,
                                                    lat,
                                                    lon,
                                                    profile?.displayName ?: profile?.name ?: "Merchant"
                                                )
                                            } catch (e: Exception) { /* Ignore */ }
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = if (!profile?.geohash.isNullOrBlank()) kianColors.accent else kianColors.muted,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = profile?.location ?: stringResource(R.string.open_navigation),
                                    fontSize = 14.sp,
                                    color = if (!profile?.geohash.isNullOrBlank()) kianColors.accent else kianColors.muted,
                                    fontWeight = FontWeight.Medium,
                                    textDecoration = if (!profile?.geohash.isNullOrBlank()) androidx.compose.ui.text.style.TextDecoration.Underline else null
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        
                        if (!profile?.about.isNullOrBlank()) {
                            Text(
                                text = profile?.about ?: "",
                                fontSize = 15.sp,
                                lineHeight = 22.sp,
                                color = kianColors.ink.copy(alpha = 0.8f)
                            )
                        }

                        if (!profile?.geohash.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(20.dp))
                            Text(
                                text = stringResource(R.string.location),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = kianColors.ink
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val coords = remember(profile?.geohash) {
                                try { Geohash.decode(profile?.geohash!!) } catch (e: Exception) { null }
                            }
                            
                            if (coords != null) {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
                                        .clip(RoundedCornerShape(16.dp))
                                        .border(1.dp, kianColors.line, RoundedCornerShape(16.dp)),
                                    onClick = {
                                        NavigationUtils.openInMaps(context, coords.first, coords.second, profile?.displayName ?: profile?.name ?: "Merchant")
                                    }
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        AndroidView(
                                            modifier = Modifier.fillMaxSize(),
                                            factory = { ctx ->
                                                MapView(ctx).apply {
                                                    setTileSource(TileSourceFactory.MAPNIK)
                                                    setMultiTouchControls(false) // Read only basically
                                                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                                                    controller.setZoom(15.0)
                                                    val point = GeoPoint(coords.first, coords.second)
                                                    controller.setCenter(point)
                                                    
                                                    val marker = Marker(this)
                                                    marker.position = point
                                                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                                    overlays.add(marker)
                                                }
                                            }
                                        )
                                        
                                        // Overlay to prevent map interaction but allow card click
                                        Box(modifier = Modifier.fillMaxSize().background(Color.Transparent))
                                        
                                        Surface(
                                            modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                                            color = kianColors.ink.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = stringResource(R.string.open_navigation),
                                                color = kianColors.canvas,
                                                fontSize = 12.sp,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        Row(
                            modifier = Modifier.padding(top = 24.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            ProfileStat(label = stringResource(R.string.products), value = products.size.toString())
                            ProfileStat(
                                label = stringResource(R.string.followers),
                                value = followerCount.toString(),
                                onClick = { onFollowersClick(pubkey) }
                            )
                            ProfileStat(label = stringResource(R.string.rating), value = avgRating, icon = Icons.Default.Star)
                        }

                        // Action Buttons
                        Row(modifier = Modifier.padding(top = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (isOwnProfile) {
                                KianButton(
                                    text = stringResource(R.string.edit_profile),
                                    onClick = onEdit,
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                KianButton(
                                    text = stringResource(R.string.message),
                                    onClick = { onMessage(pubkey) },
                                    modifier = Modifier.weight(1f)
                                )
                                KianButton(
                                    text = if (isFollowing) stringResource(R.string.unfollow) else stringResource(R.string.follow),
                                    onClick = { viewModel.toggleFollow() },
                                    type = if (isFollowing) com.ely.kian.ui.components.ButtonType.Secondary else com.ely.kian.ui.components.ButtonType.Primary,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Sticky Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = kianColors.canvas,
                        contentColor = kianColors.accent,
                        divider = { HorizontalDivider(color = kianColors.line) },
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = kianColors.accent
                            )
                        },
                        modifier = Modifier.offset(y = (-30).dp)
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.showcase_tab), fontWeight = FontWeight.Bold) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.reviews_tab), fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                if (selectedTab == 0) {
                    if (products.isEmpty() && showcaseTokens.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_showcase_items), color = kianColors.muted)
                            }
                        }
                    } else {
                        item {
                            CategoryFilterBar(
                                categories = categories,
                                selectedPath = selectedFilterPath,
                                onPathChange = { selectedFilterPath = it }
                            )
                        }

                        if (filteredProducts.isEmpty() && filteredTokens.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_products_category), color = kianColors.muted)
                                }
                            }
                        }

                        items(filteredTokens) { token ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                ShowcaseTokenCard(token)
                            }
                        }

                        items(filteredProducts) { product ->
                            val productCats = remember(product, categories) {
                                try {
                                    val ids = Json.decodeFromString<List<String>>(product.categories)
                                    categories.filter { it.id in ids }
                                } catch (e: Exception) { emptyList() }
                            }
                            
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                ProductCard(
                                    product = product,
                                    categories = productCats,
                                    showAddToCart = !isOwnProfile,
                                    onAddToCart = { qty, pos, img ->
                                        flyingImage = img
                                        flyingStart = pos
                                        isFlying = true
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (!isOwnProfile) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                KianButton(
                                    text = if (userReview != null) stringResource(R.string.edit_your_review) else stringResource(R.string.write_review),
                                    onClick = { showReviewDialog = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    type = com.ely.kian.ui.components.ButtonType.Secondary
                                )
                            }
                        }
                    }
                    
                    if (reviews.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                Text(stringResource(R.string.no_reviews_yet), color = kianColors.muted)
                            }
                        }
                    } else {
                        items(reviews) { review ->
                            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                ReviewCard(review)
                            }
                        }
                    }
                }
                
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }

        // Flying animation overlay
        if (flyingImage != null) {
            val offsetX = androidx.compose.ui.util.lerp(flyingStart.x, cartIconPosition.x, animProgress.value)
            val offsetY = androidx.compose.ui.util.lerp(flyingStart.y, cartIconPosition.y, animProgress.value)
            val scale = androidx.compose.ui.util.lerp(1f, 0.1f, animProgress.value)
            val alpha = androidx.compose.ui.util.lerp(1f, 0f, animProgress.value)

            AsyncImage(
                model = flyingImage,
                contentDescription = null,
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(70.dp)
                    .scale(scale)
                    .alpha(alpha)
                    .clip(RoundedCornerShape(12.dp))
                    .background(kianColors.line),
                contentScale = ContentScale.Crop
            )
        }
    }

    if (showReviewDialog) {
        ReviewDialog(
            initialRating = userReview?.rating ?: 5,
            initialComment = userReview?.comment ?: "",
            onDismiss = { showReviewDialog = false },
            onConfirm = { rating, comment ->
                viewModel.postReview(rating, comment)
                showReviewDialog = false
            }
        )
    }
}
