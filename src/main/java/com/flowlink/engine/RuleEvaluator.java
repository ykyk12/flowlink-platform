package com.flowlink.engine;

import com.flowlink.dsl.ConditionNode;
import com.flowlink.dsl.Operator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 条件求值器：递归求值条件树，并把每个节点的结果写入轨迹（可解释）。
 * 缺失值语义：除 EXISTS 外一律 false —— 宁可漏过，不可误命中。
 *
 * 正则策略：MATCHES 的 Pattern 按正则串做有界 LRU 缓存，编译一次、多次执行，
 * 对标 AviatorScript / QLExpress 的"编译一次、多次求值"模型，避免热路径反复编译。
 */
@Component
public class RuleEvaluator {

    /** 已编译正则的上限：规则数本就有界（默认 ≤500），这里再兜一层防规则抖动导致无界增长。 */
    private static final int MAX_CACHED_PATTERNS = 1024;

    /** access-order 的 LinkedHashMap 做 LRU，配合 synchronized 保证线程安全。 */
    private final Map<String, Pattern> patternCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Pattern> eldest) {
                    return size() > MAX_CACHED_PATTERNS;
                }
            });

    public boolean evaluate(ConditionNode node, DecisionContext context, String ruleId,
                            String path, List<EvaluationTrace> traces) {
        return switch (node.getType()) {
            case ALL -> evaluateAll(node, context, ruleId, path, traces);
            case ANY -> evaluateAny(node, context, ruleId, path, traces);
            case NOT -> evaluateNot(node, context, ruleId, path, traces);
            case EXPR -> evaluateExpr(node, context, ruleId, path, traces);
        };
    }

    private boolean evaluateAll(ConditionNode node, DecisionContext context, String ruleId,
                                String path, List<EvaluationTrace> traces) {
        boolean result = true;
        List<ConditionNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean child = evaluate(children.get(i), context, ruleId, path + ".all[" + i + "]", traces);
            result = result && child;
        }
        traces.add(new EvaluationTrace(ruleId, path, "ALL", result, null, null));
        return result;
    }

    private boolean evaluateAny(ConditionNode node, DecisionContext context, String ruleId,
                                String path, List<EvaluationTrace> traces) {
        boolean result = false;
        List<ConditionNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean child = evaluate(children.get(i), context, ruleId, path + ".any[" + i + "]", traces);
            result = result || child;
        }
        traces.add(new EvaluationTrace(ruleId, path, "ANY", result, null, null));
        return result;
    }

    private boolean evaluateNot(ConditionNode node, DecisionContext context, String ruleId,
                                String path, List<EvaluationTrace> traces) {
        boolean child = evaluate(node.getChildren().get(0), context, ruleId, path + ".not", traces);
        boolean result = !child;
        traces.add(new EvaluationTrace(ruleId, path, "NOT", result, null, null));
        return result;
    }

    private boolean evaluateExpr(ConditionNode node, DecisionContext context, String ruleId,
                                 String path, List<EvaluationTrace> traces) {
        Object actual = context.resolve(node.getField());
        boolean result = compare(actual, node.getOp(), node.getValue());
        String expression = node.getField() + " " + node.getOp() + " " + describe(node.getValue());
        traces.add(new EvaluationTrace(ruleId, path, expression, result, actual, node.getValue()));
        return result;
    }

    boolean compare(Object actual, Operator op, Object expected) {
        if (op == Operator.EXISTS) {
            return actual != null;
        }
        if (actual == null) {
            return false;
        }
        return switch (op) {
            case EQ -> matchesValue(actual, expected);
            case NE -> !matchesValue(actual, expected);
            case GT -> compareOrdered(actual, expected) > 0;
            case GTE -> compareOrdered(actual, expected) >= 0;
            case LT -> compareOrdered(actual, expected) < 0;
            case LTE -> compareOrdered(actual, expected) <= 0;
            case IN -> anyMatch(actual, expected);
            case NOT_IN -> !anyMatch(actual, expected);
            case CONTAINS -> contains(actual, expected);
            case MATCHES -> matchesRegex(actual, expected);
            default -> false;
        };
    }

    private boolean matchesValue(Object actual, Object expected) {
        if (actual instanceof Number a && expected instanceof Number b) {
            return Double.compare(a.doubleValue(), b.doubleValue()) == 0;
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private int compareOrdered(Object actual, Object expected) {
        Optional<Double> a = toDouble(actual);
        Optional<Double> b = toDouble(expected);
        if (a.isPresent() && b.isPresent()) {
            return Double.compare(a.get(), b.get());
        }
        return String.valueOf(actual).compareTo(String.valueOf(expected));
    }

    private boolean anyMatch(Object actual, Object expected) {
        if (expected instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> matchesValue(actual, item));
        }
        return matchesValue(actual, expected);
    }

    private boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> matchesValue(item, expected));
        }
        return String.valueOf(actual).contains(String.valueOf(expected));
    }

    private boolean matchesRegex(Object actual, Object expected) {
        if (expected == null) {
            return false;
        }
        String regex = String.valueOf(expected);
        Pattern pattern;
        synchronized (patternCache) {
            pattern = patternCache.get(regex);
            if (pattern == null) {
                // 发布前 RuleValidator 已校验语法，此处不会抛 PatternSyntaxException
                pattern = Pattern.compile(regex);
                patternCache.put(regex, pattern);
            }
        }
        return pattern.matcher(String.valueOf(actual)).find();
    }

    private Optional<Double> toDouble(Object value) {        if (value instanceof Number number) {
            return Optional.of(number.doubleValue());
        }
        try {
            return Optional.of(Double.parseDouble(String.valueOf(value)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** 测试可见：当前已编译并缓存的正则数量。 */
    int cachedPatternCount() {
        synchronized (patternCache) {
            return patternCache.size();
        }
    }

    private String describe(Object value) {
        return value == null ? "null" : String.valueOf(value);
    }
}
