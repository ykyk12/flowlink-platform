package com.flowlink.replay;

import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 规则变更影响评估 API：历史决策在目标版本上的回放差异。 */
@RestController
@RequestMapping("/api/v1/replay")
@RequiredArgsConstructor
public class ReplayController {

    private final ReplayService replayService;

    @PostMapping
    public ApiResponse<ReplayDtos.ReplayDiff> replay(@Valid @RequestBody ReplayDtos.ReplayRequest request) {
        return ApiResponse.ok(replayService.replay(TenantContext.requireTenantId(), request));
    }
}
