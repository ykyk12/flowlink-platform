package com.flowlink.engine;

import lombok.Builder;
import lombok.Getter;

import java.util.Collections;
import java.util.Map;

/**
 * 一次决策的输入上下文。
 * 字段解析顺序：counter:*（窗口计数） → features → attributes，缺失返回 null。
 */
@Getter
@Builder
public class DecisionContext {

    private final String tenantId;
    private final String subjectId;
    private final String eventType;
    @Builder.Default
    private final Map<String, Object> features = Collections.emptyMap();
    @Builder.Default
    private final Map<String, Object> attributes = Collections.emptyMap();
    /** 窗口计数快照：counter 名 → 当前窗口内累计值 */
    @Builder.Default
    private final Map<String, Long> counters = Collections.emptyMap();

    public static final String COUNTER_PREFIX = "counter:";

    /** 按字段名取值；支持 counter: 前缀。 */
    public Object resolve(String field) {
        if (field == null) {
            return null;
        }
        if (field.startsWith(COUNTER_PREFIX)) {
            String name = field.substring(COUNTER_PREFIX.length());
            return counters.getOrDefault(name, 0L);
        }
        if (features != null && features.containsKey(field)) {
            return features.get(field);
        }
        if (attributes != null && attributes.containsKey(field)) {
            return attributes.get(field);
        }
        return null;
    }
}
