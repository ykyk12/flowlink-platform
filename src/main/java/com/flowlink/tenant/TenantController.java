package com.flowlink.tenant;

import com.flowlink.common.ApiResponse;
import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "租户管理", description = "管理面接口，需 X-Admin-Key 鉴权：开通租户、轮换密钥、调整配额、启停")
@RestController
@RequestMapping("/api/v1/admin/tenants")
@RequiredArgsConstructor
public class TenantController {

    public static final String ADMIN_HEADER = "X-Admin-Key";

    private final TenantService tenantService;

    @Value("${app.admin-key:flowlink-admin-key}")
    private String adminKey;

    @Operation(summary = "开通租户", description = "返回首次生成的租户 API Key，仅本次返回，请妥善保管。")
    @PostMapping
    public ApiResponse<TenantDtos.TenantCreatedView> create(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                            @Valid @RequestBody TenantDtos.CreateTenantRequest request) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.create(request));
    }

    @Operation(summary = "列出全部租户")
    @GetMapping
    public ApiResponse<List<TenantDtos.TenantView>> list(@RequestHeader(value = ADMIN_HEADER, required = false) String key) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.list());
    }

    @Operation(summary = "轮换租户 API Key", description = "旧 key 立即失效，返回新的明文 key。")
    @PostMapping("/{tenantId}/rotate-key")
    public ApiResponse<TenantDtos.TenantCreatedView> rotate(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                            @PathVariable String tenantId) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.rotateKey(tenantId));
    }

    @Operation(summary = "调整租户每分钟配额")
    @PostMapping("/{tenantId}/quota")
    public ApiResponse<TenantDtos.TenantView> updateQuota(@RequestHeader(value = ADMIN_HEADER, required = false) String key,
                                                          @PathVariable String tenantId,
                                                          @Valid @RequestBody TenantDtos.UpdateQuotaRequest request) {
        requireAdmin(key);
        return ApiResponse.ok(tenantService.updateQuota(tenantId, request.quotaPerMinute()));
    }

    @Operation(summary = "启用/停用租户")
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
