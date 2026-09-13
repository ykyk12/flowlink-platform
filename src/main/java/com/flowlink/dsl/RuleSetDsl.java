package com.flowlink.dsl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/** 规则集 DSL 根节点。 */
@Data
public class RuleSetDsl {

    public enum Mode {
        /** 命中第一条即裁决（按优先级升序，适合硬规则） */
        FIRST_MATCH,
        /** 命中规则分数累加，达阈值才出风险决策（适合评分卡） */
        SCORE
    }

    private String description;

    private Mode mode = Mode.SCORE;

    private Fallback fallback = new Fallback();

    private List<RuleDsl> rules = new ArrayList<>();

    /** 未命中/未达阈值时的兜底决策 */
    @Data
    public static class Fallback {
        private String decision = "PASS";
        private int score = 0;
        private String reason = "未命中任何规则";
    }
}
