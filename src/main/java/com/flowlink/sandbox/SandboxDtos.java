package com.flowlink.sandbox;

import com.flowlink.decision.EvaluateResponse;
import com.flowlink.engine.EvaluationTrace;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.Map;

/**
 * 规则预演（沙箱）DTO：给定样本输入，在指定规则集版本上干跑一次，返回命中与轨迹。
 * 与在线决策的区别：不自增窗口计数、不落审计、不做幂等、不走灰度路由。
 */
public final class SandboxDtos {

    private SandboxDtos() {
    }

    public record SandboxRequest(
            @NotBlank String ruleSetKey,
            @Positive Long version,
            String subjectId,
            String eventType,
            Map<String, Object> features,
            Map<String, Object> attributes,
            /** 直接给定的窗口计数快照（counter 名 → 值），沙箱内只读取、不自增 */
            Map<String, Long> counters) {
    }

    public record SandboxResult(
            long version,
            String decision,
            int score,
            boolean fallbackUsed,
            String reason,
            List<EvaluateResponse.MatchedRuleView> matchedRules,
            List<EvaluationTrace> traces) {
    }
}
