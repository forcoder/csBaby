package com.csbaby.kefu.domain.usecase.ota

import com.csbaby.kefu.infrastructure.ota.OtaManager
import javax.inject.Inject

/**
 * 取消下载 UseCase
 */
class CancelDownloadUseCase @Inject constructor(
    private val otaManager: OtaManager
) {
    operator fun invoke() {
        otaManager.cancelDownload()
    }
}