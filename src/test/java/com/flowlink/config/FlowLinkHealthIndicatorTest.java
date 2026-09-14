package com.flowlink.config;

import com.flowlink.feature.FeatureStore;
import com.flowlink.ruleset.RuleCache;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 决策子系统健康指示器：应 UP 并透出特征存储后端与缓存规模。 */
class FlowLinkHealthIndicatorTest {

    @Test
    void reportsUpWithBackendAndCacheDetails() {
        FeatureStore store = mock(FeatureStore.class);
        when(store.backend()).thenReturn("memory");
        RuleCache cache = mock(RuleCache.class);
        when(cache.size()).thenReturn(3);

        FlowLinkHealthIndicator indicator = new FlowLinkHealthIndicator(store, cache);
        var health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        assertEquals("memory", health.getDetails().get("featureStoreBackend"));
        assertEquals(3, health.getDetails().get("ruleSetCacheEntries"));
        assertTrue((Integer) health.getDetails().get("ruleSetCacheEntries") >= 0);
    }
}
