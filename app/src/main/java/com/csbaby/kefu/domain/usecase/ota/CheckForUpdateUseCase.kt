package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import javax.inject.Inject

/**
 * 检查更新 UseCase
 */
class CheckForUpdateUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    suspend operator fun invoke(): Boolean = otaManager.checkForUpdate()
}