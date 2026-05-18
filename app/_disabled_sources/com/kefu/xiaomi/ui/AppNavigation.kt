package com.kefu.xiaomi.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 检查是否在子页面（隐藏底部导航）
    val isSubScreen = currentDestination?.route in listOf(
        SubScreen.Settings.route,
        SubScreen.ScenarioConfig.route,
        SubScreen.StyleLearning.route,
        SubScreen.AppSelection.route
    )

    Scaffold(
        bottomBar = {
            if (!isSubScreen) {
                NavigationBar {
                    Screen.bottomNavItems.forEach { screen ->
                        val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                        NavigationBarItem(
                            icon = {
                                Icon(
                                    if (selected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = { Text(screen.title) },
                            selected = selected,
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
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // 主页面
            composable(Screen.Home.route) {
                MainScreen(
                    onNavigateToSettings = {
                        navController.navigate(SubScreen.Settings.route)
                    },
                    onNavigateToAppSelection = {
                        navController.navigate(SubScreen.AppSelection.route)
                    }
                )
            }

            composable(Screen.KnowledgeBase.route) {
                KnowledgeBaseScreen()
            }

            composable(Screen.ModelConfig.route) {
                ModelConfigScreen()
            }

            composable(Screen.Profile.route) {
                ProfileScreen(
                    onNavigateToSettings = {
                        navController.navigate(SubScreen.Settings.route)
                    },
                    onNavigateToScenarioConfig = {
                        navController.navigate(SubScreen.ScenarioConfig.route)
                    },
                    onNavigateToStyleLearning = {
                        navController.navigate(SubScreen.StyleLearning.route)
                    }
                )
            }

            // 子页面
            composable(SubScreen.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToScenarioConfig = {
                        navController.navigate(SubScreen.ScenarioConfig.route)
                    },
                    onNavigateToStyleLearning = {
                        navController.navigate(SubScreen.StyleLearning.route)
                    }
                )
            }

            composable(SubScreen.ScenarioConfig.route) {
                ScenarioConfigScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(SubScreen.StyleLearning.route) {
                StyleLearningScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            composable(SubScreen.AppSelection.route) {
                AppSelectionScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToScenarioConfig: () -> Unit,
    onNavigateToStyleLearning: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        com.kefu.xiaomi.ui.SettingsScreen(
            onNavigateBack = { },
            onNavigateToScenarioConfig = onNavigateToScenarioConfig,
            onNavigateToStyleLearning = onNavigateToStyleLearning
        )
    }
}
