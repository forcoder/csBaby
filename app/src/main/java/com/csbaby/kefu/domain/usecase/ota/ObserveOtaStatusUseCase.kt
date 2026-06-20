package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.UpdateStatus
import com.csbaby.kefu.infrastructure.ota.OtaManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * 观察更新状态 UseCase
 */
class ObserveOtaStatusUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    operator fun invoke(): Flow<UpdateStatus> = otaManager.updateStatus
}