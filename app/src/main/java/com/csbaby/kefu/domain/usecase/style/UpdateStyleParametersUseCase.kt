package com.csbaby.kefu.domain.usecase.style

import com.csbaby.kefu.infrastructure.style.StyleLearningEngine
import javax.inject.Inject

/**
 * 更新风格参数 UseCase
 *
 * 用一行调用让 ProfileViewModel 与 StyleLearningEngine 解耦。
 * 后续如果需要批量更新或加校验（如参数范围），统一在此处加。
 */
class UpdateStyleParametersUseCase @Inject constructor(
    private val styleLearningEngine: StyleLearningEngine
) {
    suspend operator fun invoke(
        userId: String,
        formality: Float? = null,
        enthusiasm: Float? = null,
        professionalism: Float? = null
    ) {
        styleLearningEngine.updateStyleParameters(
            userId = userId,
            formality = formality,
            enthusiasm = enthusiasm,
            professionalism = professionalism
        )
    }
}
