package com.kefu.xiaomi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefu.xiaomi.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToScenarioConfig: () -> Unit = {},
    onNavigateToStyleLearning: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 外观设置
            item {
                SettingsSection(title = "外观") {
                    SettingsSwitchItem(
                        title = "深色主题",
                        subtitle = "使用深色配色方案",
                        icon = Icons.Default.DarkMode,
                        checked = uiState.isDarkTheme,
                        onCheckedChange = { viewModel.toggleDarkTheme() }
                    )
                }
            }

            // 通知设置
            item {
                SettingsSection(title = "通知") {
                    SettingsSwitchItem(
                        title = "消息通知",
                        subtitle = "收到新消息时显示通知",
                        icon = Icons.Default.Notifications,
                        checked = uiState.notificationEnabled,
                        onCheckedChange = { viewModel.toggleNotification() }
                    )
                    SettingsSwitchItem(
                        title = "声音提醒",
                        subtitle = "收到新消息时播放提示音",
                        icon = Icons.Default.VolumeUp,
                        checked = uiState.soundEnabled,
                        onCheckedChange = { viewModel.toggleSound() }
                    )
                    SettingsSwitchItem(
                        title = "震动提醒",
                        subtitle = "收到新消息时震动",
                        icon = Icons.Default.Vibration,
                        checked = uiState.vibrationEnabled,
                        onCheckedChange = { viewModel.toggleVibration() }
                    )
                }
            }

            // 回复设置
            item {
                SettingsSection(title = "回复设置") {
                    SettingsSwitchItem(
                        title = "自动发送",
                        subtitle = "自动发送AI生成的回复（需确认）",
                        icon = Icons.Default.Send,
                        checked = uiState.autoSendEnabled,
                        onCheckedChange = { viewModel.toggleAutoSend() }
                    )
                    SettingsSwitchItem(
                        title = "显示预览窗口",
                        subtitle = "在回复前显示预览窗口",
                        icon = Icons.Default.Window,
                        checked = uiState.showPreviewWindow,
                        onCheckedChange = { viewModel.togglePreviewWindow() }
                    )
                    SettingsSliderItem(
                        title = "回复延迟",
                        subtitle = "收到消息后延迟 ${uiState.replyDelay} 秒再生成回复",
                        icon = Icons.Default.Timer,
                        value = uiState.replyDelay.toFloat(),
                        valueRange = 0f..10f,
                        steps = 9,
                        onValueChange = { viewModel.setReplyDelay(it.toInt()) }
                    )
                }
            }

            // 场景配置
            item {
                SettingsSection(title = "功能配置") {
                    SettingsClickItem(
                        title = "场景配置",
                        subtitle = "配置关键词适用的场景",
                        icon = Icons.Default.Layers,
                        onClick = onNavigateToScenarioConfig
                    )
                    SettingsClickItem(
                        title = "风格学习",
                        subtitle = "调整AI回复风格参数",
                        icon = Icons.Default.Psychology,
                        onClick = onNavigateToStyleLearning
                    )
                }
            }

            // 数据管理
            item {
                SettingsSection(title = "数据管理") {
                    SettingsSliderItem(
                        title = "历史记录保存天数",
                        subtitle = "保留最近 ${uiState.maxHistoryDays} 天的记录",
                        icon = Icons.Default.History,
                        value = uiState.maxHistoryDays.toFloat(),
                        valueRange = 7f..90f,
                        steps = 82,
                        onValueChange = { viewModel.setMaxHistoryDays(it.toInt()) }
                    )
                    SettingsClickItem(
                        title = "导出数据",
                        subtitle = "导出知识库和设置",
                        icon = Icons.Default.Upload,
                        onClick = { /* 导出数据 */ }
                    )
                    SettingsClickItem(
                        title = "导入数据",
                        subtitle = "从文件导入知识库",
                        icon = Icons.Default.Download,
                        onClick = { /* 导入数据 */ }
                    )
                }
            }

            // 关于
            item {
                SettingsSection(title = "关于") {
                    SettingsClickItem(
                        title = "版本信息",
                        subtitle = "v1.0.0",
                        icon = Icons.Default.Info,
                        onClick = { }
                    )
                    SettingsClickItem(
                        title = "隐私政策",
                        subtitle = "查看隐私政策",
                        icon = Icons.Default.Security,
                        onClick = { }
                    )
                    SettingsClickItem(
                        title = "用户协议",
                        subtitle = "查看用户协议",
                        icon = Icons.Default.Description,
                        onClick = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
}

@Composable
private fun SettingsSwitchItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingsClickItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsSliderItem(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    onValueChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
