package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察 OTA 错误消息 UseCase
 */
class ObserveOtaErrorUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    operator fun invoke(): Flow<String?> = otaManager.errorMessage
}