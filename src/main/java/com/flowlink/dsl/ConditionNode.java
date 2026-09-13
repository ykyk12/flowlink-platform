package com.flowlink.dsl;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 条件节点（统一结构，避免多态反序列化的脆弱性）：
 *  - ALL  ：所有子条件为真（AND）
 *  - ANY  ：任一子条件为真（OR）
 *  - NOT  ：唯一子条件为假
 *  - EXPR ：field op value 的原子比较
 */
@Data
public class ConditionNode {

    public enum NodeType {
        ALL,
        ANY,
        NOT,
        EXPR
    }

    private NodeType type = NodeType.EXPR;

    /** EXPR 使用：字段名。支持内置前缀 counter:（由请求声明的窗口计数特征） */
    private String field;

    /** EXPR 使用：操作符 */
    private Operator op;

    /** EXPR 使用：比较值（IN/NOT_IN 为数组） */
    private Object value;

    /** ALL / ANY / NOT 使用 */
    private List<ConditionNode> children = new ArrayList<>();
}
