package com.flowlink.feature;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 滑动窗口计数：窗口内累计、窗口外过期。 */
class InMemoryFeatureStoreTest {

    private final InMemoryFeatureStore store = new InMemoryFeatureStore();

    @Test
    void countsWithinWindow() {
        String key = "feat:t1:order_count_60s";
        assertEquals(1, store.incrementCounter(key, 60));
        assertEquals(2, store.incrementCounter(key, 60));
        assertEquals(3, store.incrementCounter(key, 60));
        assertEquals(3, store.getCounter(key, 60));
        assertEquals("memory", store.backend());
        assertTrue(store.trackedKeys() >= 1);
    }

    @Test
    void expiresOutsideWindow() throws InterruptedException {
        String key = "feat:t1:login_fail_1s";
        store.incrementCounter(key, 1);
        assertEquals(1, store.getCounter(key, 1));
        Thread.sleep(1200L);
        assertEquals(0, store.getCounter(key, 1));
        assertEquals(1, store.incrementCounter(key, 1));
    }

    @Test
    void isolatedBetweenKeys() {
        store.incrementCounter("feat:t1:a", 60);
        store.incrementCounter("feat:t2:a", 60);
        store.incrementCounter("feat:t2:a", 60);
        assertEquals(1, store.getCounter("feat:t1:a", 60));
        assertEquals(2, store.getCounter("feat:t2:a", 60));
    }
}
