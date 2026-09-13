package com.flowlink.audit;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.common.Ids;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 审计服务：落库（append-only）、按 traceId 追溯、按条件分页查询与决策分布统计。 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final DecisionAuditRepository repository;

    @Transactional
    public DecisionAudit record(String tenantId, String traceId, String ruleSetKey, long ruleVersion, boolean canary,
                                String subjectId, String eventType, String inputSnapshot,
                                String decision, int score, String matchedRuleIds, long latencyMicros) {
        DecisionAudit audit = new DecisionAudit(Ids.uuid(), traceId, tenantId, ruleSetKey, ruleVersion, canary,
                subjectId, eventType, inputSnapshot, decision, score, matchedRuleIds, latencyMicros);
        return repository.save(audit);
    }

    @Transactional(readOnly = true)
    public DecisionAudit requireByTraceId(String tenantId, String traceId) {
        return repository.findFirstByTenantIdAndTraceId(tenantId, traceId)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND, "未找到 traceId=" + traceId + " 的决策记录"));
    }

    @Transactional(readOnly = true)
    public Page<DecisionAudit> query(String tenantId, String decision, Instant from, Instant to, int page, int size) {
        PageRequest pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean hasDecision = decision != null && !decision.isBlank();
        boolean hasRange = from != null && to != null;
        if (hasDecision && hasRange) {
            return repository.findByTenantIdAndDecisionAndCreatedAtBetween(tenantId, decision, from, to, pageable);
        }
        if (hasDecision) {
            return repository.findByTenantIdAndDecision(tenantId, decision, pageable);
        }
        if (hasRange) {
            return repository.findByTenantIdAndCreatedAtBetween(tenantId, from, to, pageable);
        }
        return repository.findByTenantId(tenantId, pageable);
    }

    @Transactional(readOnly = true)
    public List<DecisionAudit> recentForReplay(String tenantId, String ruleSetKey, int limit) {
        return repository.findByTenantIdAndRuleSetKeyOrderByCreatedAtDesc(tenantId, ruleSetKey,
                PageRequest.of(0, Math.min(Math.max(limit, 1), 1000)));
    }

    /** 决策分布统计（按决策类型计数）。 */
    @Transactional(readOnly = true)
    public Map<String, Long> decisionDistribution(String tenantId) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String decision : List.of("REJECT", "REVIEW", "PASS")) {
            long count = repository.countByTenantIdAndDecision(tenantId, decision);
            if (count > 0) {
                result.put(decision, count);
            }
        }
        result.put("TOTAL", repository.countByTenantId(tenantId));
        return result;
    }

    public AuditView toView(DecisionAudit audit) {
        return new AuditView(audit.getId(), audit.getTraceId(), audit.getRuleSetKey(), audit.getRuleVersion(),
                audit.isCanary(), audit.getSubjectId(), audit.getEventType(), audit.getDecision(), audit.getScore(),
                audit.getMatchedRuleIds(), audit.getLatencyMicros(), audit.getCreatedAt());
    }

    public record AuditView(String id,
                            String traceId,
                            String ruleSetKey,
                            long ruleVersion,
                            boolean canary,
                            String subjectId,
                            String eventType,
                            String decision,
                            int score,
                            String matchedRuleIds,
                            long latencyMicros,
                            Instant createdAt) {
    }
}
