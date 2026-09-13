package com.flowlink.ruleset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 规则版本：内容一旦创建不可修改，只能新建版本（可回滚、可审计）。 */
@Entity
@Table(name = "rule_version",
        uniqueConstraints = @UniqueConstraint(name = "uk_rule_version", columnNames = {"rule_set_id", "version"}))
@Getter
@Setter
@NoArgsConstructor
public class RuleVersionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "rule_set_id", nullable = false, length = 36)
    private String ruleSetId;

    @Column(nullable = false)
    private long version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private VersionStatus status = VersionStatus.DRAFT;

    /** DSL 原文（YAML 或 JSON）。用 varchar(65535) 而非 @Lob：避免 Hibernate 在 PostgreSQL 上映射成 oid */
    @Column(nullable = false, length = 65535)
    private String content;

    @Column(length = 255)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public RuleVersionEntity(String id, String tenantId, String ruleSetId, long version, String content, String note) {
        this.id = id;
        this.tenantId = tenantId;
        this.ruleSetId = ruleSetId;
        this.version = version;
        this.content = content;
        this.note = note;
        this.status = VersionStatus.DRAFT;
        this.createdAt = Instant.now();
    }
}
