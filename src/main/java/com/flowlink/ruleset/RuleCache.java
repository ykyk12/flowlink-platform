package com.flowlink.ruleset;

import com.flowlink.dsl.CompiledRuleSet;

import java.util.Optional;

/**
 * 编译产物缓存（无锁热加载）：决策路径只读缓存，
 * 规则发布/灰度/回滚时由 RuleSetService 整体替换；
 * 缓存未命中（重启或多实例）时由 DecisionService 触发一次回源加载。
 */
public interface RuleCache {

    Optional<CachedRuleSet> get(String tenantId, String key);

    void put(String tenantId, String key, CachedRuleSet cached);

    void invalidate(String tenantId, String key);

    int size();

    /** 一个规则集的生效版本 + 灰度版本编译结果。 */
    record CachedRuleSet(String key,
                         long activeVersion,
                         CompiledRuleSet active,
                         Long canaryVersion,
                         CompiledRuleSet canary,
                         int canaryPercent) {

        public boolean hasCanary() {
            return canary != null && canaryVersion != null && canaryPercent > 0;
        }
    }
}
