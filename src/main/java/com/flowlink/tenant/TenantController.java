package com.flowlink.tenant;

import com.flowlink.common.ApiResponse;
import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 租户管理 API（管理面）：用 X-Admin-Key 鉴权，而不是租户 API Key（避免自举问题）。
 * 生产环境应把 admin key 换成一等公民的运维身份体系（RBAC + 双人复核）。
 */
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    public static final String ADMIN_HEADER = "X-Admin-Key";

    private final TenantService tenantService;

    @Value("${app.admin-key:flowlink-admin-key}")
    private String adminKey;

    @PostMapping
    public ApiResponse<TenantDtos.TenantCreatedView> create(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                            @Valid @RequestBody TenantDtos.CreateTenantRequest request) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.create(request));
    }

    @GetMapping
    public ApiResponse<List<TenantDtos.TenantView>> list(@RequestHeader(value = ADMIN_HEADER, required = false) String key) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.list());
    }

    @PostMapping("/{tenantId}/rotate-key")
    public ApiResponse<TenantDtos.TenantCreatedView> rotate(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                            @PathVariable String tenantId) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.rotateKey(tenantId));
    }

    @PostMapping("/{tenantId}/quota")
    public ApiResponse<TenantDtos.TenantView> updateQuota(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                          @PathVariable String tenantId,
                                                          @Valid @RequestBody TenantDtos.UpdateQuotaRequest request) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.updateQuota(tenantId, request.quotaPerMinute()));
    }

    @PostMapping("/{tenantId}/enabled")
    public ApiResponse<TenantDtos.TenantView> setEnabled(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                         @PathVariable String tenantId,
                                                         @RequestParam boolean enabled) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.setEnabled(tenantId, enabled));
    }

    private void requireAdmin(String provided) {
        if (provided == null || !provided.equals(adminKey)) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "缺少或无效的 " + ADMIN_HEADER);
        }
    }
}
