package com.flowlink.ruleset;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存实现：单实例足够；多实例部署时每个实例各自持有编译产物（内容一致）。
 *
 * 缓存命中率打点：hit / miss 计数，便于判断热加载缓存是否真正生效、回源频率是否过高。
 */
@Component
public class InMemoryRuleCache implements RuleCache {

    private final Map<String, CachedRuleSet> store = new ConcurrentHashMap<>();
    private final Counter hits;
    private final Counter misses;

    public InMemoryRuleCache(MeterRegistry meterRegistry) {
        this.hits = Counter.builder("flowlink_rule_cache_total")
                .description("规则集编译产物缓存命中/未命中次数")
                .tag("result", "hit")
                .register(meterRegistry);
        this.misses = Counter.builder("flowlink_rule_cache_total")
                .tag("result", "miss")
                .register(meterRegistry);
    }

    @Override
    public Optional<CachedRuleSet> get(String tenantId, String key) {
        Optional<CachedRuleSet> cached = Optional.ofNullable(store.get(cacheKey(tenantId, key)));
        if (cached.isPresent()) {
            hits.increment();
        } else {
            misses.increment();
        }
        return cached;
    }

    @Override
    public void put(String tenantId, String key, CachedRuleSet cached) {
        store.put(cacheKey(tenantId, key), cached);
    }

    @Override
    public void invalidate(String tenantId, String key) {
        store.remove(cacheKey(tenantId, key));
    }

    @Override
    public int size() {
        return store.size();
    }

    private String cacheKey(String tenantId, String key) {
        return tenantId + "::" + key;
    }
}
