package com.flowlink.config;

import com.flowlink.feature.FeatureStore;
import com.flowlink.ruleset.RuleCache;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 决策子系统健康检查：暴露特征存储后端与规则缓存规模。
 * 正常返回 UP；规则缓存为空（尚未热加载）不视为故障，仅作 details 提示。
 */
@Component("flowlinkDecision")
public class FlowLinkHealthIndicator implements HealthIndicator {

    private final FeatureStore featureStore;
    private final RuleCache ruleCache;

    public FlowLinkHealthIndicator(FeatureStore featureStore, RuleCache ruleCache) {
        this.featureStore = featureStore;
        this.ruleCache = ruleCache;
    }

    @Override
    public Health health() {
        int cachedSets = ruleCache.size();
        return Health.up()
                .withDetail("featureStoreBackend", featureStore.backend())
                .withDetail("ruleSetCacheEntries", cachedSets)
                .build();
    }
}
