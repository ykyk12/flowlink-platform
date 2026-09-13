package com.flowlink.dsl;

import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import com.flowlink.config.FlowLinkProperties;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 规则校验器：发布前拦截脏规则（唯一 id、优先级、条件深度、操作符与值匹配）。
 * 缺失值语义：除 EXISTS 外，字段缺失一律判为 false（风控宁可不命中，不可误命中）。
 */
@Component
public class RuleValidator {

    private final FlowLinkProperties properties;

    public RuleValidator(FlowLinkProperties properties) {
        this.properties = properties;
    }

    public void validate(RuleSetDsl dsl) {
        List<RuleDsl> rules = dsl.getRules();
        if (rules == null || rules.isEmpty()) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则集至少要有一条规则");
        }
        int max = properties.getEngine().getMaxRulesPerSet();
        if (rules.size() > max) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则数量 " + rules.size() + " 超过上限 " + max);
        }
        if (dsl.getMode() == null) {
            throw new BizException(ErrorCode.RULE_INVALID, "缺少裁决模式 mode");
        }
        if (dsl.getFallback() == null || dsl.getFallback().getDecision() == null
                || dsl.getFallback().getDecision().isBlank()) {
            throw new BizException(ErrorCode.RULE_INVALID, "缺少兜底决策 fallback.decision");
        }

        Set<String> ids = new HashSet<>();
        for (RuleDsl rule : rules) {
            if (rule.getId() == null || rule.getId().isBlank()) {
                throw new BizException(ErrorCode.RULE_INVALID, "存在未设置 id 的规则");
            }
            if (!ids.add(rule.getId())) {
                throw new BizException(ErrorCode.RULE_INVALID, "规则 id 重复：" + rule.getId());
            }
            if (rule.getDecision() == null || rule.getDecision().isBlank()) {
                throw new BizException(ErrorCode.RULE_INVALID, "规则 " + rule.getId() + " 缺少 decision");
            }
            if (rule.getPriority() < 0 || rule.getPriority() > 100_000) {
                throw new BizException(ErrorCode.RULE_INVALID, "规则 " + rule.getId() + " 优先级需在 0..100000");
            }
            if (rule.getWhen() == null) {
                throw new BizException(ErrorCode.RULE_INVALID, "规则 " + rule.getId() + " 缺少 when 条件");
            }
            validateNode(rule.getWhen(), 1, rule.getId());
        }
    }

    private void validateNode(ConditionNode node, int depth, String ruleId) {
        int maxDepth = properties.getEngine().getMaxConditionDepth();
        if (depth > maxDepth) {
            throw new BizException(ErrorCode.RULE_INVALID,
                    "规则 " + ruleId + " 条件嵌套深度超过 " + maxDepth);
        }
        if (node.getType() == null) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 存在未声明 type 的条件节点");
        }
        switch (node.getType()) {
            case EXPR -> validateExpr(node, ruleId);
            case NOT -> {
                if (node.getChildren() == null || node.getChildren().size() != 1) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的 NOT 必须且只能有一个子条件");
                }
                validateNode(node.getChildren().get(0), depth + 1, ruleId);
            }
            case ALL, ANY -> {
                if (node.getChildren() == null || node.getChildren().isEmpty()) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的 " + node.getType() + " 至少需要一个子条件");
                }
                for (ConditionNode child : node.getChildren()) {
                    validateNode(child, depth + 1, ruleId);
                }
            }
            default -> throw new BizException(ErrorCode.RULE_INVALID, "未知条件类型 " + node.getType());
        }
    }

    private void validateExpr(ConditionNode node, String ruleId) {
        if (node.getField() == null || node.getField().isBlank()) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的比较条件缺少 field");
        }
        if (node.getOp() == null) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的比较条件缺少 op");
        }
        switch (node.getOp()) {
            case EXISTS -> {
                // 只需字段名
            }
            case IN, NOT_IN -> {
                if (!(node.getValue() instanceof List<?>)) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的 " + node.getOp() + " 需要数组值");
                }
            }
            case MATCHES -> {
                if (!(node.getValue() instanceof String pattern)) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的 MATCHES 需要字符串正则");
                }
                try {
                    Pattern.compile(pattern);
                } catch (PatternSyntaxException e) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的正则非法：" + e.getMessage());
                }
            }
            default -> {
                if (node.getValue() == null) {
                    throw new BizException(ErrorCode.RULE_INVALID, "规则 " + ruleId + " 的 " + node.getOp() + " 缺少 value");
                }
            }
        }
    }
}
