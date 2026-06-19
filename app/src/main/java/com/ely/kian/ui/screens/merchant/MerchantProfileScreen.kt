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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
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
import com.ely.kian.data.local.entities.Review
import com.ely.kian.data.local.entities.VoucherCategory
import com.ely.kian.data.local.entities.Profile
import com.ely.kian.data.repository.BalanceItem
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.ui.components.KianButton
import com.ely.kian.ui.components.QrCodeDialog
import com.ely.kian.ui.screens.merchant.components.*
import com.ely.kian.ui.theme.KianTheme
import com.ely.kian.ui.screens.vouchers.components.FilterChip
import com.ely.kian.util.Geohash
import com.ely.kian.util.NavigationUtils
import androidx.compose.ui.res.stringResource
import com.ely.kian.R
import coil.compose.AsyncImage
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
    onEdit: () -> Unit = {},
    onMessage: (String) -> Unit = {},
    onFollowersClick: (String) -> Unit = {},
    ownPubkey: String? = null,
    viewModel: MerchantProfileViewModel = viewModel(
        factory = MerchantProfileViewModel.provideFactory(
            pubkey,
            ownPubkey,
            (LocalContext.current.applicationContext as KianApp).container.userProfileDao,
            (LocalContext.current.applicationContext as KianApp).container.voucherRepository,
            (LocalContext.current.applicationContext as KianApp).container.chatRepository,
            (LocalContext.current.applicationContext as KianApp).container.reviewDao,
            (LocalContext.current.applicationContext as KianApp).container.nostrSyncManager,
            (LocalContext.current.applicationContext as KianApp).container.secureStorage
        )
    )
) {
    val kianColors = KianTheme.colors
    val profile by viewModel.profile.collectAsState(null)
    val showcaseTokens by viewModel.showcaseTokens.collectAsState(emptyList<BalanceItem>())
    val merchantCategories by viewModel.categories.collectAsState(emptyList<VoucherCategory>())
    val selectedCat by viewModel.selectedCategoryId.collectAsState(null)

    val breadcrumbPath = remember(selectedCat, merchantCategories) {
        val path = mutableListOf<VoucherCategory>()
        var current = merchantCategories.find { it.id == selectedCat }
        while (current != null) {
            path.add(0, current)
            current = merchantCategories.find { it.id == current.parentId }
        }
        path
    }

    val currentOptions = remember(selectedCat, merchantCategories) {
        merchantCategories.filter { it.parentId == selectedCat }.sortedBy { it.name }
    }

    val reviews by viewModel.reviews.collectAsState(emptyList<Review>())
    val userReview by viewModel.userReview.collectAsState(null)
    val isFollowing by viewModel.isFollowing.collectAsState(false)
    val followerCount by viewModel.followerCount.collectAsState(0)
    val isRefreshing by viewModel.isRefreshing.collectAsState(false)
    val syncErrorResId by viewModel.syncErrorResId.collectAsState(null)
    val syncErrorArgs by viewModel.syncErrorArgs.collectAsState(emptyArray<Any>())
    val isOwnProfile = viewModel.isOwnProfile
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    LaunchedEffect(syncErrorResId) {
        syncErrorResId?.let { resId ->
            val message = context.getString(resId, *syncErrorArgs)
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearSyncError()
        }
    }
    
    var selectedTab by remember { mutableIntStateOf(0) }
    var showReviewDialog by remember { mutableStateOf(false) }
    var showQrDialog by remember { mutableStateOf(false) }
    
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

    Box(modifier = Modifier.fillMaxSize()) {
        val pullToRefreshState = rememberPullToRefreshState()
        
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refresh() },
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = isRefreshing,
                    modifier = Modifier.align(Alignment.TopCenter),
                    color = kianColors.accent,
                    containerColor = kianColors.panel
                )
            }
        ) {
            Scaffold(
                containerColor = kianColors.canvas,
                contentWindowInsets = WindowInsets(0.dp),
                topBar = {
                    MerchantTopBar(
                        onBack = onBack,
                        showBack = !isOwnProfile,
                        onShare = {
                            val shareText = context.getString(R.string.share_profile_message, pubkey)
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.share)))
                        },
                        onShowQr = { showQrDialog = true }
                    )
                }
            ) { paddingValues ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues)
                ) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                            val bannerUrl = profile?.banner
                            if (!bannerUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = bannerUrl,
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
                                if (profile?.nip05?.isNotBlank() == true) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Verified, 
                                        contentDescription = stringResource(R.string.verified), 
                                        tint = Color(0xFF3B82F6),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            
                            val nameTag = profile?.name
                            Text(
                                text = if (!nameTag.isNullOrBlank()) "@$nameTag" else "@" + pubkey.take(12) + "...", 
                                fontSize = 14.sp, 
                                color = kianColors.muted,
                                fontWeight = FontWeight.Medium
                            )
                            
                            val website = profile?.website
                            if (!website.isNullOrBlank()) {
                                Row(
                                    modifier = Modifier.padding(top = 10.dp).clickable { uriHandler.openUri(website) },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Language, contentDescription = null, tint = kianColors.accent, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = website.removePrefix("https://").removePrefix("http://").removeSuffix("/"),
                                        fontSize = 14.sp,
                                        color = kianColors.accent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            val geohash = profile?.geohash
                            val location = profile?.location
                            if (!location.isNullOrBlank() || !geohash.isNullOrBlank()) {
                                val context = LocalContext.current
                                Row(
                                    modifier = Modifier
                                        .padding(top = 8.dp)
                                        .clickable(enabled = !geohash.isNullOrBlank()) {
                                            geohash?.let { hash ->
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
                                        tint = if (!geohash.isNullOrBlank()) kianColors.accent else kianColors.muted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = location ?: stringResource(R.string.open_navigation),
                                        fontSize = 14.sp,
                                        color = if (!geohash.isNullOrBlank()) kianColors.accent else kianColors.muted,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (!geohash.isNullOrBlank()) androidx.compose.ui.text.style.TextDecoration.Underline else null
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            val about = profile?.about
                            if (!about.isNullOrBlank()) {
                                Text(
                                    text = about,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = kianColors.ink.copy(alpha = 0.8f)
                                )
                            }

                            if (!geohash.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    text = stringResource(R.string.location),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = kianColors.ink
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                val coords = remember(geohash) {
                                    try { Geohash.decode(geohash) } catch (e: Exception) { null }
                                }
                                
                                if (coords != null) {
                                    val context = LocalContext.current
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
                                                        setMultiTouchControls(false) 
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
                                ProfileStat(label = stringResource(R.string.vouchers), value = showcaseTokens.size.toString(), icon = Icons.Default.ConfirmationNumber)
                                ProfileStat(
                                    label = stringResource(R.string.followers),
                                    value = followerCount.toString(),
                                    onClick = { onFollowersClick(pubkey) }
                                )
                                ProfileStat(label = stringResource(R.string.rating), value = avgRating, icon = Icons.Default.Star)
                            }

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

                    item {
                        MerchantProfileTabs(selectedTab = selectedTab, onTabSelected = { selectedTab = it })
                    }

                    if (selectedTab == 0) {
                        if (merchantCategories.isNotEmpty()) {
                            item {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .offset(y = (-20).dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Level Path (Breadcrumb)
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 20.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        item {
                                            TextButton(
                                                onClick = { viewModel.selectCategory(null) },
                                                contentPadding = PaddingValues(horizontal = 8.dp),
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = if (selectedCat == null) kianColors.accent else kianColors.muted
                                                )
                                            ) {
                                                Text(stringResource(R.string.all), fontWeight = if (selectedCat == null) FontWeight.Bold else FontWeight.Normal)
                                            }
                                        }

                                        items(breadcrumbPath) { cat ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                                    contentDescription = null,
                                                    tint = kianColors.line,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                TextButton(
                                                    onClick = { viewModel.selectCategory(cat.id) },
                                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                                    colors = ButtonDefaults.textButtonColors(
                                                        contentColor = if (selectedCat == cat.id) kianColors.accent else kianColors.muted
                                                    )
                                                ) {
                                                    Text(cat.name, fontWeight = if (selectedCat == cat.id) FontWeight.Bold else FontWeight.Normal)
                                                }
                                            }
                                        }
                                    }

                                    // Children Options
                                    if (currentOptions.isNotEmpty()) {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 20.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            items(currentOptions) { cat ->
                                                FilterChip(
                                                    label = cat.name,
                                                    selected = false,
                                                    onClick = { viewModel.selectCategory(cat.id) },
                                                    colors = kianColors
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (showcaseTokens.isEmpty()) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                                    Text(stringResource(R.string.no_showcase_items), color = kianColors.muted)
                                }
                            }
                        } else {
                            items(showcaseTokens) { token ->
                                Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                                    ShowcaseTokenCard(
                                        token = token,
                                        showAddToCart = !isOwnProfile,
                                        onSendRequest = { qty, pos, img, spendingMsg, buyMsg ->
                                            flyingImage = img
                                            flyingStart = pos
                                            isFlying = true
                                            viewModel.sendPurchaseRequest(token, qty, spendingMsg, buyMsg)
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
        }

        if (flyingImage != null) {
            FlyingTokenAnimation(flyingImage = flyingImage!!, flyingStart = flyingStart, animProgress = animProgress.value)
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

    if (showQrDialog) {
        QrCodeDialog(
            content = "https://github.com/ElyasMehraein/Kian/releases?pk=$pubkey",
            title = profile?.displayName ?: profile?.name ?: stringResource(R.string.merchant),
            onDismiss = { showQrDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantTopBar(onBack: () -> Unit, showBack: Boolean, onShare: () -> Unit, onShowQr: () -> Unit) {
    val kianColors = KianTheme.colors
    TopAppBar(
        title = { },
        navigationIcon = {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showBack) {
                    IconButton(onClick = onBack, modifier = Modifier.background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = kianColors.ink)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }
                IconButton(onClick = onShare, modifier = Modifier.background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.Share, contentDescription = stringResource(R.string.share), tint = kianColors.ink)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onShowQr, modifier = Modifier.background(kianColors.canvas.copy(alpha = 0.6f), RoundedCornerShape(12.dp))) {
                    Icon(Icons.Default.QrCode, contentDescription = stringResource(R.string.scan_qr), tint = kianColors.ink)
                }
            }
        },
        actions = {
            Spacer(modifier = Modifier.width(8.dp))
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
fun MerchantProfileTabs(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val kianColors = KianTheme.colors
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
            onClick = { onTabSelected(0) },
            text = { Text(stringResource(R.string.showcase_tab), fontWeight = FontWeight.Bold) }
        )
        Tab(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            text = { Text(stringResource(R.string.reviews_tab), fontWeight = FontWeight.Bold) }
        )
    }
}

@Composable
fun FlyingTokenAnimation(flyingImage: String, flyingStart: Offset, animProgress: Float) {
    val kianColors = KianTheme.colors
    val offsetX = androidx.compose.ui.util.lerp(flyingStart.x, 0f, animProgress)
    val offsetY = androidx.compose.ui.util.lerp(flyingStart.y, 0f, animProgress)
    val scale = androidx.compose.ui.util.lerp(1f, 0.1f, animProgress)
    val alpha = androidx.compose.ui.util.lerp(1f, 0f, animProgress)

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