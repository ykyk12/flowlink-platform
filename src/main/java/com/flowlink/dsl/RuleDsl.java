package com.flowlink.dsl;

import lombok.Data;

/** 单条规则：优先级升序求值，priority 越小越先算。 */
@Data
public class RuleDsl {

    /** 规则唯一 id（同一规则集内不可重复） */
    private String id;

    /** 规则名称（展示与命中路径可读性） */
    private String name;

    /** 优先级，默认 100 */
    private int priority = 100;

    /** 命中后的决策，例如 REJECT / REVIEW / PASS */
    private String decision;

    /** 命中后的分数贡献（SCORE 模式累加） */
    private int score;

    /** 决策理由（可解释性，随响应返回） */
    private String reason;

    /** 触发条件 */
    private ConditionNode when;
}
