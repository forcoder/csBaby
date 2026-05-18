package com.csbaby.kefu.infrastructure.error

import android.content.Context
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统一错误处理框架
 * 负责处理应用中的各种错误情况
 */
@Singleton
class ErrorHandler @Inject constructor(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "ErrorHandler"
    }
    
    /**
     * 错误类型
     */
    sealed class AppError(val message: String, val cause: Throwable? = null) {
        // 网络错误
        class NetworkError(message: String, cause: Throwable? = null) : AppError(message, cause)
        
        // API错误
        class ApiError(message: String, val errorCode: Int? = null, cause: Throwable? = null) : AppError(message, cause)
        
        // 数据库错误
        class DatabaseError(message: String, cause: Throwable? = null) : AppError(message, cause)
        
        // 权限错误
        class PermissionError(message: String, cause: Throwable? = null) : AppError(message, cause)
        
        // 业务逻辑错误
        class BusinessError(message: String, cause: Throwable? = null) : AppError(message, cause)
        
        // 未知错误
        class UnknownError(message: String, cause: Throwable? = null) : AppError(message, cause)
    }
    
    /**
     * 处理错误
     */
    fun handleError(error: AppError) {
        when (error) {
            is AppError.NetworkError -> handleNetworkError(error)
            is AppError.ApiError -> handleApiError(error)
            is AppError.DatabaseError -> handleDatabaseError(error)
            is AppError.PermissionError -> handlePermissionError(error)
            is AppError.BusinessError -> handleBusinessError(error)
            is AppError.UnknownError -> handleUnknownError(error)
        }
    }
    
    /**
     * 处理网络错误
     */
    private fun handleNetworkError(error: AppError.NetworkError) {
        Timber.e(error.cause, "网络错误: ${error.message}")
        // 可以在这里显示网络错误提示，或者重试逻辑
    }
    
    /**
     * 处理API错误
     */
    private fun handleApiError(error: AppError.ApiError) {
        Timber.e(error.cause, "API错误 (${error.errorCode}): ${error.message}")
        // 可以根据错误代码进行不同的处理
    }
    
    /**
     * 处理数据库错误
     */
    private fun handleDatabaseError(error: AppError.DatabaseError) {
        Timber.e(error.cause, "数据库错误: ${error.message}")
        // 可以在这里进行数据库恢复操作
    }
    
    /**
     * 处理权限错误
     */
    private fun handlePermissionError(error: AppError.PermissionError) {
        Timber.e(error.cause, "权限错误: ${error.message}")
        // 可以在这里引导用户授予权限
    }
    
    /**
     * 处理业务逻辑错误
     */
    private fun handleBusinessError(error: AppError.BusinessError) {
        Timber.e(error.cause, "业务逻辑错误: ${error.message}")
        // 可以在这里显示业务错误提示
    }
    
    /**
     * 处理未知错误
     */
    private fun handleUnknownError(error: AppError.UnknownError) {
        Timber.e(error.cause, "未知错误: ${error.message}")
        // 可以在这里显示通用错误提示
    }
    
    /**
     * 将Throwable转换为AppError
     */
    fun convertToAppError(throwable: Throwable): AppError {
        return when (throwable) {
            is java.net.SocketTimeoutException, 
            is java.net.UnknownHostException, 
            is java.net.ConnectException -> {
                AppError.NetworkError("网络连接失败，请检查网络设置", throwable)
            }
            is retrofit2.HttpException -> {
                val errorCode = throwable.code()
                val errorMessage = "API请求失败: ${throwable.message()}"
                AppError.ApiError(errorMessage, errorCode, throwable)
            }
            is android.database.sqlite.SQLiteException -> {
                AppError.DatabaseError("数据库操作失败", throwable)
            }
            is SecurityException -> {
                AppError.PermissionError("权限不足", throwable)
            }
            else -> {
                AppError.UnknownError(throwable.message ?: "未知错误", throwable)
            }
        }
    }
    
    /**
     * 安全执行代码块，捕获并处理错误
     */
    fun <T> safeExecute(block: () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            val appError = convertToAppError(e)
            handleError(appError)
            Result.failure(e)
        }
    }
    
    /**
     * 安全执行挂起函数，捕获并处理错误
     */
    suspend fun <T> safeExecuteSuspend(block: suspend () -> T): Result<T> {
        return try {
            Result.success(block())
        } catch (e: Exception) {
            val appError = convertToAppError(e)
            handleError(appError)
            Result.failure(e)
        }
    }
}