package com.flowlink.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlink.audit.AuditService;
import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.common.Hashes;
import com.flowlink.common.Ids;
import com.flowlink.common.TraceIdFilter;
import com.flowlink.config.FlowLinkProperties;
import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.engine.DecisionContext;
import com.flowlink.engine.DecisionEngine;
import com.flowlink.engine.DecisionResult;
import com.flowlink.feature.FeatureStore;
import com.flowlink.metrics.DecisionMetrics;
import com.flowlink.ruleset.RuleCache;
import com.flowlink.ruleset.RuleSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 决策编排：幂等 → 上下文（含窗口计数）→ 规则集缓存（含灰度路由）→ 引擎 → 审计 → 指标。
 * 单次决策的完整链路都在这里，便于面试时逐段讲清。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DecisionService {

    private final RuleSetService ruleSetService;
    private final RuleCache ruleCache;
    private final DecisionEngine engine;
    private final FeatureStore featureStore;
    private final IdempotencyStore idempotencyStore;
    private final AuditService auditService;
    private final DecisionMetrics metrics;
    private final FlowLinkProperties properties;
    private final ObjectMapper objectMapper;

    public EvaluateResponse evaluate(String tenantId, EvaluateRequest request) {
        Optional<EvaluateResponse> cached = idempotencyStore.get(tenantId, request.idempotencyKey());
        if (cached.isPresent()) {
            metrics.recordIdempotentHit();
            return replayOf(cached.get());
        }

        String traceId = currentTraceId();
        DecisionContext context = buildContext(tenantId, request);

        RuleCache.CachedRuleSet ruleSet = ruleCache.get(tenantId, request.ruleSetKey())
                .orElseGet(() -> ruleSetService.refreshCache(tenantId, request.ruleSetKey()));

        boolean canary = false;
        long version;
        CompiledRuleSet compiled;
        if (ruleSet.hasCanary() && routeToCanary(request.subjectId(), ruleSet.canaryPercent())) {
            canary = true;
            version = ruleSet.canaryVersion();
            compiled = ruleSet.canary();
        } else {
            version = ruleSet.activeVersion();
            compiled = ruleSet.active();
        }

        DecisionResult result = engine.evaluate(request.ruleSetKey(), version, compiled, context);
        EvaluateResponse response = new EvaluateResponse(
                traceId,
                request.ruleSetKey(),
                version,
                canary,
                result.getDecision(),
                result.getScore(),
                result.isFallbackUsed(),
                result.reason(),
                result.getMatchedRules().stream().map(EvaluateResponse.MatchedRuleView::from).toList(),
                request.explain() ? result.getTraces() : null,
                result.getLatencyMicros(),
                false);

        if (properties.getAudit().isEnabled()) {
            auditService.record(tenantId, traceId, request.ruleSetKey(), version, canary,
                    request.subjectId(), request.eventType(), snapshot(request, context.getCounters()),
                    result.getDecision(), result.getScore(), response.matchedRuleIdsCsv(), result.getLatencyMicros());
        }

        metrics.recordDecision(result.getDecision());
        if (canary) {
            metrics.recordCanary(version);
        }
        metrics.recordLatency(result.getLatencyMicros());
        idempotencyStore.put(tenantId, request.idempotencyKey(), response, properties.getIdempotencyTtlSeconds());
        return response;
    }

    public List<EvaluateResponse> evaluateBatch(String tenantId, BatchEvaluateRequest request) {
        int max = properties.getEngine().getMaxBatchSize();
        if (request.items().size() > max) {
            throw new BizException(ErrorCode.BAD_REQUEST, "批量条数 " + request.items().size() + " 超过上限 " + max);
        }
        List<EvaluateResponse> responses = new ArrayList<>(request.items().size());
        for (EvaluateRequest item : request.items()) {
            responses.add(evaluate(tenantId, item));
        }
        return responses;
    }

    private DecisionContext buildContext(String tenantId, EvaluateRequest request) {
        Map<String, Long> counters = new LinkedHashMap<>();
        if (request.counters() != null) {
            request.counters().forEach((name, windowSeconds) -> {
                if (name == null || name.isBlank() || windowSeconds == null || windowSeconds <= 0) {
                    return;
                }
                long value = featureStore.incrementCounter("feat:" + tenantId + ":" + name, windowSeconds);
                counters.put(name, value);
            });
        }
        return DecisionContext.builder()
                .tenantId(tenantId)
                .subjectId(request.subjectId())
                .eventType(request.eventType())
                .features(request.features() == null ? Map.of() : request.features())
                .attributes(request.attributes() == null ? Map.of() : request.attributes())
                .counters(counters)
                .build();
    }

    /** 灰度路由：同一 subjectId 稳定落同一分支；未提供主体时一律走生效版本。 */
    private boolean routeToCanary(String subjectId, int canaryPercent) {
        if (subjectId == null || subjectId.isBlank() || canaryPercent <= 0) {
            return false;
        }
        return Hashes.stableBucket(subjectId, 100) < canaryPercent;
    }

    /**
     * 输入快照：features/attributes + **计数快照实际值**（回放时不重新自增，保证可复现）。
     */
    private String snapshot(EvaluateRequest request, Map<String, Long> counters) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("subjectId", request.subjectId());
        snapshot.put("eventType", request.eventType());
        snapshot.put("features", request.features() == null ? Map.of() : request.features());
        snapshot.put("attributes", request.attributes() == null ? Map.of() : request.attributes());
        snapshot.put("counters", counters == null ? Map.of() : counters);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.warn("决策输入快照序列化失败：{}", e.getMessage());
            return "{}";
        }
    }

    private EvaluateResponse replayOf(EvaluateResponse original) {
        return new EvaluateResponse(original.traceId(), original.ruleSetKey(), original.version(), original.canary(),
                original.decision(), original.score(), original.fallbackUsed(), original.reason(),
                original.matchedRules(), original.traces(), original.latencyMicros(), true);
    }

    private String currentTraceId() {
        String traceId = MDC.get(TraceIdFilter.TRACE_ID);
        return traceId == null || traceId.isBlank() ? Ids.shortId() : traceId;
    }
}
