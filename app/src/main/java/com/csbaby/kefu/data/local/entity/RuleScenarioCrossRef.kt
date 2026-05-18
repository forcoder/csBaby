package com.csbaby.kefu.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "rule_scenario_relation",
    primaryKeys = ["ruleId", "scenarioId"]
)
data class RuleScenarioCrossRef(
    val ruleId: Long,
    val scenarioId: Long,
    val tenantId: String = DEFAULT_TENANT_ID
) {
    companion object {
        const val DEFAULT_TENANT_ID = "default_tenant"
    }
}
