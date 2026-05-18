package com.csbaby.kefu.data.remote.dto

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("platform") val platform: String = "android",
    @SerializedName("app_version") val appVersion: String = "",
    @SerializedName("name") val name: String = ""
)

data class RegisterResponse(
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0
)

// User Authentication DTOs
data class UserRegisterRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String,
    @SerializedName("name") val name: String = "",
    @SerializedName("platform") val platform: String = "android"
)

data class UserLoginRequest(
    @SerializedName("phone") val phone: String,
    @SerializedName("password") val password: String
)

data class ChangePasswordRequest(
    @SerializedName("old_password") val oldPassword: String,
    @SerializedName("new_password") val newPassword: String
)

data class ChangePasswordResponse(
    @SerializedName("status") val status: String = ""
)

data class AuthResponse(
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("expires_in") val expiresIn: Int = 0
)

data class HeartbeatResponse(
    @SerializedName("status") val status: String = ""
)
