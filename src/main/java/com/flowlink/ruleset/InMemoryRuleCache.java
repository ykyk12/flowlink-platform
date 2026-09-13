package com.flowlink.ruleset;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 内存实现：单实例足够；多实例部署时每个实例各自持有编译产物（内容一致）。 */
@Component
@RequiredArgsConstructor
public class InMemoryRuleCache implements RuleCache {

    private final Map<String, CachedRuleSet> store = new ConcurrentHashMap<>();

    @Override
    public Optional<CachedRuleSet> get(String tenantId, String key) {
        return Optional.ofNullable(store.get(cacheKey(tenantId, key)));
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
