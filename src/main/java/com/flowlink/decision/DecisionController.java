package com.flowlink.decision;

import com.flowlink.audit.AuditService;
import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 在线决策 API（核心链路）。 */
@Tag(name = "决策", description = "在线风控决策：单条评估、批量评估、按 traceId 追溯")
@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;
    private final AuditService auditService;

    @Operation(summary = "单条决策评估", description = "按规则集实时评估一次事件，返回决策/评分/命中规则，可选返回求值轨迹。")
    @PostMapping("/evaluate")
    public ApiResponse<EvaluateResponse> evaluate(@Valid @RequestBody EvaluateRequest request) {
        return ApiResponse.ok(decisionService.evaluate(TenantContext.requireTenantId(), request));
    }

    @Operation(summary = "批量决策评估", description = "一次请求评估多条事件，条数上限由 app.engine.max-batch-size 控制。")
    @PostMapping("/batch")
    public ApiResponse<List<EvaluateResponse>> batch(@Valid @RequestBody BatchEvaluateRequest request) {
        return ApiResponse.ok(decisionService.evaluateBatch(TenantContext.requireTenantId(), request));
    }

    @Operation(summary = "按 traceId 追溯决策", description = "返回该次决策的审计记录，用于排查与对账。")
    @GetMapping("/{traceId}")
    public ApiResponse<AuditService.AuditView> trace(@PathVariable String traceId) {
        return ApiResponse.ok(auditService.toView(
                auditService.requireByTraceId(TenantContext.requireTenantId(), traceId)));
    }
}
