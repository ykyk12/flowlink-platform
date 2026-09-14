package com.flowlink.replay;

import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 规则变更影响评估 API：历史决策在目标版本上的回放差异。 */
@Tag(name = "决策回放", description = "用历史决策样本在目标版本上回放，输出命中差异")
@RestController
@RequestMapping("/api/v1/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    @Operation(summary = "回放并对比", description = "把历史决策在目标版本上重放，统计决策漂移与新增命中规则。")
    @PostMapping
    public ApiResponse<ReplayDtos.ReplayDiff> replay(@Valid @RequestBody ReplayDtos.ReplayRequest request) {
        return ApiResponse.ok(replayService.replay(TenantContext.requireTenantId(), request));
    }
}
