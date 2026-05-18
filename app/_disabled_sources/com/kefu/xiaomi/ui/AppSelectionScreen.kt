package com.kefu.xiaomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.kefu.xiaomi.data.model.AppConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(
    onNavigateBack: () -> Unit = {}
) {
    var apps by remember { mutableStateOf(listOf(
        AppConfig("com.tencent.mm", "微信", isMonitored = true),
        AppConfig("com.alibaba.android.rimet", "钉钉", isMonitored = true),
        AppConfig("com.tencent.mobileqq", "QQ", isMonitored = false),
        AppConfig("com.sina.weibo", "微博", isMonitored = false),
        AppConfig("com.taobao.taobao4", "淘宝", isMonitored = false),
        AppConfig("com.jd.lib", "京东", isMonitored = false),
        AppConfig("com.baidu.tieba", "贴吧", isMonitored = false),
        AppConfig("io.dcloud.H5B8462BE", "小红书", isMonitored = false)
    ))}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("选择监听应用") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 提示信息
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "选择需要监听消息的应用，开启后将自动检测新消息并生成回复建议",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // 应用列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(apps) { app ->
                    AppSelectionItem(
                        app = app,
                        onToggle = {
                            apps = apps.map {
                                if (it.packageName == app.packageName) {
                                    it.copy(isMonitored = !it.isMonitored)
                                } else {
                                    it
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppSelectionItem(
    app: AppConfig,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle),
        colors = CardDefaults.cardColors(
            containerColor = if (app.isMonitored)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            if (app.isMonitored)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = null,
                        tint = if (app.isMonitored)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Switch(
                checked = app.isMonitored,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
