package com.flowlink.feature;

/**
 * 特征存储抽象：在线特征与窗口计数。
 * 实现：
 *  - InMemoryFeatureStore：分桶滑动窗口，零依赖（默认，适合单机/压测）
 *  - RedisFeatureStore：Redis ZSET 滑动窗口 Lua 脚本，多实例计数一致
 */
public interface FeatureStore {

    /** 在 windowSeconds 窗口内自增并返回当前累计值。 */
    long incrementCounter(String key, long windowSeconds);

    /** 读取窗口内当前累计值（不自增）。 */
    long getCounter(String key, long windowSeconds);

    /** 实现标识，用于健康检查与指标标签。 */
    String backend();
}
