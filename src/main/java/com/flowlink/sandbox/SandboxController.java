package com.flowlink.sandbox;

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

/**
 * 规则预演/沙箱 API：用一份样本数据在指定（可未发布）版本上试跑规则，
 * 不落审计、不自增窗口计数、不走灰度路由，用于规则上线前验证命中效果。
 */
@Tag(name = "规则沙箱", description = "规则预演：在任意（含未发布）版本上试跑样本，不落审计")
@RestController
@RequestMapping("/api/v1/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;

    @Operation(summary = "沙箱试跑", description = "样本数据在指定版本上评估一次，用于上线前验证命中效果。")
    @PostMapping("/evaluate")
    public ApiResponse<SandboxDtos.SandboxResult> simulate(@Valid @RequestBody SandboxDtos.SandboxRequest request) {
        return ApiResponse.ok(sandboxService.simulate(TenantContext.requireTenantId(), request));
    }
}
