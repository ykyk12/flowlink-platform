package com.flowlink.decision;

import com.flowlink.engine.DecisionResult;
import com.flowlink.engine.EvaluationTrace;

import java.util.List;

/** 评估响应：决策 + 理由 + 命中规则 + 可选轨迹。 */
public record EvaluateResponse(String traceId,
                               String ruleSetKey,
                               long version,
                               boolean canary,
                               String decision,
                               int score,
                               boolean fallbackUsed,
                               String reason,
                               List<MatchedRuleView> matchedRules,
                               List<EvaluationTrace> traces,
                               long latencyMicros,
                               boolean replayed) {

    public record MatchedRuleView(String ruleId,
                                  String name,
                                  int priority,
                                  String decision,
                                  int score,
                                  String reason) {

        static MatchedRuleView from(DecisionResult.MatchedRule rule) {
            return new MatchedRuleView(rule.ruleId(), rule.name(), rule.priority(),
                    rule.decision(), rule.score(), rule.reason());
        }
    }

    /** 命中规则 id 列表（审计与回放差异对比用）。 */
    public String matchedRuleIdsCsv() {
        return matchedRules == null ? "" : String.join(",",
                matchedRules.stream().map(MatchedRuleView::ruleId).toList());
    }
}
