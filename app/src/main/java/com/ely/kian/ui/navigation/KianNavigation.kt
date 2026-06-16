package com.ely.kian.ui.navigation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ely.kian.ui.screens.onboarding.OnboardingViewModel
import com.ely.kian.ui.screens.onboarding.PrivateKeyViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ely.kian.ui.components.AppMenuButton
import com.ely.kian.ui.components.AppMenuDialog
import com.ely.kian.ui.components.LogoutConfirmationDialog
import com.ely.kian.ui.components.InitialAvatar
import com.ely.kian.KianApp
import com.ely.kian.ui.MainViewModel
import com.ely.kian.ui.screens.home.HomeScreen
import com.ely.kian.ui.screens.products.ProductManagerScreen
import com.ely.kian.ui.screens.products.ProductCategoriesScreen
import com.ely.kian.ui.screens.products.ProductViewModel
import com.ely.kian.ui.screens.wallet.WalletScreen
import com.ely.kian.ui.screens.wallet.SendTokenScreen
import com.ely.kian.ui.screens.backups.BackupScreen
import com.ely.kian.ui.screens.onboarding.OnboardingScreen
import com.ely.kian.ui.screens.onboarding.PrivateKeyScreen
import com.ely.kian.ui.screens.merchant.MerchantProfileScreen
import com.ely.kian.ui.screens.profile.ProfileEditScreen
import com.ely.kian.ui.screens.cart.CartScreen
import com.ely.kian.ui.screens.relays.RelayManagementScreen
import com.ely.kian.ui.screens.chat.ChatInboxScreen
import com.ely.kian.ui.screens.chat.ChatroomScreen
import com.ely.kian.ui.screens.chat.ChatViewModel
import com.ely.kian.ui.screens.pending.PendingEventsScreen
import com.ely.kian.ui.screens.pending.PendingEventsViewModel
import com.ely.kian.ui.theme.KianTheme

import androidx.compose.ui.res.stringResource
import com.ely.kian.R

sealed class Screen(val route: String, val labelId: Int, val icon: ImageVector) {
    object Home : Screen("home", R.string.home, Icons.Default.Home)
    object Chat : Screen("chat", R.string.chat, Icons.Default.Chat)
    object Wallet : Screen("wallet", R.string.wallet, Icons.Default.Wallet)
    object Products : Screen("products", R.string.products, Icons.Default.Inventory)
    object Profile : Screen("profile", R.string.profile, Icons.Default.Person)
    
    // Sub-screens
    object MerchantProfile : Screen("merchant/{pubkey}", R.string.merchant, Icons.Default.Person)
    object Chatroom : Screen("chat/{roomId}", R.string.chatroom, Icons.Default.Chat)
    object Cart : Screen("cart", R.string.cart, Icons.Default.ShoppingCart)
    object Backups : Screen("backups", R.string.backups, Icons.Default.Backup)
}

val items = listOf(
    Screen.Home,
    Screen.Chat,
    Screen.Wallet,
    Screen.Products,
    Screen.Profile,
)

@Composable
fun KianScaffold(initialChatRoomId: String? = null) {
    val context = LocalContext.current
    val app = context.applicationContext as KianApp
    val viewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(
            app.container.keyDao, 
            app.container.userProfileDao,
            app.container.nostrSyncManager,
            app.container.secureStorage,
            app.container.database,
            app.container.updateManager
        )
    )

    val navController = rememberNavController()
    var isMenuOpen by remember { mutableStateOf(false) }
    var isLogoutDialogOpen by remember { mutableStateOf(false) }
    val kianColors = KianTheme.colors
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = items.any { it.route == currentRoute }

    val isLoggedIn = viewModel.isLoggedIn
    val totalUnreadCount by viewModel.totalUnreadCount.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()

    LaunchedEffect(isLoggedIn, initialChatRoomId) {
        if (isLoggedIn == true && initialChatRoomId != null) {
            navController.navigate("chat/$initialChatRoomId") {
                popUpTo(Screen.Home.route)
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(Unit) {
        app.container.tokenRepository.notifications.collect { message ->
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn == false) {
            // Ensure we don't navigate before NavHost is ready or if already there
            val hasGraph = try { navController.graph; true } catch (e: Exception) { false }
            if (hasGraph && navController.currentDestination?.route != "onboarding") {
                navController.navigate("onboarding") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    if (isLoggedIn == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = kianColors.accent)
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBottomBar) {
                    NavigationBar(
                        containerColor = kianColors.canvas,
                        contentColor = kianColors.ink,
                        tonalElevation = 0.dp
                    ) {
                        val currentDestination = navBackStackEntry?.destination
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = {
                                    if (screen == Screen.Profile) {
                                        InitialAvatar(
                                            name = userProfile?.displayName ?: userProfile?.name ?: "",
                                            pictureUrl = userProfile?.picture,
                                            size = 24.dp
                                        )
                                    } else if (screen == Screen.Chat && totalUnreadCount > 0) {
                                        BadgedBox(
                                            badge = {
                                                Badge(
                                                    containerColor = Color.Red,
                                                    contentColor = Color.White
                                                ) {
                                                    Text(totalUnreadCount.toString())
                                                }
                                            }
                                        ) {
                                            Icon(screen.icon, contentDescription = null)
                                        }
                                    } else {
                                        Icon(screen.icon, contentDescription = null)
                                    }
                                },
                                label = { Text(stringResource(screen.labelId)) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id)
                                        launchSingleTop = true
                                    }
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = kianColors.ink,
                                    selectedTextColor = kianColors.ink,
                                    indicatorColor = kianColors.panel,
                                    unselectedIconColor = kianColors.ink.copy(alpha = 0.4f),
                                    unselectedTextColor = kianColors.ink.copy(alpha = 0.4f)
                                )
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = if (isLoggedIn == true) Screen.Home.route else "onboarding",
                modifier = Modifier.padding(innerPadding),
                enterTransition = { androidx.compose.animation.EnterTransition.None },
                exitTransition = { androidx.compose.animation.ExitTransition.None },
                popEnterTransition = { androidx.compose.animation.EnterTransition.None },
                popExitTransition = { androidx.compose.animation.ExitTransition.None }
            ) {
                composable(Screen.Home.route) { 
                    HomeScreen(onMerchantClick = { pubkey ->
                        navController.navigate("merchant/$pubkey")
                    }) 
                }
                composable(Screen.Chat.route) {
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = ChatViewModel.provideFactory(
                            app.container.chatRepository,
                            app.container.userProfileDao,
                            app.container.productRepository,
                            app.container.tokenRepository
                        )
                    )
                    ChatInboxScreen(
                        viewModel = chatViewModel,
                        onConversationClick = { roomId ->
                            navController.navigate("chat/$roomId")
                        }
                    )
                }
                composable(Screen.Chatroom.route) { backStackEntry ->
                    val roomId = backStackEntry.arguments?.getString("roomId") ?: ""
                    val chatViewModel: ChatViewModel = viewModel(
                        factory = ChatViewModel.provideFactory(
                            app.container.chatRepository,
                            app.container.userProfileDao,
                            app.container.productRepository,
                            app.container.tokenRepository
                        )
                    )
                    ChatroomScreen(
                        contactPubkey = roomId,
                        viewModel = chatViewModel,
                        onBack = { navController.popBackStack() },
                        onProfileClick = { pubkey ->
                            navController.navigate("merchant/$pubkey")
                        }
                    )
                }
                composable(Screen.Wallet.route) { 
                    WalletScreen(
                        onSendToken = { navController.navigate("tokens/send") },
                        onProducerClick = { pubkey -> navController.navigate("merchant/$pubkey") }
                    )
                }
                composable("tokens/send") {
                    SendTokenScreen(onBack = { navController.popBackStack() })
                }
                composable(Screen.Products.route) { 
                    val productViewModel: ProductViewModel = viewModel(
                        factory = ProductViewModel.provideFactory(
                            app.container.productRepository, 
                            app.container.secureStorage
                        )
                    )
                    ProductManagerScreen(
                        viewModel = productViewModel,
                        onNavigateToCategories = { navController.navigate("product-categories") }
                    ) 
                }
                composable("product-categories") {
                    val productViewModel: ProductViewModel = viewModel(
                        factory = ProductViewModel.provideFactory(
                            app.container.productRepository, 
                            app.container.secureStorage
                        )
                    )
                    ProductCategoriesScreen(
                        viewModel = productViewModel,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Profile.route) { 
                    MerchantProfileScreen(
                        pubkey = viewModel.ownPubkey ?: "",
                        ownPubkey = viewModel.ownPubkey,
                        onBack = { /* No back on main tab */ },
                        onCart = { navController.navigate("cart") },
                        onEdit = { navController.navigate("profile/edit") },
                        onMessage = { pubkey -> navController.navigate("chat/$pubkey") }
                    ) 
                }
                composable("profile/edit") {
                    ProfileEditScreen(onBack = { navController.popBackStack() })
                }
                composable("relays") { RelayManagementScreen() }
                composable("backups") { BackupScreen(onBack = { navController.popBackStack() }) }
                composable("pending") {
                    val pendingViewModel: PendingEventsViewModel = viewModel(
                        factory = PendingEventsViewModel.provideFactory(
                            app.container.relayPoolManager,
                            app.container.nostrSyncManager,
                            app.container.offlineQueueDao
                        )
                    )
                    PendingEventsScreen(
                        viewModel = pendingViewModel,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("private-key") {
                    val privateKeyViewModel: PrivateKeyViewModel = viewModel(factory = PrivateKeyViewModel.provideFactory(app.container.keyDao, app.container.secureStorage))
                    PrivateKeyScreen(viewModel = privateKeyViewModel, onContinue = { navController.popBackStack() })
                }
                composable("onboarding") {
                    val onboardingViewModel: OnboardingViewModel = viewModel(
                        factory = OnboardingViewModel.provideFactory(
                            app.container.keyDao,
                            app.container.userProfileDao,
                            app.container.productDao,
                            app.container.tokenDao,
                            app.container.reviewDao,
                            app.container.secureStorage
                        )
                    )
                    OnboardingScreen(
                        onSuccess = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        },
                        viewModel = onboardingViewModel,
                        currentLanguage = viewModel.currentLanguage,
                        onLanguageChange = { lang ->
                            viewModel.updateLanguage(lang)
                            (context as? android.app.Activity)?.recreate()
                        }
                    )
                }
                
                composable(Screen.MerchantProfile.route) { backStackEntry ->
                    val pubkey = backStackEntry.arguments?.getString("pubkey") ?: ""
                    MerchantProfileScreen(
                        pubkey = pubkey,
                        ownPubkey = viewModel.ownPubkey,
                        onBack = { navController.popBackStack() },
                        onCart = { navController.navigate("cart") },
                        onEdit = { navController.navigate("profile/edit") },
                        onMessage = { pk -> navController.navigate("chat/$pk") }
                    )
                }
                
                composable(Screen.Cart.route) {
                    CartScreen(
                        onBack = { navController.popBackStack() },
                        onCheckout = { /* Handle checkout */ }
                    )
                }
            }
        }

        if (showBottomBar) {
            AppMenuButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 18.dp, end = 20.dp),
                onOpenMenu = { isMenuOpen = true }
            )
        }

        AppMenuDialog(
            isOpen = isMenuOpen,
            accountMode = viewModel.accountMode,
            currentLanguage = viewModel.currentLanguage,
            updateResult = viewModel.updateResult,
            updateError = viewModel.updateError,
            isCheckingUpdate = viewModel.isCheckingUpdate,
            onAccountModeChange = { mode -> viewModel.updateAccountMode(mode) },
            onLanguageChange = { lang ->
                viewModel.updateLanguage(lang)
                (context as? android.app.Activity)?.recreate()
            },
            onCheckUpdate = { viewModel.checkForUpdates() },
            onDownloadUpdate = { viewModel.downloadUpdate() },
            onClearUpdateResult = { viewModel.clearUpdateResult() },
            onOpenUrl = { url ->
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                context.startActivity(intent)
            },
            onDismiss = { isMenuOpen = false },
            onNavigate = { route ->
                if (route == "logout") {
                    isLogoutDialogOpen = true
                } else {
                    navController.navigate(route)
                }
            }
        )

        if (isLogoutDialogOpen) {
            LogoutConfirmationDialog(
                onDismiss = { isLogoutDialogOpen = false },
                onConfirm = {
                    isLogoutDialogOpen = false
                    viewModel.logout()
                },
                onBackup = {
                    isLogoutDialogOpen = false
                    navController.navigate(Screen.Backups.route)
                }
            )
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    val kianColors = KianTheme.colors
    Surface(color = kianColors.canvas) {
        Text(text = name, modifier = Modifier.padding(16.dp), color = kianColors.ink)
    }
}
