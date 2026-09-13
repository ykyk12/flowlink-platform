package com.flowlink.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DecisionAuditRepository extends JpaRepository<DecisionAudit, String> {

    Optional<DecisionAudit> findFirstByTenantIdAndTraceId(String tenantId, String traceId);

    Page<DecisionAudit> findByTenantId(String tenantId, Pageable pageable);

    Page<DecisionAudit> findByTenantIdAndDecision(String tenantId, String decision, Pageable pageable);

    Page<DecisionAudit> findByTenantIdAndCreatedAtBetween(String tenantId, Instant from, Instant to, Pageable pageable);

    Page<DecisionAudit> findByTenantIdAndDecisionAndCreatedAtBetween(String tenantId, String decision,
                                                                    Instant from, Instant to, Pageable pageable);

    long countByTenantIdAndDecision(String tenantId, String decision);

    long countByTenantId(String tenantId);

    List<DecisionAudit> findByTenantIdAndRuleSetKeyOrderByCreatedAtDesc(String tenantId, String ruleSetKey, Pageable pageable);
}
