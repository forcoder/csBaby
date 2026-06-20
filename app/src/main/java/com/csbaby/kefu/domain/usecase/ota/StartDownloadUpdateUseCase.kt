package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.data.model.OtaUpdate
import com.csbaby.kefu.infrastructure.ota.OtaManager
import javax.inject.Inject

/**
 * 开始下载更新 UseCase
 */
class StartDownloadUpdateUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    operator fun invoke(update: OtaUpdate): Boolean = otaManager.startDownload(update)
}