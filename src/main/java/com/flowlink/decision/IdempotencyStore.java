package com.flowlink.decision;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幂等结果存放（内存 TTL 缓存）。
 * 语义：同一 (租户, 幂等键) 在 TTL 内重复提交，返回首次的决策结果，避免网络重试造成重复放行/重复拒绝。
 * 多实例部署时如需全局幂等，可替换为 Redis 实现（保留接口）。
 */
@Component
@RequiredArgsConstructor
public class IdempotencyStore {

    private record Entry(EvaluateResponse response, long expiresAtMillis) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

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

    public int size() {
        return store.size();
    }

    private String key(String tenantId, String idempotencyKey) {
        return tenantId + "::" + idempotencyKey;
    }
}
