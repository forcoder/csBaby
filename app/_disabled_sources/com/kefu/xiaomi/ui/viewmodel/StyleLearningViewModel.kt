package com.kefu.xiaomi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kefu.xiaomi.data.model.UserStyleProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StyleLearningUiState(
    val profile: UserStyleProfile = UserStyleProfile(),
    val isLearning: Boolean = false,
    val learningProgress: Float = 0f,
    val commonPhrases: List<String> = listOf(
        "您好，感谢您的咨询",
        "祝您生活愉快",
        "如有其他问题随时联系",
        "期待为您服务"
    ),
    val avoidPhrases: List<String> = listOf(
        "不知道",
        "你自己看",
        "这不是我的责任"
    )
)

@HiltViewModel
class StyleLearningViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(StyleLearningUiState())
    val uiState: StateFlow<StyleLearningUiState> = _uiState.asStateFlow()

    init {
        loadProfile()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            // 模拟加载用户风格画像
            val profile = UserStyleProfile(
                userId = "default",
                formalityLevel = 0.6f,
                enthusiasmLevel = 0.7f,
                professionalismLevel = 0.8f,
                wordCountPreference = 50,
                commonPhrases = listOf(
                    "您好，感谢您的咨询",
                    "祝您生活愉快",
                    "如有其他问题随时联系",
                    "期待为您服务"
                ),
                avoidPhrases = listOf(
                    "不知道",
                    "你自己看",
                    "这不是我的责任"
                ),
                learningSamples = 128,
                accuracyScore = 0.85f,
                lastTrained = System.currentTimeMillis() - 86400000
            )
            _uiState.value = _uiState.value.copy(profile = profile)
        }
    }

    fun updateFormalityLevel(level: Float) {
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile.copy(formalityLevel = level)
        )
    }

    fun updateEnthusiasmLevel(level: Float) {
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile.copy(enthusiasmLevel = level)
        )
    }

    fun updateProfessionalismLevel(level: Float) {
        _uiState.value = _uiState.value.copy(
            profile = _uiState.value.profile.copy(professionalismLevel = level)
        )
    }

    fun addCommonPhrase(phrase: String) {
        if (phrase.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                commonPhrases = _uiState.value.commonPhrases + phrase
            )
        }
    }

    fun removeCommonPhrase(phrase: String) {
        _uiState.value = _uiState.value.copy(
            commonPhrases = _uiState.value.commonPhrases.filter { it != phrase }
        )
    }

    fun addAvoidPhrase(phrase: String) {
        if (phrase.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                avoidPhrases = _uiState.value.avoidPhrases + phrase
            )
        }
    }

    fun removeAvoidPhrase(phrase: String) {
        _uiState.value = _uiState.value.copy(
            avoidPhrases = _uiState.value.avoidPhrases.filter { it != phrase }
        )
    }

    fun startLearning() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLearning = true, learningProgress = 0f)
            // 模拟学习过程
            for (i in 1..10) {
                kotlinx.coroutines.delay(500)
                _uiState.value = _uiState.value.copy(learningProgress = i / 10f)
            }
            _uiState.value = _uiState.value.copy(
                isLearning = false,
                learningProgress = 0f,
                profile = _uiState.value.profile.copy(
                    learningSamples = _uiState.value.profile.learningSamples + 10,
                    accuracyScore = (_uiState.value.profile.accuracyScore + 0.02f).coerceAtMost(1f),
                    lastTrained = System.currentTimeMillis()
                )
            )
        }
    }

    fun saveProfile() {
        viewModelScope.launch {
            // 保存到数据库
        }
    }
}
