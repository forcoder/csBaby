package com.csbaby.kefu.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "rule_scenario_relation",
    primaryKeys = ["ruleId", "scenarioId"]
)
data class RuleScenarioCrossRef(
    val ruleId: Long,
    val scenarioId: Long,
    val tenantId: String = DEFAULT_TENANT_ID  // 保留默认值用于旧数据兼容
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
