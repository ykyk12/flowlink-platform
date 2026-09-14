package com.flowlink.decision;

import com.flowlink.audit.AuditService;
import com.flowlink.feature.FeatureStore;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 幂等键并发回归：同一幂等键并发重复提交时，必须只有一次真正执行
 * （窗口计数只自增一次、只落一条审计），其余全部命中幂等回放。
 *
 * <p>旧实现对幂等键是"先查缓存再做事"的 check-then-act，并发下两个请求同时 miss，
 * 各自自增计数、各自审计，幂等承诺被击穿。
 */
@SpringBootTest
class DecisionIdempotencyConcurrencyTest {

    private static final String DSL = """
            mode: FIRST_MATCH
            fallback: { decision: PASS, score: 0 }
            rules:
              - id: r_burst
                decision: REJECT
                priority: 1
                when: { type: EXPR, field: "counter:burst_60s", op: GTE, value: 100 }
            """;

    @Autowired
    private TenantService tenantService;
    @Autowired
    private RuleSetService ruleSetService;
    @Autowired
    private DecisionService decisionService;
    @Autowired
    private FeatureStore featureStore;
    @Autowired
    private AuditService auditService;

    @Test
    void concurrentDuplicateIdempotencyKeyExecutesOnlyOnce() throws Exception {
        String rawKey = "key-idem-concurrent-" + System.nanoTime();
        Tenant tenant = tenantService.createWithRawKey("幂等并发租户", rawKey, "test", 100_000);
        String tenantId = tenant.getId();
        String key = "risk_idem_concurrent_" + System.nanoTime();

        ruleSetService.createSet(tenantId, new RuleSetDtos.CreateSetRequest(key, "幂等并发规则集"));
        RuleSetDtos.VersionView version = ruleSetService.createVersion(tenantId, key,
                new RuleSetDtos.CreateVersionRequest(DSL, "并发幂等用"));
        ruleSetService.publish(tenantId, key, version.version());

        String idemKey = "idem-" + System.nanoTime();
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Exception> errors = new ArrayList<>();
        AtomicInteger replayCount = new AtomicInteger();
        AtomicInteger freshCount = new AtomicInteger();

        try {
            for (int i = 0; i < threads; i++) {
                pool.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        EvaluateRequest req = new EvaluateRequest(key, "user-same", "ORDER_CREATE",
                                Map.of(), Map.of(), Map.of("burst_60s", 60), idemKey, false);
                        EvaluateResponse resp = decisionService.evaluate(tenantId, req);
                        if (resp.replayed()) {
                            replayCount.incrementAndGet();
                        } else {
                            freshCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        synchronized (errors) {
                            errors.add(e);
                        }
                    }
                });
            }
            assertTrue(ready.await(5, TimeUnit.SECONDS), "线程未就绪");
            start.countDown();
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), "线程未在超时内完成");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(errors.isEmpty(), () -> "并发决策出现异常：" + errors);

        // 16 次并发同键：恰好一次真正执行，其余 15 次幂等回放
        assertEquals(1, freshCount.get(), "应只有一次首次执行");
        assertEquals(threads - 1, replayCount.get(), "其余请求应命中幂等回放");

        // 真正的副作用：窗口计数只自增一次，审计只落一条
        long counter = featureStore.getCounter("feat:" + tenantId + ":burst_60s", 60);
        assertEquals(1, counter, "并发重复提交不应多次自增窗口计数");

        long auditRows = auditService.recentForReplay(tenantId, key, 100).size();
        assertEquals(1, auditRows, "并发重复提交不应落多条审计");
    }
}
