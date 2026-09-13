package com.flowlink.ruleset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 规则集：一个租户下按 key 唯一，持有点向当前生效/灰度版本。 */
@Entity
@Table(name = "rule_set",
        uniqueConstraints = @UniqueConstraint(name = "uk_rule_set_tenant_key", columnNames = {"tenant_id", "rule_key"}))
@Getter
@Setter
@NoArgsConstructor
public class RuleSetEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "tenant_id", nullable = false, length = 36)
    private String tenantId;

    @Column(name = "rule_key", nullable = false, length = 64)
    private String key;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "active_version_id", length = 36)
    private String activeVersionId;

    @Column(name = "canary_version_id", length = 36)
    private String canaryVersionId;

    /** 灰度比例 0..100（按 subjectId 稳定哈希路由） */
    @Column(name = "canary_percent", nullable = false)
    private int canaryPercent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public RuleSetEntity(String id, String tenantId, String key, String name) {
        this.id = id;
        this.tenantId = tenantId;
        this.key = key;
        this.name = name;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
}
