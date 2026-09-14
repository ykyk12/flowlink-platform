package com.flowlink.feature;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * 内存滑动窗口计数器（默认实现）。
 * 每个 key 维护一个按毫秒时间戳递增的事件队列，读取窗口内的元素数量即为计数；
 * 定期清理过期队列，避免长时间运行内存膨胀。
 *
 * <p>每个桶记录其声明过的最大窗口（毫秒），定时清理<b>按桶自己的窗口</b>裁剪事件，
 * 而不是用一个全局固定 horizon —— 否则窗口大于该 horizon 的事件会被误删、计数偏低。
 */
@Component
@ConditionalOnProperty(name = "app.feature-store", havingValue = "memory", matchIfMissing = true)
public class InMemoryFeatureStore implements FeatureStore {

    /** 单个桶：事件时间戳队列 + 该桶声明过的最大窗口（毫秒）。 */
    private static final class Bucket {
        final Deque<Long> events = new ArrayDeque<>();
        long windowMillis;
    }

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public InMemoryFeatureStore() {
        this(System::currentTimeMillis);
    }

    /** 测试注入可控时钟。 */
    InMemoryFeatureStore(LongSupplier clock) {
        this.clock = clock;
    }

    @Override
    public long incrementCounter(String key, long windowSeconds) {
        long now = clock.getAsLong();
        long windowMillis = windowSeconds * 1000L;
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket());
        synchronized (bucket) {
            bucket.events.addLast(now);
            // 同一 key 可能被不同窗口查询复用，取最大窗口作为清理 horizon，
            // 避免按小窗口清理时误删长窗口仍有效的事件
            if (windowMillis > bucket.windowMillis) {
                bucket.windowMillis = windowMillis;
            }
            return countInWindow(bucket, now, windowMillis);
        }
    }

    @Override
    public long getCounter(String key, long windowSeconds) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) {
            return 0L;
        }
        synchronized (bucket) {
            return countInWindow(bucket, clock.getAsLong(), windowSeconds * 1000L);
        }
    }

    @Override
    public String backend() {
        return "memory";
    }

    private long countInWindow(Bucket bucket, long now, long windowMillis) {
        long threshold = now - windowMillis;
        while (!bucket.events.isEmpty() && bucket.events.peekFirst() < threshold) {
            bucket.events.pollFirst();
        }
        return bucket.events.size();
    }

    /** 每 30 秒清理一次：按各桶自己声明的窗口裁剪，仅回收已空的桶（无扫全表热点）。 */
    @Scheduled(fixedDelay = 30_000L)
    public void cleanup() {
        cleanup(clock.getAsLong());
    }

    /** 测试/运维：按指定时刻清理。 */
    void cleanup(long nowMillis) {
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            synchronized (bucket) {
                countInWindow(bucket, nowMillis, bucket.windowMillis);
                return bucket.events.isEmpty();
            }
        });
    }

    /** 测试与运维使用：当前维护的 key 数量。 */
    public int trackedKeys() {
        return buckets.size();
    }
}
