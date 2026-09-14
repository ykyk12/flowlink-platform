package com.flowlink.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** 决策相关指标：决策分布、耗时、灰度分流、幂等命中。 */
@Component
public class DecisionMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> decisionCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> canaryCounters = new ConcurrentHashMap<>();
    private final Timer latencyTimer;
    private final Counter idempotentHits;
    private final Counter engineErrors;

    public DecisionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.latencyTimer = Timer.builder("flowlink_decision_latency")
                .description("决策耗时（含审计落库）")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
        this.idempotentHits = Counter.builder("flowlink_idempotent_hits_total")
                .description("幂等键命中次数")
                .register(registry);
        this.engineErrors = Counter.builder("flowlink_engine_errors_total")
                .description("决策引擎求值抛异常次数")
                .register(registry);
    }

    public void recordDecision(String decision) {
        decisionCounters.computeIfAbsent(decision, d -> Counter.builder("flowlink_decisions_total")
                .tag("decision", d)
                .register(registry)).increment();
    }

    public void recordCanary(long version) {
        canaryCounters.computeIfAbsent(String.valueOf(version), v -> Counter.builder("flowlink_canary_total")
                .tag("version", v)
                .register(registry)).increment();
    }

    public void recordLatency(long micros) {
        latencyTimer.record(micros, TimeUnit.MICROSECONDS);
    }

    public void recordIdempotentHit() {
        idempotentHits.increment();
    }

    public void recordEngineError() {
        engineErrors.increment();
    }
}
