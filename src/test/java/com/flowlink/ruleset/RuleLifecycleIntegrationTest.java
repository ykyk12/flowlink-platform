package com.flowlink.ruleset;

import com.flowlink.common.Hashes;
import com.flowlink.decision.DecisionService;
import com.flowlink.decision.EvaluateRequest;
import com.flowlink.decision.EvaluateResponse;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 规则生命周期集成测试：新建 → 发布 → 再发布 → 回滚 → 灰度，以及灰度路由对决策的影响。 */
@SpringBootTest
class RuleLifecycleIntegrationTest {

    private static final String LENIENT = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0, reason: 未命中拦截规则 }
            rules:
              - id: r_huge
                name: 超大额拦截
                priority: 10
                decision: REJECT
                score: 100
                reason: 金额超过 10 万
                when: { type: EXPR, field: amount, op: GT, value: 100000 }
            """;

    private static final String STRICT = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0, reason: 未命中拦截规则 }
            rules:
              - id: r_strict
                name: 严格拦截
                priority: 10
                decision: REJECT
                score: 100
                reason: 金额超过 1 元（灰度严格版）
                when: { type: EXPR, field: amount, op: GT, value: 1 }
            """;

    @Autowired
    private TenantService tenantService;
    @Autowired
    private RuleSetService ruleSetService;
    @Autowired
    private RuleCache ruleCache;
    @Autowired
    private DecisionService decisionService;

    @Test
    void lifecyclePublishRollbackCanary() {
        Tenant tenant = tenantService.createWithRawKey("生命周期租户", "key-lifecycle-1", "test", 100_000);
        String tenantId = tenant.getId();
        String key = "risk_lifecycle";

        ruleSetService.createSet(tenantId, new RuleSetDtos.CreateSetRequest(key, "生命周期规则集"));

        RuleSetDtos.VersionView v1 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(LENIENT, "宽松版"));
        assertEquals(1L, v1.version());
        ruleSetService.publish(tenantId, key, v1.version());

        RuleCache.CachedRuleSet cachedV1 = ruleCache.get(tenantId, key).orElseThrow();
        assertEquals(1L, cachedV1.activeVersion());
        assertFalse(cachedV1.hasCanary());
        assertEquals("FIRST_MATCH", cachedV1.active().mode().name());
        assertEquals(1, cachedV1.active().ruleCount());

        RuleSetDtos.VersionView v2 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(STRICT, "严格版"));
        assertEquals(2L, v2.version());
        ruleSetService.publish(tenantId, key, v2.version());
        assertEquals(2L, ruleCache.get(tenantId, key).orElseThrow().activeVersion());

        // 回滚：切回 v1
        RuleSetDtos.RuleSetView rolled = ruleSetService.rollback(tenantId, key);
        assertEquals(1L, rolled.activeVersion());
        assertEquals(1L, ruleCache.get(tenantId, key).orElseThrow().activeVersion());

        // 灰度 v2 @50%：命中灰度的主体走严格版
        ruleSetService.canary(tenantId, key, v2.version(), 50);
        RuleCache.CachedRuleSet cached = ruleCache.get(tenantId, key).orElseThrow();
        assertTrue(cached.hasCanary());
        assertEquals(2L, cached.canaryVersion());
        assertEquals(50, cached.canaryPercent());

        String canarySubject = subjectLandingInCanaryBucket(50);
        EvaluateResponse canaryResponse = decisionService.evaluate(tenantId,
                new EvaluateRequest(key, canarySubject, "ORDER_CREATE", Map.of("amount", 100),
                        Map.of(), Map.of(), null, false));
        assertTrue(canaryResponse.canary());
        assertEquals(2L, canaryResponse.version());
        assertEquals("REJECT", canaryResponse.decision());

        String stableSubject = subjectLandingOutsideCanaryBucket(50);
        EvaluateResponse stableResponse = decisionService.evaluate(tenantId,
                new EvaluateRequest(key, stableSubject, "ORDER_CREATE", Map.of("amount", 100),
                        Map.of(), Map.of(), null, false));
        assertFalse(stableResponse.canary());
        assertEquals(1L, stableResponse.version());
        assertEquals("PASS", stableResponse.decision());

        // 取消灰度
        ruleSetService.canary(tenantId, key, v2.version(), 0);
        assertFalse(ruleCache.get(tenantId, key).orElseThrow().hasCanary());
    }

    private String subjectLandingInCanaryBucket(int percent) {
        for (int i = 0; i < 1000; i++) {
            String candidate = "user-in-" + i;
            if (Hashes.stableBucket(candidate, 100) < percent) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法构造落在灰度桶内的主体");
    }

    private String subjectLandingOutsideCanaryBucket(int percent) {
        for (int i = 0; i < 1000; i++) {
            String candidate = "user-out-" + i;
            if (Hashes.stableBucket(candidate, 100) >= percent) {
                return candidate;
            }
        }
        throw new IllegalStateException("无法构造落在灰度桶外的主体");
    }
}
