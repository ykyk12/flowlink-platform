package com.flowlink.feature;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存滑动窗口计数器（默认实现）。
 * 每个 key 维护一个按毫秒时间戳递增的事件队列，读取窗口内的元素数量即为计数；
 * 定期清理过期队列，避免长时间运行内存膨胀。
 */
@Component
@ConditionalOnProperty(name = "app.feature-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFeatureStore implements FeatureStore {

    private final Map<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    @Override
    public long incrementCounter(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = buckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(now);
            return countInWindow(deque, now, windowSeconds);
        }
    }

    @Override
    public long getCounter(String key, long windowSeconds) {
        Deque<Long> deque = buckets.get(key);
        if (deque == null) {
            return 0L;
        }
        synchronized (deque) {
            return countInWindow(deque, System.currentTimeMillis(), windowSeconds);
        }
    }

    @Override
    public String backend() {
        return "memory";
    }

    private long countInWindow(Deque<Long> deque, long now, long windowSeconds) {
        long threshold = now - windowSeconds * 1000L;
        while (!deque.isEmpty() && deque.peekFirst() < threshold) {
            deque.pollFirst();
        }
        return deque.size();
    }

    /** 每 30 秒清理一次空桶与全过期桶（无扫全表热点，复杂度 O(桶数)）。 */
    @Scheduled(fixedDelay = 30_000L)
    public void cleanup() {
        long now = System.currentTimeMillis();
        buckets.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                while (!deque.isEmpty() && deque.peekFirst() < now - 300_000L) {
                    deque.pollFirst();
                }
                return deque.isEmpty();
            }
        });
    }

    /** 测试与运维使用：当前维护的 key 数量。 */
    public int trackedKeys() {
        return buckets.size();
    }
}
