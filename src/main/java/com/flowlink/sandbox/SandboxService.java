package com.flowlink.sandbox;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.dsl.CompiledRuleSet;
import com.flowlink.decision.EvaluateResponse;
import com.flowlink.engine.DecisionContext;
import com.flowlink.engine.DecisionEngine;
import com.flowlink.engine.DecisionResult;
import com.flowlink.ruleset.RuleCache;
import com.flowlink.ruleset.RuleSetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 规则预演/沙箱：在不影响生产的前提下，用样本数据在指定版本上试跑规则。
 *
 * <p>设计要点（与在线 {@code DecisionService} 的差异）：
 * <ul>
 *   <li>可指定<b>任意历史/未发布版本</b>，用于上线前验证；缺省取当前生效版本；</li>
 *   <li>窗口计数只读取请求里给定的快照，<b>不调用 FeatureStore 自增</b>，无副作用；</li>
 *   <li><b>不落审计、不做幂等、不走灰度路由</b>，纯只读求值；</li>
 *   <li>默认返回完整求值轨迹（{@code explain} 语义恒为开），便于排查命中原因。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SandboxService {

    private final RuleSetService ruleSetService;
    private final RuleCache ruleCache;
    private final DecisionEngine engine;

    public SandboxDtos.SandboxResult simulate(String tenantId, SandboxDtos.SandboxRequest request) {
        long version;
        CompiledRuleSet compiled;
        if (request.version() != null) {
            // 指定版本（可未发布）：直接取内容编译，不经过缓存、不影响线上热数据
            var content = ruleSetService.getVersionContent(
                    tenantId, request.ruleSetKey(), request.version());
            compiled = ruleSetService.compile(content.content());
            version = request.version();
        } else {
            RuleCache.CachedRuleSet cached = ruleCache.get(tenantId, request.ruleSetKey())
                    .orElseThrow(() -> new BizException(ErrorCode.CONFLICT,
                            "规则集没有生效版本：" + request.ruleSetKey()));
            compiled = cached.active();
            version = cached.activeVersion();
        }

        DecisionContext context = DecisionContext.builder()
                .tenantId(tenantId)
                .subjectId(request.subjectId())
                .eventType(request.eventType())
                .features(request.features() == null ? Map.of() : request.features())
                .attributes(request.attributes() == null ? Map.of() : request.attributes())
                .counters(request.counters() == null ? Map.of() : request.counters())
                .build();

        DecisionResult result = engine.evaluate(request.ruleSetKey(), version, compiled, context);
        log.debug("沙箱预演 租户 {} 规则集 {} v{}：决策={} 分={}",
                tenantId, request.ruleSetKey(), version, result.getDecision(), result.getScore());
        return new SandboxDtos.SandboxResult(
                version,
                result.getDecision(),
                result.getScore(),
                result.isFallbackUsed(),
                result.reason(),
                result.getMatchedRules().stream().map(EvaluateResponse.MatchedRuleView::from).toList(),
                result.getTraces());
    }
}
