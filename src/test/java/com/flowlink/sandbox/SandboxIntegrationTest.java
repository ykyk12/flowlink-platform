package com.flowlink.sandbox;

import com.flowlink.audit.AuditService;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则沙箱/预演集成测试：未发布的严格版本可直接试跑命中效果，
 * 且全程无副作用（不落审计、不依赖线上灰度路由）。
 */
@SpringBootTest
class SandboxIntegrationTest {

    private static final String LENIENT_V1 = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0 }
            rules:
              - id: r_huge
                decision: REJECT
                priority: 10
                when: { type: EXPR, field: amount, op: GT, value: 1000000 }
            """;

    private static final String STRICT_V2 = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0 }
            rules:
              - id: r_low
                decision: REJECT
                priority: 10
                reason: 金额超过 50 元即拦截
                when: { type: EXPR, field: amount, op: GT, value: 50 }
            """;

    @Autowired
    private TenantService tenantService;
    @Autowired
    private RuleSetService ruleSetService;
    @Autowired
    private SandboxService sandboxService;
    @Autowired
    private AuditService auditService;

    @Test
    void simulatesUnpublishedVersionWithoutSideEffects() {
        String rawKey = "key-sandbox-" + System.nanoTime();
        Tenant tenant = tenantService.createWithRawKey("沙箱租户", rawKey, "test", 100_000);
        String tenantId = tenant.getId();
        String key = "risk_sandbox_" + System.nanoTime();

        ruleSetService.createSet(tenantId, new RuleSetDtos.CreateSetRequest(key, "沙箱规则集"));
        RuleSetDtos.VersionView v1 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(LENIENT_V1, "宽松版"));
        ruleSetService.publish(tenantId, key, v1.version());
        RuleSetDtos.VersionView v2 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(STRICT_V2, "严格版（未发布）"));
        assertEquals(2L, v2.version());

        // 线上生效版（v1 宽松）对 amount=100 放行
        SandboxDtos.SandboxResult onActive = sandboxService.simulate(tenantId,
                new SandboxDtos.SandboxRequest(key, null, "u1", "ORDER",
                        Map.of("amount", 100), Map.of(), null));
        assertEquals(1L, onActive.version());
        assertEquals("PASS", onActive.decision());

        // 沙箱指定未发布的 v2 严格版，同一笔 100 元交易应被 REJECT
        SandboxDtos.SandboxResult onStrict = sandboxService.simulate(tenantId,
                new SandboxDtos.SandboxRequest(key, v2.version(), "u1", "ORDER",
                        Map.of("amount", 100), Map.of(), null));
        assertEquals(2L, onStrict.version());
        assertEquals("REJECT", onStrict.decision());
        assertFalse(onStrict.fallbackUsed());
        assertEquals("r_low", onStrict.matchedRules().get(0).ruleId());
        // 沙箱默认带完整轨迹，便于解释
        assertFalse(onStrict.traces().isEmpty());
        assertNotNull(onStrict.reason());

        // 沙箱纯只读：不写审计，也不污染线上决策
        assertEquals(0, auditService.recentForReplay(tenantId, key, 10).size());
    }

    @Test
    void sandboxHonoursSuppliedCounterSnapshotWithoutIncrementing() {
        String rawKey = "key-sandbox-counter-" + System.nanoTime();
        Tenant tenant = tenantService.createWithRawKey("沙箱计数租户", rawKey, "test", 100_000);
        String tenantId = tenant.getId();
        String key = "risk_sandbox_cnt_" + System.nanoTime();

        String dsl = """
                mode: FIRST_MATCH
                fallback: { decision: PASS, score: 0 }
                rules:
                  - id: r_burst
                    decision: REVIEW
                    priority: 1
                    when: { type: EXPR, field: "counter:login_fail_60s", op: GTE, value: 5 }
                """;
        ruleSetService.createSet(tenantId, new RuleSetDtos.CreateSetRequest(key, "沙箱计数规则集"));
        RuleSetDtos.VersionView v1 = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(dsl, "计数规则"));
        ruleSetService.publish(tenantId, key, v1.version());

        // 直接给定计数快照=7，应命中 REVIEW；沙箱不自增真实窗口计数
        SandboxDtos.SandboxResult hit = sandboxService.simulate(tenantId,
                new SandboxDtos.SandboxRequest(key, null, "u-cnt", "LOGIN_FAIL",
                        Map.of(), Map.of(), Map.of("login_fail_60s", 7L)));
        assertEquals("REVIEW", hit.decision());

        SandboxDtos.SandboxResult miss = sandboxService.simulate(tenantId,
                new SandboxDtos.SandboxRequest(key, null, "u-cnt", "LOGIN_FAIL",
                        Map.of(), Map.of(), Map.of("login_fail_60s", 2L)));
        assertEquals("PASS", miss.decision());
        assertTrue(miss.fallbackUsed());
    }
}
