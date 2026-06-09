package com.ely.kian.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ely.kian.ui.screens.chats.ChatsScreen
import com.ely.kian.ui.screens.home.HomeScreen
import com.ely.kian.ui.screens.products.ProductManagerScreen
import com.ely.kian.ui.screens.wallet.WalletScreen
import com.ely.kian.ui.theme.Ink
import com.ely.kian.ui.theme.Line

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object Wallet : Screen("wallet", "Wallet", Icons.Default.Wallet)
    object Products : Screen("products", "Products", Icons.Default.Inventory)
    object Chats : Screen("chats", "Chats", Icons.Default.Chat)
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
    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = Ink,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
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
                            selectedIconColor = Ink,
                            selectedTextColor = Ink,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            unselectedIconColor = Ink.copy(alpha = 0.6f),
                            unselectedTextColor = Ink.copy(alpha = 0.6f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Wallet.route) { WalletScreen() }
            composable(Screen.Products.route) { ProductManagerScreen() }
            composable(Screen.Chats.route) { ChatsScreen() }
        }
    }
}

@Composable
fun PlaceholderScreen(name: String) {
    Surface {
        Text(text = name, modifier = Modifier.padding(16.dp))
    }
}
