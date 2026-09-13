package com.flowlink.tenant;

import com.flowlink.feature.FeatureStore;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 租户配额限流：每租户每分钟评估次数，滑动窗口由 FeatureStore 提供（内存或 Redis 一致）。
 */
@Service
@RequiredArgsConstructor
public class QuotaService {

    private static final long WINDOW_SECONDS = 60L;

    private final FeatureStore featureStore;
    private final MeterRegistry meterRegistry;

    public QuotaDecision tryAcquire(Tenant tenant) {
        if (tenant.getQuotaPerMinute() <= 0) {
            return new QuotaDecision(true, 0);
        }
        String key = "quota:" + tenant.getId();
        long used = featureStore.incrementCounter(key, WINDOW_SECONDS);
        if (used > tenant.getQuotaPerMinute()) {
            Counter.builder("flowlink_quota_rejected_total")
                    .tag("tenant", tenant.getId())
                    .register(meterRegistry)
                    .increment();
            long nowSecond = Instant.now().getEpochSecond();
            long retryAfter = WINDOW_SECONDS - Math.floorMod(nowSecond, WINDOW_SECONDS);
            return new QuotaDecision(false, Math.max(1, retryAfter));
        }
        return new QuotaDecision(true, 0);
    }

    public long currentUsage(String tenantId) {
        return featureStore.getCounter("quota:" + tenantId, WINDOW_SECONDS);
    }

    public record QuotaDecision(boolean allowed, long retryAfterSeconds) {
    }
}
