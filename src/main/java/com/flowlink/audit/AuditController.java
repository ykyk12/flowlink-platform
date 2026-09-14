package com.flowlink.audit;

import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "审计查询", description = "决策审计记录检索、单条追溯、决策分布统计")
@RestController
@RequestMapping("/api/v1/audits")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    @Operation(summary = "分页检索审计记录", description = "可按决策结果、时间区间过滤。")
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

    @Operation(summary = "按 traceId 查询单条审计记录")
    @GetMapping("/{traceId}")
    public ApiResponse<AuditService.AuditView> getByTraceId(@PathVariable String traceId) {
        return ApiResponse.ok(auditService.toView(
                auditService.requireByTraceId(TenantContext.requireTenantId(), traceId)));
    }

    @Operation(summary = "决策分布统计", description = "返回 APPROVE/REVIEW/REJECT 等各决策结果的计数。")
    @GetMapping("/stats")
    public ApiResponse<Map<String, Long>> stats() {
        return ApiResponse.ok(auditService.decisionDistribution(TenantContext.requireTenantId()));
    }

    @Operation(summary = "取最近 N 条决策", description = "供决策回放接口复用历史输入。")
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
