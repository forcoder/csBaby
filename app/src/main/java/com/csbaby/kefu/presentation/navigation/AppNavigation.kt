package com.csbaby.kefu.presentation.navigation

import androidx.compose.foundation.layout.padding
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
import com.csbaby.kefu.presentation.screens.blacklist.BlacklistScreen
import com.csbaby.kefu.presentation.screens.home.HomeScreen
import com.csbaby.kefu.presentation.screens.knowledge.KnowledgeScreen
import com.csbaby.kefu.presentation.screens.model.ModelScreen
import com.csbaby.kefu.presentation.screens.profile.ProfileScreen

sealed class Screen(
    val route: String,
    val titleResId: Int,
    val icon: ImageVector
) {
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 10.dp
            ) {
                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(stringResource(screen.titleResId)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
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
            composable(Screen.Profile.route) { ProfileScreen(navController = navController) }
            composable("blacklist") {
                BlacklistScreen(onNavigateBack = { navController.popBackStack() })
            }
        }
    }
}
