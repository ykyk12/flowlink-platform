package com.flowlink.ruleset;

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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 规则集管理 API：规则集 / 版本 / 发布 / 灰度 / 回滚。 */
@Tag(name = "规则集治理", description = "规则集与版本全生命周期：创建、草稿版本、发布、灰度、回滚")
@RestController
@RequestMapping("/api/v1/rule-sets")
@RequiredArgsConstructor
public class RuleSetController {

    private final RuleSetService ruleSetService;

    @Operation(summary = "创建规则集")
    @PostMapping
    public ApiResponse<RuleSetDtos.RuleSetView> createSet(@Valid @RequestBody RuleSetDtos.CreateSetRequest request) {
        return ApiResponse.ok(ruleSetService.createSet(TenantContext.requireTenantId(), request));
    }

    @Operation(summary = "列出当前租户的规则集")
    @GetMapping
    public ApiResponse<List<RuleSetDtos.RuleSetView>> listSets() {
        return ApiResponse.ok(ruleSetService.listSets(TenantContext.requireTenantId()));
    }

    @Operation(summary = "查询规则集详情")
    @GetMapping("/{key}")
    public ApiResponse<RuleSetDtos.RuleSetView> getSet(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.getSet(TenantContext.requireTenantId(), key));
    }

    @Operation(summary = "新建草稿版本", description = "规则内容不可变，任何修改都产生新版本。")
    @PostMapping("/{key}/versions")
    public ApiResponse<RuleSetDtos.VersionView> createVersion(@PathVariable String key,
                                                              @Valid @RequestBody RuleSetDtos.CreateVersionRequest request) {
        return ApiResponse.ok(ruleSetService.createVersion(TenantContext.requireTenantId(), key, request));
    }

    @Operation(summary = "列出规则集的所有版本")
    @GetMapping("/{key}/versions")
    public ApiResponse<List<RuleSetDtos.VersionView>> listVersions(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.listVersions(TenantContext.requireTenantId(), key));
    }

    @Operation(summary = "读取版本 DSL 原文")
    @GetMapping("/{key}/versions/{version}/content")
    public ApiResponse<RuleSetDtos.ContentView> getContent(@PathVariable String key, @PathVariable long version) {
        return ApiResponse.ok(ruleSetService.getVersionContent(TenantContext.requireTenantId(), key, version));
    }

    @Operation(summary = "发布版本", description = "目标版本置生效，其余生效版本退役，热加载无需重启。")
    @PostMapping("/{key}/versions/{version}/publish")
    public ApiResponse<RuleSetDtos.RuleSetView> publish(@PathVariable String key, @PathVariable long version) {
        return ApiResponse.ok(ruleSetService.publish(TenantContext.requireTenantId(), key, version));
    }

    @Operation(summary = "灰度发布版本", description = "percent=0 取消灰度；0..100 表示按 subjectId 哈希分流比例。")
    @PostMapping("/{key}/versions/{version}/canary")
    public ApiResponse<RuleSetDtos.RuleSetView> canary(@PathVariable String key,
                                                       @PathVariable long version,
                                                       @Valid @RequestBody RuleSetDtos.CanaryRequest request) {
        return ApiResponse.ok(ruleSetService.canary(TenantContext.requireTenantId(), key, version, request.percent()));
    }

    @Operation(summary = "回滚到次新版本")
    @PostMapping("/{key}/rollback")
    public ApiResponse<RuleSetDtos.RuleSetView> rollback(@PathVariable String key) {
        return ApiResponse.ok(ruleSetService.rollback(TenantContext.requireTenantId(), key));
    }
}
