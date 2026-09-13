package com.flowlink.engine;

/**
 * 条件求值轨迹：可解释性的最小单元。
 *
 * @param ruleId      所属规则
 * @param path        节点路径（如 rule[r_amount].when.any[0]）
 * @param expression  可读表达式（如 amount &gt; 5000）
 * @param result      该节点结果
 * @param actual      实际取值（缺失为 null）
 * @param expected    期望值
 */
public record EvaluationTrace(String ruleId,
                              String path,
                              String expression,
                              boolean result,
                              Object actual,
                              Object expected) {
}
