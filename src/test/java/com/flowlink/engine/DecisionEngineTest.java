package com.flowlink.engine;

import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.dsl.RuleDslParser;
import com.flowlink.dsl.RuleSetDsl;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 决策引擎：两种裁决模式、窗口计数、缺失值语义与可解释轨迹。 */
class DecisionEngineTest {

    private final RuleDslParser parser = new RuleDslParser();
    private final DecisionEngine engine = new DecisionEngine(new RuleEvaluator());

    private DecisionContext context(Map<String, Object> features, Map<String, Long> counters) {
        return DecisionContext.builder()
                .tenantId("t1")
                .subjectId("u1")
                .eventType("ORDER_CREATE")
                .features(features)
                .attributes(Map.of("region", "cn"))
                .counters(counters)
                .build();
    }

    @Test
    void firstMatchPicksHighestPriorityHit() {
        RuleSetDsl dsl = parser.parse("""
                mode: FIRST_MATCH
                fallback: { decision: PASS, score: 0 }
                rules:
                  - id: r_block
                    name: 黑名单
                    priority: 5
                    decision: REJECT
                    score: 100
                    reason: 黑名单命中
                    when: { type: EXPR, field: riskLevel, op: IN, value: [ HIGH, BLACK ] }
                  - id: r_review
                    name: 大额复核
                    priority: 30
                    decision: REVIEW
                    score: 60
                    when: { type: EXPR, field: amount, op: GT, value: 5000 }
                """);
        DecisionResult result = engine.evaluate("demo", 1L, new CompiledRuleSet(dsl),
                context(Map.of("riskLevel", "HIGH", "amount", 9999), Map.of()));

        assertEquals("REJECT", result.getDecision());
        assertEquals(100, result.getScore());
        assertFalse(result.isFallbackUsed());
        assertEquals(1, result.getMatchedRules().size());
        assertFalse(result.getTraces().isEmpty());
    }

    @Test
    void scoreModeSumsAndAppliesThreshold() {
        RuleSetDsl dsl = parser.parse("""
                mode: SCORE
                fallback: { decision: PASS, score: 60, reason: 未达阈值 }
                rules:
                  - id: r_amount
                    name: 大额
                    priority: 30
                    decision: REVIEW
                    score: 70
                    reason: 大额交易
                    when: { type: EXPR, field: amount, op: GT, value: 5000 }
                  - id: r_new_device
                    name: 新设备
                    priority: 40
                    decision: REVIEW
                    score: 25
                    reason: 设备过新
                    when: { type: EXPR, field: deviceAgeDays, op: LT, value: 3 }
                """);
        CompiledRuleSet compiled = new CompiledRuleSet(dsl);

        DecisionResult both = engine.evaluate("demo", 2L, compiled,
                context(Map.of("amount", 9000, "deviceAgeDays", 1), Map.of()));
        assertEquals(95, both.getScore());
        assertEquals("REVIEW", both.getDecision());
        assertEquals(2, both.getMatchedRules().size());

        DecisionResult onlyWeak = engine.evaluate("demo", 2L, compiled,
                context(Map.of("amount", 100, "deviceAgeDays", 1), Map.of()));
        assertEquals(25, onlyWeak.getScore());
        assertTrue(onlyWeak.isFallbackUsed());
        assertEquals("PASS", onlyWeak.getDecision());
    }

    @Test
    void counterFieldsResolveFromWindowCounters() {
        RuleSetDsl dsl = parser.parse("""
                mode: FIRST_MATCH
                fallback: { decision: PASS, score: 0 }
                rules:
                  - id: r_burst
                    name: 频次异常
                    priority: 10
                    decision: REVIEW
                    score: 50
                    reason: 短时下单过多
                    when: { type: EXPR, field: "counter:order_count_60s", op: GTE, value: 6 }
                """);
        CompiledRuleSet compiled = new CompiledRuleSet(dsl);

        DecisionResult burst = engine.evaluate("demo", 3L, compiled, context(Map.of(), Map.of("order_count_60s", 7L)));
        assertEquals("REVIEW", burst.getDecision());

        DecisionResult normal = engine.evaluate("demo", 3L, compiled, context(Map.of(), Map.of("order_count_60s", 2L)));
        assertEquals("PASS", normal.getDecision());
        assertTrue(normal.isFallbackUsed());
    }

    @Test
    void missingFieldNeverMatchesExceptExists() {
        RuleSetDsl dsl = parser.parse("""
                mode: FIRST_MATCH
                fallback: { decision: PASS, score: 0 }
                rules:
                  - id: r_amount
                    name: 大额
                    priority: 10
                    decision: REVIEW
                    score: 10
                    when: { type: EXPR, field: amount, op: GT, value: 100 }
                  - id: r_exists
                    name: 字段存在
                    priority: 20
                    decision: REVIEW
                    score: 10
                    when: { type: EXPR, field: deviceId, op: EXISTS }
                """);
        CompiledRuleSet compiled = new CompiledRuleSet(dsl);
        DecisionResult result = engine.evaluate("demo", 4L, compiled, context(Map.of("deviceId", "d-1"), Map.of()));

        assertEquals("REVIEW", result.getDecision());
        assertEquals("r_exists", result.getMatchedRules().get(0).ruleId());
        assertNotNull(result.getTraces());
        assertTrue(result.getLatencyMicros() >= 0);
    }

    @Test
    void fallbackUsedWhenNothingMatches() {
        RuleSetDsl dsl = parser.parse("""
                mode: SCORE
                fallback: { decision: PASS, score: 10, reason: 无风险特征 }
                rules:
                  - id: r1
                    decision: REJECT
                    priority: 1
                    score: 90
                    when: { type: EXPR, field: riskLevel, op: EQ, value: BLACK }
                """);
        DecisionResult result = engine.evaluate("demo", 5L, new CompiledRuleSet(dsl), context(Map.of(), Map.of()));
        assertEquals("PASS", result.getDecision());
        assertTrue(result.isFallbackUsed());
        assertEquals("未命中任何规则", result.reason());
    }
}
