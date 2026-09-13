package com.flowlink.dsl;

import com.flowlink.common.BizException;
import com.flowlink.config.FlowLinkProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** DSL 解析与校验：未知字段、重复 id、非法正则、超深嵌套都要在发布前拦住。 */
class RuleDslParserTest {

    private final RuleDslParser parser = new RuleDslParser();
    private final RuleValidator validator = new RuleValidator(new FlowLinkProperties());

    private static final String VALID = """
            description: 示例
            mode: SCORE
            fallback:
              decision: PASS
              score: 60
            rules:
              - id: r1
                name: 黑名单
                priority: 5
                decision: REJECT
                score: 100
                reason: 命中黑名单
                when:
                  type: EXPR
                  field: riskLevel
                  op: IN
                  value: [ HIGH, BLACK ]
              - id: r2
                name: 大额
                priority: 20
                decision: REVIEW
                score: 70
                when:
                  type: ALL
                  children:
                    - type: EXPR
                      field: amount
                      op: GT
                      value: 5000
                    - type: NOT
                      children:
                        - type: EXPR
                          field: vip
                          op: EQ
                          value: true
            """;

    @Test
    void parsesValidYamlAndPassesValidation() {
        RuleSetDsl dsl = parser.parse(VALID);
        assertEquals(RuleSetDsl.Mode.SCORE, dsl.getMode());
        assertEquals(2, dsl.getRules().size());
        assertEquals("PASS", dsl.getFallback().getDecision());
        validator.validate(dsl);
    }

    @Test
    void parsesJsonBecauseYamlIsSuperset() {
        String json = """
                {"mode":"FIRST_MATCH","fallback":{"decision":"PASS","score":0},
                 "rules":[{"id":"r1","decision":"REJECT","priority":1,
                 "when":{"type":"EXPR","field":"ipRiskScore","op":"GTE","value":80}}]}
                """;
        RuleSetDsl dsl = parser.parse(json);
        assertEquals(RuleSetDsl.Mode.FIRST_MATCH, dsl.getMode());
        assertEquals(Operator.GTE, dsl.getRules().get(0).getWhen().getOp());
    }

    @Test
    void rejectsUnknownFieldToCatchTypos() {
        String typo = """
                mode: SCORE
                rules:
                  - id: r1
                    decision: PASS
                    priorty: 10
                    when:
                      type: EXPR
                      field: a
                      op: EQ
                      value: 1
                """;
        BizException ex = assertThrows(BizException.class, () -> parser.parse(typo));
        assertTrue(ex.getMessage().contains("无法解析"), ex.getMessage());
    }

    @Test
    void rejectsDuplicateRuleIds() {
        String duplicated = """
                mode: SCORE
                rules:
                  - id: same
                    decision: PASS
                    when: { type: EXPR, field: a, op: EQ, value: 1 }
                  - id: same
                    decision: PASS
                    when: { type: EXPR, field: a, op: EQ, value: 1 }
                """;
        RuleSetDsl dsl = parser.parse(duplicated);
        BizException ex = assertThrows(BizException.class, () -> validator.validate(dsl));
        assertTrue(ex.getMessage().contains("重复"), ex.getMessage());
    }

    @Test
    void rejectsInvalidRegex() {
        String badRegex = """
                mode: SCORE
                rules:
                  - id: r1
                    decision: PASS
                    when:
                      type: EXPR
                      field: phone
                      op: MATCHES
                      value: "[unclosed"
                """;
        RuleSetDsl dsl = parser.parse(badRegex);
        assertThrows(BizException.class, () -> validator.validate(dsl));
    }

    @Test
    void rejectsTooDeepNesting() {
        String deep = """
                mode: SCORE
                rules:
                  - id: r1
                    decision: PASS
                    when:
                      type: ALL
                      children:
                        - type: ALL
                          children:
                            - type: ALL
                              children:
                                - type: ALL
                                  children:
                                    - type: ALL
                                      children:
                                        - type: ALL
                                          children:
                                            - type: ALL
                                              children:
                                                - type: ALL
                                                  children:
                                                    - type: EXPR
                                                      field: a
                                                      op: EQ
                                                      value: 1
                """;
        RuleSetDsl dsl = parser.parse(deep);
        BizException ex = assertThrows(BizException.class, () -> validator.validate(dsl));
        assertTrue(ex.getMessage().contains("深度"), ex.getMessage());
    }

    @Test
    void rejectsInWithoutArrayValue() {
        String inWithoutList = """
                mode: SCORE
                rules:
                  - id: r1
                    decision: PASS
                    when: { type: EXPR, field: level, op: IN, value: HIGH }
                """;
        RuleSetDsl dsl = parser.parse(inWithoutList);
        assertThrows(BizException.class, () -> validator.validate(dsl));
    }
}
