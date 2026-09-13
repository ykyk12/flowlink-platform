package com.flowlink.replay;

import com.flowlink.audit.AuditService;
import com.flowlink.decision.DecisionService;
import com.flowlink.decision.EvaluateRequest;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 回放对比集成测试：历史决策在更严格的新版本上重跑，差异必须被识别出来。 */
@SpringBootTest
class ReplayIntegrationTest {

    private static final String LENIENT_V1 = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0, reason: 未命中拦截 }
            rules:
              - id: r_huge
                name: 超大额
                priority: 10
                decision: REJECT
                score: 100
                reason: 金额超过 100 万
                when: { type: EXPR, field: amount, op: GT, value: 1000000 }
            """;

    private static final String STRICT_V2 = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0, reason: 未命中拦截 }
            rules:
              - id: r_low
                name: 低额拦截（新策略）
                priority: 10
                decision: REJECT
                score: 100
                reason: 金额超过 50 元
                when: { type: EXPR, field: amount, op: GT, value: 50 }
            """;

    @Autowired
    private TenantService tenantService;
    @Autowired
    private RuleSetService ruleSetService;
    @Autowired
    private DecisionService decisionService;
    @Autowired
    private ReplayService replayService;
    @Autowired
    private AuditService auditService;

    @Test
    void replayDetectsDecisionChangesAgainstNewVersion() {
        String rawKey = "key-replay-" + System.nanoTime();
        Tenant tenant = tenantService.createWithRawKey("回放租户", rawKey, "test", 100_000);
        String tenantId = tenant.getId();
        String key = "risk_replay_" + System.nanoTime();

        ruleSetService.createSet(tenantId, new RuleSetDtos.CreateSetRequest(key, "回放规则集"));
        RuleSetDtos.VersionView v1 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(LENIENT_V1, "宽松版"));
        ruleSetService.publish(tenantId, key, v1.version());

        for (int i = 0; i < 3; i++) {
            decisionService.evaluate(tenantId, new EvaluateRequest(key, "user-" + i, "ORDER_CREATE",
                    Map.of("amount", 100 + i), Map.of(), Map.of(), null, false));
        }
        assertEquals(3, auditService.recentForReplay(tenantId, key, 10).size());

        RuleSetDtos.VersionView v2 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(STRICT_V2, "严格版（未发布，仅用于回放评估）"));
        assertEquals(2L, v2.version());

        ReplayDtos.ReplayDiff diff = replayService.replay(tenantId,
                new ReplayDtos.ReplayRequest(key, v2.version(), 50));

        assertEquals(3, diff.total());
        assertEquals(3, diff.changed());
        assertEquals(1.0, diff.changedRate(), 0.0001);
        assertTrue(diff.samples().stream()
                .allMatch(sample -> "PASS".equals(sample.oldDecision()) && "REJECT".equals(sample.newDecision())));
        assertEquals("r_low", diff.samples().get(0).newMatchedRuleIds());
    }
}
