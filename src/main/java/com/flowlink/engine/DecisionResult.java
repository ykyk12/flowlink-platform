package com.flowlink.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 决策结果：决策 + 分数 + 命中规则 + 完整求值轨迹 + 耗时。 */
@Getter
@Builder
public class DecisionResult {

    private final String decision;
    private final int score;
    private final boolean fallbackUsed;
    private final List<MatchedRule> matchedRules;
    private final List<EvaluationTrace> traces;
    private final String ruleSetKey;
    private final long version;
    private final String mode;
    private final long latencyMicros;

    /** 决策理由摘要（取命中规则 reason 拼接，或兜底理由）。 */
    public String reason() {
        if (matchedRules == null || matchedRules.isEmpty()) {
            return "未命中任何规则";
        }
        return String.join(" | ", matchedRules.stream()
                .map(MatchedRule::reason)
                .filter(r -> r != null && !r.isBlank())
                .toList());
    }

    public record MatchedRule(String ruleId,
                              String name,
                              int priority,
                              String decision,
                              int score,
                              String reason) {
    }
}
