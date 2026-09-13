package com.flowlink.dsl;

import java.util.Comparator;
import java.util.List;

/**
 * 编译产物：校验通过并按优先级排好序的规则集（不可变视图）。
 * 决策路径只读该对象，规则变更时整体替换引用（无锁热加载）。
 */
public final class CompiledRuleSet {

    private final RuleSetDsl dsl;
    private final List<RuleDsl> orderedRules;

    public CompiledRuleSet(RuleSetDsl dsl) {
        this.dsl = dsl;
        this.orderedRules = dsl.getRules().stream()
                .sorted(Comparator.comparingInt(RuleDsl::getPriority))
                .toList();
    }

    public RuleSetDsl dsl() {
        return dsl;
    }

    public List<RuleDsl> orderedRules() {
        return orderedRules;
    }

    public RuleSetDsl.Mode mode() {
        return dsl.getMode();
    }

    public RuleSetDsl.Fallback fallback() {
        return dsl.getFallback();
    }

    public int ruleCount() {
        return orderedRules.size();
    }
}
