package com.flowlink.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** 租户：持有 API Key 摘要与配额。 */
@Entity
@Table(name = "tenant", uniqueConstraints = @UniqueConstraint(name = "uk_tenant_api_key", columnNames = "api_key_hash"))
@Getter
@Setter
@NoArgsConstructor
public class Tenant {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    /** API Key 的 SHA-256 摘要（明文仅在创建/轮换响应中返回一次） */
    @Column(name = "api_key_hash", nullable = false, length = 64)
    private String apiKeyHash;

    /** 套餐名（展示用） */
    @Column(length = 32)
    private String plan;

    /** 每分钟评估配额 */
    @Column(name = "quota_per_minute", nullable = false)
    private int quotaPerMinute;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Tenant(String id, String name, String apiKeyHash, String plan, int quotaPerMinute) {
        this.id = id;
        this.name = name;
        this.apiKeyHash = apiKeyHash;
        this.plan = plan;
        this.quotaPerMinute = quotaPerMinute;
        this.createdAt = Instant.now();
    }
}
