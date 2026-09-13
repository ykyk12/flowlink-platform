package com.flowlink.audit;

import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** 审计查询 API：追溯单次决策、按条件检索、决策分布统计。 */
@RestController
@RequestMapping("/api/v1/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @GetMapping
    public ApiResponse<Page<AuditService.AuditView>> query(
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<AuditService.AuditView> result = auditService
                .query(TenantContext.requireTenantId(), decision, from, to, page, size)
                .map(auditService::toView);
        return ApiResponse.ok(result);
    }

    @GetMapping("/{traceId}")
    public ApiResponse<AuditService.AuditView> getByTraceId(@PathVariable String traceId) {
        return ApiResponse.ok(auditService.toView(
                auditService.requireByTraceId(TenantContext.requireTenantId(), traceId)));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(auditService.decisionDistribution(TenantContext.requireTenantId()));
    }

    @GetMapping("/recent")
    public ApiResponse<List<AuditService.AuditView>> recent(
            @RequestParam String ruleSetKey,
            @RequestParam(defaultValue = "20") int limit) {
        List<AuditService.AuditView> views = auditService
                .recentForReplay(TenantContext.requireTenantId(), ruleSetKey, limit).stream()
                .map(auditService::toView)
                .toList();
        return ApiResponse.ok(views);
    }
}
