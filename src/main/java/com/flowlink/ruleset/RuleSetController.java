package com.flowlink.ruleset;

import com.flowlink.common.ApiResponse;
import com.flowlink.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 规则集管理 API：规则集 / 版本 / 发布 / 灰度 / 回滚。 */
@RestController
@RequestMapping("/api/v1/rule-sets")
@RequiredArgsConstructor
public class RuleSetController {

    private final RuleSetService ruleSetService;

    @PostMapping
    public ApiResponse<RuleSetDtos.RuleSetView> createSet(@Valid @RequestBody RuleSetDtos.CreateSetRequest request) {
        return ApiResponse.ok(ruleSetService.createSet(TenantContext.requireTenantId(), request));
    }

    @GetMapping
    public ApiResponse<List<RuleSetDtos.RuleSetView>> listSets() {
        return ApiResponse.ok(ruleSetService.listSets(TenantContext.requireTenantId()));
    }

    @GetMapping("/{key}")
    public ApiResponse<RuleSetDtos.RuleSetView> getSet(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.getSet(TenantContext.requireTenantId(), key));
    }

    @PostMapping("/{key}/versions")
    public ApiResponse<RuleSetDtos.VersionView> createVersion(@PathVariable String key,
                                                              @Valid @RequestBody RuleSetDtos.CreateVersionRequest request) {
        return ApiResponse.ok(ruleSetService.createVersion(TenantContext.requireTenantId(), key, request));
    }

    @GetMapping("/{key}/versions")
    public ApiResponse<List<RuleSetDtos.VersionView>> listVersions(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.listVersions(TenantContext.requireTenantId(), key));
    }

    @GetMapping("/{key}/versions/{version}/content")
    public ApiResponse<RuleSetDtos.ContentView> getContent(@PathVariable String key, @PathVariable long version) {
        return ApiResponse.ok(ruleSetService.getVersionContent(TenantContext.requireTenantId(), key, version));
    }

    @PostMapping("/{key}/versions/{version}/publish")
    public ApiResponse<RuleSetDtos.RuleSetView> publish(@PathVariable String key, @PathVariable long version) {
        return ApiResponse.ok(ruleSetService.publish(TenantContext.requireTenantId(), key, version));
    }

    @PostMapping("/{key}/versions/{version}/canary")
    public ApiResponse<RuleSetDtos.RuleSetView> canary(@PathVariable String key,
                                                       @PathVariable long version,
                                                       @Valid @RequestBody RuleSetDtos.CanaryRequest request) {
        return ApiResponse.ok(ruleSetService.canary(TenantContext.requireTenantId(), key, version, request.percent()));
    }

    @PostMapping("/{key}/rollback")
    public ApiResponse<RuleSetDtos.RuleSetView> rollback(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.rollback(TenantContext.requireTenantId(), key));
    }
}
