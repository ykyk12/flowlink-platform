package com.flowlink.engine;

import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.dsl.RuleDsl;
import com.flowlink.dsl.RuleSetDsl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 决策引擎（无状态，可并发调用）。
 *
 * FIRST_MATCH：按优先级升序取第一条命中规则立即裁决。
 * SCORE      ：命中规则分数累加；累加值 ≥ fallback.score 时，取"优先级最高"的命中规则决策；
 *              否则回落到兜底决策（响应里给出实际分数，便于调参）。
 */
@Component
public class DecisionEngine {

    private final RuleEvaluator evaluator;

    public DecisionEngine(RuleEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public DecisionResult evaluate(String ruleSetKey, long version, CompiledRuleSet compiled, DecisionContext context) {
        long start = System.nanoTime();
        List<DecisionResult.MatchedRule> matched = new ArrayList<>();
        List<EvaluationTrace> traces = new ArrayList<>();

        for (RuleDsl rule : compiled.orderedRules()) {
            boolean hit = evaluator.evaluate(rule.getWhen(), context, rule.getId(),
                    "rule[" + rule.getId() + "].when", traces);
            if (hit) {
                matched.add(new DecisionResult.MatchedRule(
                        rule.getId(), rule.getName(), rule.getPriority(),
                        rule.getDecision(), rule.getScore(), rule.getReason()));
                if (compiled.mode() == RuleSetDsl.Mode.FIRST_MATCH) {
                    break;
                }
            }
        }

        DecisionResult.DecisionResultBuilder builder = DecisionResult.builder()
                .matchedRules(matched)
                .traces(traces)
                .ruleSetKey(ruleSetKey)
                .version(version)
                .mode(compiled.mode().name());

        if (matched.isEmpty()) {
            RuleSetDsl.Fallback fallback = compiled.fallback();
            return builder.decision(fallback.getDecision())
                    .score(fallback.getScore())
                    .fallbackUsed(true)
                    .latencyMicros(elapsed(start))
                    .build();
        }

        if (compiled.mode() == RuleSetDsl.Mode.FIRST_MATCH) {
            DecisionResult.MatchedRule first = matched.get(0);
            return builder.decision(first.decision())
                    .score(first.score())
                    .fallbackUsed(false)
                    .latencyMicros(elapsed(start))
                    .build();
        }

        int totalScore = matched.stream().mapToInt(DecisionResult.MatchedRule::score).sum();
        RuleSetDsl.Fallback fallback = compiled.fallback();
        if (totalScore < fallback.getScore()) {
            return builder.decision(fallback.getDecision())
                    .score(totalScore)
                    .fallbackUsed(true)
                    .latencyMicros(elapsed(start))
                    .build();
        }
        DecisionResult.MatchedRule top = matched.stream()
                .min(Comparator.comparingInt(DecisionResult.MatchedRule::priority))
                .orElse(matched.get(0));
        return builder.decision(top.decision())
                .score(totalScore)
                .fallbackUsed(false)
                .latencyMicros(elapsed(start))
                .build();
    }

    private long elapsed(long startNanos) {
        return (System.nanoTime() - startNanos) / 1000L;
    }
}
