package com.csbaby.kefu.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.csbaby.kefu.R
import com.csbaby.kefu.presentation.screens.auth.LoginScreen
import com.csbaby.kefu.presentation.screens.auth.RegisterScreen
import com.csbaby.kefu.presentation.screens.blacklist.BlacklistScreen
import com.csbaby.kefu.presentation.screens.home.HomeScreen
import com.csbaby.kefu.presentation.screens.knowledge.KnowledgeScreen
import com.csbaby.kefu.presentation.screens.model.ModelScreen
import com.csbaby.kefu.presentation.screens.profile.ProfileScreen

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector? = null
) {
    object Login : Screen("login", 0)
    object Register : Screen("register", 0)
    object Home : Screen("home", R.string.nav_home, Icons.Default.Home)
    object Knowledge : Screen("knowledge", R.string.nav_knowledge, Icons.Default.LibraryBooks)
    object Models : Screen("models", R.string.nav_models, Icons.Default.Settings)
    object Profile : Screen("profile", R.string.nav_profile, Icons.Default.Person)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Knowledge,
    Screen.Models,
    Screen.Profile
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavItems.map { it.route }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (showBottomBar) {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 8.dp)
            ) {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                screen.icon, 
                                contentDescription = stringResource(screen.titleResId),
                                modifier = Modifier.size(24.dp)
                            ) 
                        },
                        label = { 
                            Text(
                                stringResource(screen.titleResId),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                                    androidx.compose.ui.text.font.FontWeight.SemiBold
                                } else {
                                    androidx.compose.ui.text.font.FontWeight.Normal
                                }
                            ) 
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = "auth_check",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("auth_check") {
                // 这里应该检查用户是否已登录，然后决定导航到登录页还是主页
                // 为简化，我们先导航到登录页
                navController.navigate("login") {
                    popUpTo("auth_check") { inclusive = true }
                }
            }

            composable("login") {
                LoginScreen(
                    onNavigateToRegister = { navController.navigate("register") },
                    onNavigateToMain = {
                        navController.navigate("main_app") {
                            popUpTo("auth_check") { inclusive = true }
                        }
                    }
                )
            }

            composable("register") {
                RegisterScreen(
                    onNavigateToLogin = { navController.popBackStack() },
                    onNavigateToMain = {
                        navController.navigate("main_app") {
                            popUpTo("auth_check") { inclusive = true }
                        }
                    }
                )
            }

            composable("main_app") {
                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .padding(horizontal = 8.dp)
                                .padding(bottom = 8.dp)
                        ) {
                            bottomNavItems.forEach { screen ->
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            screen.icon!!,
                                            contentDescription = stringResource(screen.titleResId),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            stringResource(screen.titleResId),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (currentDestination?.hierarchy?.any { it.route == screen.route } == true) {
                                                androidx.compose.ui.text.font.FontWeight.SemiBold
                                            } else {
                                                androidx.compose.ui.text.font.FontWeight.Normal
                                            }
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                                    onClick = {
                                        navController.navigate(screen.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
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
                        composable(Screen.Knowledge.route) { KnowledgeScreen() }
                        composable(Screen.Models.route) { ModelScreen() }
                        composable(Screen.Profile.route) {
                            ProfileScreen(
                                onNavigateToBlacklist = {
                                    navController.navigate("blacklist")
                                }
                            )
                        }
                        composable("blacklist") {
                            BlacklistScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
