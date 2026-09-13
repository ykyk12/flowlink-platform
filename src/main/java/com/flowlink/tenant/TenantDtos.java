package com.flowlink.tenant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/** 租户管理 DTO（管理面，需 X-Admin-Key）。 */
public final class TenantDtos {

    private TenantDtos() {
    }

    public record CreateTenantRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 32) String plan,
            @Min(0) @Max(1_000_000) Integer quotaPerMinute) {
    }

    public record UpdateQuotaRequest(@Min(0) @Max(1_000_000) int quotaPerMinute) {
    }

    /** API Key 明文只在创建/轮换响应中出现一次。 */
    public record TenantCreatedView(String id, String name, String plan, int quotaPerMinute, String apiKey) {
    }

    public record TenantView(String id,
                             String name,
                             String plan,
                             int quotaPerMinute,
                             boolean enabled,
                             Instant createdAt) {
    }

    public record TenantList(List<TenantView> tenants, long quotaUsageOfCurrentWindowIgnored) {
    }
}
