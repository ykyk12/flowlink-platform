package com.flowlink.decision;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 幂等存储：TTL 命中语义 + 过期项主动清理（防内存驻留泄漏）。 */
class IdempotencyStoreTest {

    private final IdempotencyStore store = new IdempotencyStore(new SimpleMeterRegistry());

    private EvaluateResponse sample() {
        return new EvaluateResponse("t-1", "rs", 1L, false, "PASS", 0, false, "ok",
                java.util.List.of(), null, 0L, false);
    }

    @Test
    void hitReturnsOriginalAndBlankKeyIgnored() {
        store.put("t1", "k-1", sample(), 60);
        Optional<EvaluateResponse> hit = store.get("t1", "k-1");
        assertTrue(hit.isPresent());
        assertEquals(0L, hit.orElseThrow().latencyMicros());

        // 空幂等键不写入也不命中
        store.put("t1", " ", sample(), 60);
        assertTrue(store.get("t1", " ").isEmpty());
    }

    @Test
    void evictExpiredRemovesStaleEntries() throws InterruptedException {
        store.put("t1", "live", sample(), 60);
        // TTL=0 立即视为过期；sleep 数毫秒保证墙钟跨过该时间点，避免同毫秒竞态
        store.put("t1", "stale", sample(), 0);
        Thread.sleep(5);
        assertEquals(2, store.size());

        // get 时惰性删除 stale
        assertTrue(store.get("t1", "stale").isEmpty());
        assertEquals(1, store.size());

        // 再放一个已过期的，由调度任务主动清理
        store.put("t1", "stale2", sample(), 0);
        Thread.sleep(5);
        assertEquals(2, store.size());
        store.evictExpired();
        assertEquals(1, store.size());
        assertTrue(store.get("t1", "live").isPresent());
        assertFalse(store.get("t1", "stale2").isPresent());
        assertTrue(store.evictedTotal() >= 1);
    }
}
