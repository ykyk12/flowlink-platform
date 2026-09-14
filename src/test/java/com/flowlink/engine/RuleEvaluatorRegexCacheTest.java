package com.flowlink.engine;

import com.flowlink.dsl.ConditionNode;
import com.flowlink.dsl.Operator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** MATCHES 正则：编译一次、多次执行的缓存语义与正确性。 */
class RuleEvaluatorRegexCacheTest {

    private final RuleEvaluator evaluator = new RuleEvaluator();

    private DecisionContext ctx(String field, Object value) {
        return DecisionContext.builder()
                .tenantId("t1")
                .subjectId("u1")
                .eventType("EVT")
                .features(Map.of(field, value))
                .build();
    }

    private ConditionNode regex(String pattern) {
        ConditionNode node = new ConditionNode();
        node.setType(ConditionNode.NodeType.EXPR);
        node.setField("s");
        node.setOp(Operator.MATCHES);
        node.setValue(pattern);
        return node;
    }

    @Test
    void sameRegexCompiledOnce() {
        ConditionNode node = regex(".*@example\\.com$");
        List<EvaluationTrace> traces = new ArrayList<>();

        evaluator.evaluate(node, ctx("s", "a@example.com"), "r1", "p", traces);
        assertEquals(1, evaluator.cachedPatternCount());

        // 同一正则再求值，不新增缓存
        evaluator.evaluate(node, ctx("s", "b@example.com"), "r1", "p", traces);
        assertEquals(1, evaluator.cachedPatternCount());

        assertTrue(evaluator.evaluate(node, ctx("s", "x@example.com"), "r1", "p", traces));
        assertFalse(evaluator.evaluate(node, ctx("s", "x@bad.net"), "r1", "p", traces));
    }

    @Test
    void differentRegexesCachedSeparately() {
        List<EvaluationTrace> traces = new ArrayList<>();
        evaluator.evaluate(regex("^abc.*"), ctx("s", "abc123"), "r1", "p", traces);
        evaluator.evaluate(regex("\\d{3}-\\d{4}"), ctx("s", "123-4567"), "r2", "p", traces);
        assertEquals(2, evaluator.cachedPatternCount());
    }
}
