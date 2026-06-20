package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.ota.OtaManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察可用更新 UseCase
 */
class ObserveAvailableUpdateUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    operator fun invoke(): Flow<OtaUpdate?> = otaManager.availableUpdate
}