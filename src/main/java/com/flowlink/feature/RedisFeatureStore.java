package com.flowlink.feature;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis 滑动窗口计数器（app.feature-store=redis 时启用）。
 * 用 ZSET 存事件时间戳，Lua 脚本保证"清理过期 + 写入 + 计数"的原子性，
 * 多实例部署时计数一致（内存实现只保证单机）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.feature-store", havingValue = "redis")
public class RedisFeatureStore implements FeatureStore {

    private static final String INCR_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            local member = ARGV[3]
            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMillis)
            redis.call('ZADD', key, now, member)
            redis.call('PEXPIRE', key, windowMillis)
            return redis.call('ZCARD', key)
            """;

    private static final String GET_LUA = """
            local key = KEYS[1]
            local now = tonumber(ARGV[1])
            local windowMillis = tonumber(ARGV[2])
            redis.call('ZREMRANGEBYSCORE', key, 0, now - windowMillis)
            return redis.call('ZCARD', key)
            """;

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> INCR_SCRIPT = new DefaultRedisScript<>(INCR_LUA, Long.class);
    private static final DefaultRedisScript<Long> GET_SCRIPT = new DefaultRedisScript<>(GET_LUA, Long.class);

    @Override
    public long incrementCounter(String key, long windowSeconds) {
        long now = System.currentTimeMillis();
        String member = now + "-" + Thread.currentThread().getId() + "-" + System.nanoTime();
        Long result = redisTemplate.execute(INCR_SCRIPT, List.of(redisKey(key)),
                String.valueOf(now), String.valueOf(windowSeconds * 1000L), member);
        return result == null ? 0L : result;
    }

    @Override
    public long getCounter(String key, long windowSeconds) {
        Long result = redisTemplate.execute(GET_SCRIPT, List.of(redisKey(key)),
                String.valueOf(System.currentTimeMillis()), String.valueOf(windowSeconds * 1000L));
        return result == null ? 0L : result;
    }

    @Override
    public String backend() {
        return "redis";
    }

    private String redisKey(String key) {
        return "flowlink:counter:" + key;
    }
}
