package com.flowlink.replay;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlink.audit.AuditService;
import com.flowlink.audit.DecisionAudit;
import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.engine.DecisionContext;
import com.flowlink.engine.DecisionEngine;
import com.flowlink.engine.DecisionResult;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 决策回放：取历史审计的输入快照，用目标版本重跑，得出"若按新规则执行，历史决策会怎么变"。
 *
 * 语义说明：回放**不重新自增窗口计数**，使用审计时记录的计数快照，保证可复现。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayService {

    private final RuleSetService ruleSetService;
    private final AuditService auditService;
    private final DecisionEngine engine;
    private final ObjectMapper objectMapper;

    public ReplayDtos.ReplayDiff replay(String tenantId, ReplayDtos.ReplayRequest request) {
        int limit = request.limit() == null ? 200 : request.limit();
        RuleSetDtos.ContentView content = ruleSetService.getVersionContent(
                tenantId, request.ruleSetKey(), request.targetVersion());
        CompiledRuleSet targetCompiled = ruleSetService.compile(content.content());

        List<DecisionAudit> audits = auditService.recentForReplay(tenantId, request.ruleSetKey(), limit);
        List<ReplayDtos.Sample> samples = new ArrayList<>(audits.size());
        int changed = 0;

        for (DecisionAudit audit : audits) {
            Snapshot snapshot = readSnapshot(audit.getInputSnapshot());
            DecisionContext context = DecisionContext.builder()
                    .tenantId(tenantId)
                    .subjectId(snapshot.subjectId())
                    .eventType(snapshot.eventType())
                    .features(snapshot.features())
                    .attributes(snapshot.attributes())
                    .counters(snapshot.counters())
                    .build();
            DecisionResult result = engine.evaluate(request.ruleSetKey(), request.targetVersion(), targetCompiled, context);
            String newMatched = String.join(",", result.getMatchedRules().stream()
                    .map(DecisionResult.MatchedRule::ruleId).toList());
            boolean same = audit.getDecision().equals(result.getDecision())
                    && audit.getScore() == result.getScore();
            if (!same) {
                changed++;
            }
            samples.add(new ReplayDtos.Sample(audit.getTraceId(), audit.getDecision(), result.getDecision(),
                    audit.getScore(), result.getScore(),
                    audit.getMatchedRuleIds() == null ? "" : audit.getMatchedRuleIds(),
                    newMatched, !same));
        }

        int total = samples.size();
        double rate = total == 0 ? 0.0 : (double) changed / total;
        log.info("租户 {} 规则集 {} 回放目标 v{}：共 {} 条，变化 {} 条（{}）",
                tenantId, request.ruleSetKey(), request.targetVersion(), total, changed,
                String.format("%.2f%%", rate * 100));
        return new ReplayDtos.ReplayDiff(total, changed, total - changed, rate, request.targetVersion(), samples);
    }

    @SuppressWarnings("unchecked")
    private Snapshot readSnapshot(String json) {
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {
            });
            Map<String, Object> features = (Map<String, Object>) raw.getOrDefault("features", Map.of());
            Map<String, Object> attributes = (Map<String, Object>) raw.getOrDefault("attributes", Map.of());
            Map<String, Long> counters = new LinkedHashMap<>();
            Object counterRaw = raw.get("counters");
            if (counterRaw instanceof Map<?, ?> map) {
                map.forEach((name, value) -> {
                    if (name != null && value != null) {
                        counters.put(String.valueOf(name), Long.valueOf(String.valueOf(value)));
                    }
                });
            }
            return new Snapshot((String) raw.get("subjectId"), (String) raw.get("eventType"),
                    features, attributes, counters);
        } catch (Exception e) {
            log.warn("审计快照解析失败，按空上下文回放：{}", e.getMessage());
            return new Snapshot(null, null, Map.of(), Map.of(), Map.of());
        }
    }

    private record Snapshot(String subjectId,
                            String eventType,
                            Map<String, Object> features,
                            Map<String, Object> attributes,
                            Map<String, Long> counters) {
    }
}
