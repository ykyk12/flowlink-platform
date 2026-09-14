package com.flowlink.decision;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 幂等结果存放（内存 TTL 缓存）。
 * 语义：同一 (租户, 幂等键) 在 TTL 内重复提交，返回首次的决策结果，避免网络重试造成重复放行/重复拒绝。
 * 多实例部署时如需全局幂等，可替换为 Redis 实现（保留接口）。
 *
 * 过期清理：除 get() 命中时惰性删除外，再由 @Scheduled 周期性扫表，
 * 防止"提交后再不复现"的键永久驻留内存（对标 Redis 主动 TTL 过期），并暴露驻留条数 gauge。
 */
@Slf4j
@Component
public class IdempotencyStore {

    private record Entry(EvaluateResponse response, long expiresAtMillis) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();
    private final Counter evictedExpired;
    private final AtomicLong evictedTotal = new AtomicLong();

    public IdempotencyStore(MeterRegistry meterRegistry) {
        this.evictedExpired = Counter.builder("flowlink_idempotency_evicted_total")
                .description("因过期被主动清理的幂等条目数")
                .register(meterRegistry);
        // 当前驻留条目数（含未过期），便于观察内存占用与 TTL 是否合理
        meterRegistry.gauge("flowlink_idempotency_entries", store, Map::size);
    }

    public Optional<EvaluateResponse> get(String tenantId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        Entry entry = store.get(key(tenantId, idempotencyKey));
        if (entry == null) {
            return Optional.empty();
        }
        if (entry.expiresAtMillis() < System.currentTimeMillis()) {
            store.remove(key(tenantId, idempotencyKey));
            return Optional.empty();
        }
        return Optional.of(entry.response());
    }

    public void put(String tenantId, String idempotencyKey, EvaluateResponse response, int ttlSeconds) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return;
        }
        store.put(key(tenantId, idempotencyKey),
                new Entry(response, System.currentTimeMillis() + ttlSeconds * 1000L));
    }

    /** 每 60 秒主动清理过期条目，避免不复现的键长期驻留内存。 */
    @Scheduled(fixedDelay = 60_000L)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        int before = store.size();
        store.entrySet().removeIf(e -> e.getValue().expiresAtMillis() < now);
        int removed = before - store.size();
        if (removed > 0) {
            evictedExpired.increment(removed);
            evictedTotal.addAndGet(removed);
            log.debug("幂等存储主动清理过期条目 {} 条", removed);
        }
    }

    public int size() {
        return store.size();
    }

    /** 测试与运维使用：累计被主动清理的过期条数。 */
    public long evictedTotal() {
        return evictedTotal.get();
    }

    private String key(String tenantId, String idempotencyKey) {
        return tenantId + "::" + idempotencyKey;
    }
}
