package com.csbaby.kefu.domain.model

enum class MatchType {
    EXACT,
    CONTAINS,
    REGEX
}

enum class ScenarioType {
    ALL_PROPERTIES,
    SPECIFIC_PROPERTY,
    SPECIFIC_PRODUCT
}

enum class ModelType {
    OPENAI,
    CLAUDE,
    ZHIPU,
    TONGYI,
    CUSTOM
}

enum class ReplySource {
    RULE_MATCH,
    AI_GENERATED
}

enum class RuleTargetType {
    ALL,
    CONTACT,
    GROUP,
    PROPERTY
}


