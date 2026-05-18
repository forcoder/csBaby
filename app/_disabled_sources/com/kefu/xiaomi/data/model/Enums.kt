package com.kefu.xiaomi.data.model

enum class MatchType {
    EXACT,    // 精确匹配
    CONTAINS, // 包含匹配
    REGEX     // 正则表达式匹配
}

enum class ScenarioType {
    ALL_PROPERTIES,      // 所有房源
    SPECIFIC_PROPERTY,   // 具体房源
    SPECIFIC_PRODUCT     // 具体产品
}

enum class ModelType {
    OPENAI,
    CLAUDE,
    ZHIPU,
    TONGYI,
    CUSTOM
}

enum class ReplySource {
    RULE_MATCH,   // 规则匹配
    AI_GENERATED  // AI生成
}
