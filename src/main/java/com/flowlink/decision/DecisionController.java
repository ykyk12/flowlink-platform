package com.flowlink.decision;

import com.flowlink.audit.AuditService;
import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
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
@RestController
@RequestMapping("/api/v1/decisions")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionService decisionService;
    private final AuditService auditService;

    @PostMapping(":evaluate")
    public ApiResponse<EvaluateResponse> evaluate(@Valid @RequestBody EvaluateRequest request) {
        return ApiResponse.ok(decisionService.evaluate(TenantContext.requireTenantId(), request));
    }

    @PostMapping(":batch")
    public ApiResponse<List<EvaluateResponse>> batch(@Valid @RequestBody BatchEvaluateRequest request) {
        return ApiResponse.ok(decisionService.evaluateBatch(TenantContext.requireTenantId(), request));
    }

    /** 按 traceId 追溯单次决策（返回审计记录）。 */
    @GetMapping("/{traceId}")
    public ApiResponse<AuditService.AuditView> trace(@PathVariable String traceId) {
        return ApiResponse.ok(auditService.toView(
                auditService.requireByTraceId(TenantContext.requireTenantId(), traceId)));
    }
}
