package com.flowlink.feature;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

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

    /**
     * 回归：定时清理曾硬编码按 300s 裁剪事件，导致窗口大于 300s 的计数在清理后被误删、
     * 计数从真实值掉到 0（窗口内仍有效的事件被当成过期淘汰）。
     * 清理必须按每个桶自己声明的窗口裁剪，不能用全局固定 horizon。
     */
    @Test
    void cleanupDoesNotEvictEventsStillInsideALongerWindow() {
        AtomicLong now = new AtomicLong(0L);
        InMemoryFeatureStore timed = new InMemoryFeatureStore(now::get);

        String key = "feat:t1:slow_window_600s";
        // t=0 写入一个事件，窗口 600s（远大于旧版清理硬编码的 300s）
        timed.incrementCounter(key, 600);

        // 推进 400s：该事件距现在 400s，仍在 600s 窗口内
        now.set(400_000L);
        timed.cleanup(now.get());

        // 旧实现：按固定 300s 裁剪 → threshold=100000，事件 0 < 100000 被淘汰，桶被移除 → 计数变 0
        // 新实现：按本桶窗口 600s 裁剪 → threshold=-200000，事件保留 → 计数仍为 1
        assertEquals(1, timed.getCounter(key, 600),
                "窗口内仍有效的事件不应被定时清理误删");
        assertEquals(1, timed.trackedKeys(),
                "仍在窗口内的桶不应被清理移除");

        // 推进到 700s：事件已出 600s 窗口，清理后应被正确淘汰
        now.set(700_000L);
        timed.cleanup(now.get());
        assertEquals(0, timed.getCounter(key, 600));
        assertEquals(0, timed.trackedKeys(), "事件全部过期后空桶应被回收");
    }
}
