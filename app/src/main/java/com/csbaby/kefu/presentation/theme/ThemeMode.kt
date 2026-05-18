package com.csbaby.kefu.presentation.theme

/**
 * 主题模式枚举
 */
enum class ThemeMode(val displayName: String, val value: String) {
    LIGHT("浅色模式", "light"),
    DARK("深色模式", "dark"),
    SYSTEM("跟随系统", "system");

    companion object {
        fun fromValue(value: String): ThemeMode {
            return values().find { it.value == value } ?: SYSTEM
        }
    }
}
