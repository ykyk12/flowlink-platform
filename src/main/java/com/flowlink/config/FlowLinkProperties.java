package com.flowlink.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** app.* 配置。 */
@Data
@ConfigurationProperties(prefix = "app")
public class FlowLinkProperties {

    /** 特征存储实现：memory（默认）| redis */
    private String featureStore = "memory";

    /** 单租户每分钟评估配额 */
    private int defaultQuotaPerMinute = 600;

    /** 幂等键缓存 TTL（秒） */
    private int idempotencyTtlSeconds = 600;

    private Engine engine = new Engine();
    private Audit audit = new Audit();

    /** 启动时写入演示数据 */
    private boolean bootstrapDemoData = true;

    @Data
    public static class Engine {
        private int maxRulesPerSet = 500;
        private int maxConditionDepth = 8;
        private int maxBatchSize = 200;
    }

    @Data
    public static class Audit {
        private boolean enabled = true;
    }
}
