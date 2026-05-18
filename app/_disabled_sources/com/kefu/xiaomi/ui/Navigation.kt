package com.kefu.xiaomi.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Home : Screen(
        route = "home",
        title = "首页",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object KnowledgeBase : Screen(
        route = "knowledge_base",
        title = "知识库",
        selectedIcon = Icons.Filled.LibraryBooks,
        unselectedIcon = Icons.Outlined.LibraryBooks
    )

    data object ModelConfig : Screen(
        route = "model_config",
        title = "模型配置",
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome
    )

    data object Profile : Screen(
        route = "profile",
        title = "个人中心",
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person
    )

    companion object {
        val bottomNavItems = listOf(Home, KnowledgeBase, ModelConfig, Profile)
    }
}

sealed class SubScreen(val route: String) {
    data object Settings : SubScreen("settings")
    data object ScenarioConfig : SubScreen("scenario_config")
    data object StyleLearning : SubScreen("style_learning")
    data object AppSelection : SubScreen("app_selection")
    data object AddRule : SubScreen("add_rule")
    data object EditRule : SubScreen("edit_rule/{ruleId}") {
        fun createRoute(ruleId: Long) = "edit_rule/$ruleId"
    }
    data object AddModel : SubScreen("add_model")
    data object EditModel : SubScreen("edit_model/{modelId}") {
        fun createRoute(modelId: Long) = "edit_model/$modelId"
    }
}
