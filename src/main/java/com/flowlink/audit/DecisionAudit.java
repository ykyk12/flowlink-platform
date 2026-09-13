package com.flowlink.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 决策审计：append-only，含输入快照，支持按 traceId 追溯与历史回放。 */
@Entity
@Table(name = "decision_audit", indexes = {
        @Index(name = "idx_audit_tenant_created", columnList = "tenant_id,created_at"),
        @Index(name = "idx_audit_trace", columnList = "trace_id"),
        @Index(name = "idx_audit_ruleset", columnList = "tenant_id,rule_set_key,created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class DecisionAudit {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "rule_set_key", nullable = false, length = 64)
    private String ruleSetKey;

    @Column(name = "rule_version", nullable = false)
    private long ruleVersion;

    @Column(nullable = false)
    private boolean canary;

    @Column(name = "subject_id", length = 128)
    private String subjectId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    /** 输入快照（features/attributes/counters），回放时用它重建上下文；varchar(65535) 兼容 H2 与 PostgreSQL */
    @Column(name = "input_snapshot", nullable = false, length = 65535)
    private String inputSnapshot;

    @Column(nullable = false, length = 32)
    private String decision;

    @Column(nullable = false)
    private int score;

    @Column(name = "matched_rule_ids", length = 512)
    private String matchedRuleIds;

    @Column(name = "latency_micros", nullable = false)
    private long latencyMicros;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DecisionAudit(String id, String traceId, String tenantId, String ruleSetKey, long ruleVersion,
                         boolean canary, String subjectId, String eventType, String inputSnapshot,
                         String decision, int score, String matchedRuleIds, long latencyMicros) {
        this.id = id;
        this.traceId = traceId;
        this.tenantId = tenantId;
        this.ruleSetKey = ruleSetKey;
        this.ruleVersion = ruleVersion;
        this.canary = canary;
        this.subjectId = subjectId;
        this.eventType = eventType;
        this.inputSnapshot = inputSnapshot;
        this.decision = decision;
        this.score = score;
        this.matchedRuleIds = matchedRuleIds;
        this.latencyMicros = latencyMicros;
        this.createdAt = Instant.now();
    }
}
