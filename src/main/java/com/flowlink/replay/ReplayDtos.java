package com.flowlink.replay;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.List;

/** 回放对比 DTO：用历史决策的输入快照在目标版本上重跑，输出差异报告。 */
public final class ReplayDtos {

    private ReplayDtos() {
    }

    public record ReplayRequest(
            @NotBlank String ruleSetKey,
            @Positive long targetVersion,
            @Positive Integer limit) {
    }

    public record ReplayDiff(int total,
                             int changed,
                             int unchanged,
                             double changedRate,
                             long targetVersion,
                             List<Sample> samples) {
    }

    public record Sample(String traceId,
                         String oldDecision,
                         String newDecision,
                         int oldScore,
                         int newScore,
                         String oldMatchedRuleIds,
                         String newMatchedRuleIds,
                         boolean changed) {
    }
}
