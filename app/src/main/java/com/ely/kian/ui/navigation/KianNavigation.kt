package com.ely.kian.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ely.kian.ui.components.AppMenuButton
import com.ely.kian.ui.components.AppMenuDialog
import com.ely.kian.ui.screens.chats.ChatsScreen
import com.ely.kian.ui.screens.home.HomeScreen
import com.ely.kian.ui.screens.products.ProductManagerScreen
import com.ely.kian.ui.screens.wallet.WalletScreen
import com.ely.kian.ui.screens.onboarding.PrivateKeyScreen
import com.ely.kian.ui.screens.merchant.MerchantProfileScreen
import com.ely.kian.ui.screens.chat.ChatRoomScreen
import com.ely.kian.ui.screens.cart.CartScreen
import com.ely.kian.ui.theme.KianTheme

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Wallet : Screen("wallet", "Wallet", Icons.Default.Wallet)
    object Products : Screen("products", "Products", Icons.Default.Inventory)
    object Chats : Screen("chats", "Chats", Icons.Default.Chat)
    
    // Sub-screens
    object MerchantProfile : Screen("merchant/{pubkey}", "Merchant", Icons.Default.Person)
    object ChatRoom : Screen("chat/{pubkey}", "Chat", Icons.Default.Chat)
    object Cart : Screen("cart", "Cart", Icons.Default.ShoppingCart)
}

val items = listOf(
    Screen.Home,
    Screen.Wallet,
    Screen.Products,
    Screen.Chats,
)

@Composable
fun KianScaffold() {
    val navController = rememberNavController()
    var isMenuOpen by remember { mutableStateOf(false) }
    val kianColors = KianTheme.colors
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val showBottomBar = items.any { it.route == currentRoute }

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
                                icon = { Icon(screen.icon, contentDescription = null) },
                                label = { Text(screen.label) },
                                selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
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
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(Screen.Home.route) { 
                    HomeScreen(onMerchantClick = { pubkey ->
                        navController.navigate("merchant/$pubkey")
                    }) 
                }
                composable(Screen.Wallet.route) { WalletScreen() }
                composable(Screen.Products.route) { ProductManagerScreen() }
                composable(Screen.Chats.route) { ChatsScreen() }
                
                composable("profile") { PlaceholderScreen("My Profile Screen") }
                composable("relays") { PlaceholderScreen("Relay Management Screen") }
                composable("pending") { PlaceholderScreen("Pending Events Screen") }
                composable("private-key") { PrivateKeyScreen(privateKey = "nsec1...", onContinue = { navController.popBackStack() }) }
                
                composable(Screen.MerchantProfile.route) { backStackEntry ->
                    val pubkey = backStackEntry.arguments?.getString("pubkey") ?: ""
                    MerchantProfileScreen(
                        pubkey = pubkey,
                        onBack = { navController.popBackStack() },
                        onChat = { navController.navigate("chat/$pubkey") },
                        onCart = { navController.navigate("cart") }
                    )
                }
                
                composable(Screen.ChatRoom.route) { backStackEntry ->
                    val pubkey = backStackEntry.arguments?.getString("pubkey") ?: ""
                    ChatRoomScreen(
                        peerPubkey = pubkey,
                        onBack = { navController.popBackStack() },
                        onCart = { navController.navigate("cart") }
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
                    .padding(top = 18.dp, end = 20.dp),
                onOpenMenu = { isMenuOpen = true }
            )
        }

        AppMenuDialog(
            isOpen = isMenuOpen,
            onDismiss = { isMenuOpen = false },
            onNavigate = { route ->
                navController.navigate(route)
            }
        )
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    val kianColors = KianTheme.colors
    Surface(color = kianColors.canvas) {
        Text(text = name, modifier = Modifier.padding(16.dp), color = kianColors.ink)
    }
}
