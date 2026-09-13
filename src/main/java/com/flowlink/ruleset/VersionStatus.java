package com.flowlink.ruleset;

/** 规则版本状态机：DRAFT → ACTIVE / CANARY → RETIRED（历史内容不可变）。 */
public enum VersionStatus {
    DRAFT,
    ACTIVE,
    CANARY,
    RETIRED
}
