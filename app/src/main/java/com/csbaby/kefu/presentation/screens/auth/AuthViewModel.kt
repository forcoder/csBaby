package com.csbaby.kefu.presentation.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.csbaby.kefu.data.local.PreferencesManager
import com.csbaby.kefu.data.remote.UserAuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val phoneNumber: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val name: String = "",
    val errorMessage: String = "",
    val loginSuccess: Boolean = false,
    val registerSuccess: Boolean = false
)

sealed class AuthEvent {
    data class UpdatePhoneNumber(val phone: String) : AuthEvent()
    data class UpdatePassword(val password: String) : AuthEvent()
    data class UpdateConfirmPassword(val confirmPassword: String) : AuthEvent()
    data class UpdateName(val name: String) : AuthEvent()
    object Login : AuthEvent()
    object Register : AuthEvent()
    object ClearError : AuthEvent()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val userAuthManager: UserAuthManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.UpdatePhoneNumber -> {
                _uiState.value = _uiState.value.copy(phoneNumber = event.phone)
            }
            is AuthEvent.UpdatePassword -> {
                _uiState.value = _uiState.value.copy(password = event.password)
            }
            is AuthEvent.UpdateConfirmPassword -> {
                _uiState.value = _uiState.value.copy(confirmPassword = event.confirmPassword)
            }
            is AuthEvent.UpdateName -> {
                _uiState.value = _uiState.value.copy(name = event.name)
            }
            is AuthEvent.Login -> {
                viewModelScope.launch {
                    login()
                }
            }
            is AuthEvent.Register -> {
                viewModelScope.launch {
                    register()
                }
            }
            is AuthEvent.ClearError -> {
                _uiState.value = _uiState.value.copy(errorMessage = "")
            }
        }
    }

    private suspend fun login() {
        val state = _uiState.value
        if (state.phoneNumber.isBlank() || state.password.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请输入手机号和密码")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = "")

        try {
            val response = userAuthManager.login(state.phoneNumber, state.password)
            // 登录成功，导航到主界面
            _uiState.value = state.copy(
                isLoading = false,
                loginSuccess = true,
                phoneNumber = "",
                password = ""
            )
        } catch (e: Exception) {
            _uiState.value = state.copy(
                isLoading = false,
                errorMessage = e.message ?: "登录失败"
            )
        }
    }

    private suspend fun register() {
        val state = _uiState.value
        if (state.phoneNumber.isBlank() || state.password.isBlank() || state.confirmPassword.isBlank()) {
            _uiState.value = state.copy(errorMessage = "请填写完整信息")
            return
        }

        if (state.password != state.confirmPassword) {
            _uiState.value = state.copy(errorMessage = "两次输入的密码不一致")
            return
        }

        _uiState.value = state.copy(isLoading = true, errorMessage = "")

        try {
            val response = userAuthManager.register(state.phoneNumber, state.password, state.name)
            // 注册成功，自动登录
            _uiState.value = state.copy(
                isLoading = false,
                registerSuccess = true,
                phoneNumber = "",
                password = "",
                confirmPassword = "",
                name = "",
                errorMessage = ""
            )
        } catch (e: Exception) {
            _uiState.value = state.copy(
                isLoading = false,
                errorMessage = e.message ?: "注册失败"
            )
        }
    }
}