package com.kefu.xiaomi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kefu.xiaomi.ui.viewmodel.StyleLearningViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyleLearningScreen(
    viewModel: StyleLearningViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var newPhrase by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("风格学习") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.saveProfile() },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("保存")
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
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 学习进度卡片
            item {
                LearningProgressCard(
                    learningSamples = uiState.profile.learningSamples,
                    accuracyScore = uiState.profile.accuracyScore,
                    lastTrained = uiState.profile.lastTrained,
                    isLearning = uiState.isLearning,
                    learningProgress = uiState.learningProgress,
                    onStartLearning = { viewModel.startLearning() }
                )
            }

            // 风格参数调整
            item {
                StyleParametersCard(
                    formalityLevel = uiState.profile.formalityLevel,
                    enthusiasmLevel = uiState.profile.enthusiasmLevel,
                    professionalismLevel = uiState.profile.professionalismLevel,
                    onFormalityChange = { viewModel.updateFormalityLevel(it) },
                    onEnthusiasmChange = { viewModel.updateEnthusiasmLevel(it) },
                    onProfessionalismChange = { viewModel.updateProfessionalismLevel(it) }
                )
            }

            // 常用短语
            item {
                PhrasesCard(
                    title = "常用短语",
                    subtitle = "AI会优先使用这些短语",
                    icon = Icons.Default.Favorite,
                    phrases = uiState.commonPhrases,
                    onAddPhrase = { viewModel.addCommonPhrase(it) },
                    onRemovePhrase = { viewModel.removeCommonPhrase(it) }
                )
            }

            // 避免短语
            item {
                PhrasesCard(
                    title = "避免短语",
                    subtitle = "AI会避免使用这些短语",
                    icon = Icons.Default.Block,
                    phrases = uiState.avoidPhrases,
                    onAddPhrase = { viewModel.addAvoidPhrase(it) },
                    onRemovePhrase = { viewModel.removeAvoidPhrase(it) },
                    isAvoid = true
                )
            }

            // 风格预览
            item {
                StylePreviewCard(
                    formalityLevel = uiState.profile.formalityLevel,
                    enthusiasmLevel = uiState.profile.enthusiasmLevel,
                    professionalismLevel = uiState.profile.professionalismLevel
                )
            }
        }
    }
}

@Composable
private fun LearningProgressCard(
    learningSamples: Int,
    accuracyScore: Float,
    lastTrained: Long,
    isLearning: Boolean,
    learningProgress: Float,
    onStartLearning: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "学习进度",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (isLearning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLearning) {
                LinearProgressIndicator(
                    progress = { learningProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "正在学习... ${(learningProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatItem(
                        icon = Icons.Default.LibraryBooks,
                        label = "学习样本",
                        value = "$learningSamples 条"
                    )
                    StatItem(
                        icon = Icons.Default.Speed,
                        label = "准确率",
                        value = "${(accuracyScore * 100).toInt()}%"
                    )
                    StatItem(
                        icon = Icons.Default.Schedule,
                        label = "上次训练",
                        value = formatTime(lastTrained)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onStartLearning,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLearning
            ) {
                Icon(
                    Icons.Default.School,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (isLearning) "训练中..." else "开始训练")
            }
        }
    }
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun StyleParametersCard(
    formalityLevel: Float,
    enthusiasmLevel: Float,
    professionalismLevel: Float,
    onFormalityChange: (Float) -> Unit,
    onEnthusiasmChange: (Float) -> Unit,
    onProfessionalismChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "风格参数",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "调整AI回复的风格特征",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

            StyleSlider(
                label = "正式度",
                description = "调整回复的正式程度",
                value = formalityLevel,
                onValueChange = onFormalityChange,
                lowLabel = "随意",
                highLabel = "正式"
            )

            Spacer(modifier = Modifier.height(12.dp))

            StyleSlider(
                label = "热情度",
                description = "调整回复的热情程度",
                value = enthusiasmLevel,
                onValueChange = onEnthusiasmChange,
                lowLabel = "冷淡",
                highLabel = "热情"
            )

            Spacer(modifier = Modifier.height(12.dp))

            StyleSlider(
                label = "专业度",
                description = "调整回复的专业程度",
                value = professionalismLevel,
                onValueChange = onProfessionalismChange,
                lowLabel = "通俗",
                highLabel = "专业"
            )
        }
    }
}

@Composable
private fun StyleSlider(
    label: String,
    description: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    lowLabel: String,
    highLabel: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${(value * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lowLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Slider(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            )
            Text(
                text = highLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhrasesCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    phrases: List<String>,
    onAddPhrase: (String) -> Unit,
    onRemovePhrase: (String) -> Unit,
    isAvoid: Boolean = false
) {
    var newPhrase by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (isAvoid)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 添加新短语
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newPhrase,
                    onValueChange = { newPhrase = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入新短语") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        onAddPhrase(newPhrase)
                        newPhrase = ""
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "添加",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 短语列表
            phrases.forEach { phrase ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = phrase,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onRemovePhrase(phrase) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "删除",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StylePreviewCard(
    formalityLevel: Float,
    enthusiasmLevel: Float,
    professionalismLevel: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Preview,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "风格预览",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            val formalityText = when {
                formalityLevel < 0.3f -> "嘿，有什么需要帮忙的吗？"
                formalityLevel < 0.7f -> "您好，有什么可以帮您的？"
                else -> "尊敬的用户，请问有什么需要为您效劳的？"
            }

            val enthusiasmText = when {
                enthusiasmLevel < 0.3f -> "好的。"
                enthusiasmLevel < 0.7f -> "好的，我会尽快为您处理！"
                else -> "太棒了！我马上为您处理，期待为您带来更好的服务！"
            }

            val professionalismText = when {
                professionalismLevel < 0.3f -> "这个嘛，我也不太清楚，你问问别人吧"
                professionalismLevel < 0.7f -> "根据您的问题，这可能是由于系统设置导致的"
                else -> "根据我们系统日志分析，该问题属于配置参数异常，建议您检查相关设置项"
            }

            Text(
                text = "正式度示例：",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = formalityText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "热情度示例：",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = enthusiasmText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "专业度示例：",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                text = professionalismText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60000 -> "刚刚"
        diff < 3600000 -> "${diff / 60000}分钟前"
        diff < 86400000 -> "${diff / 3600000}小时前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}
