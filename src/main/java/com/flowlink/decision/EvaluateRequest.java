package com.flowlink.decision;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 评估请求。
 *
 * @param ruleSetKey    规则集 key
 * @param subjectId     主体标识（用户/设备/账号），灰度路由与审计用
 * @param eventType     事件类型（下单、登录、提现…）
 * @param features      业务特征（由调用方在线计算后传入，避免决策链路查库）
 * @param attributes    环境属性（渠道、地区…）
 * @param counters      需要自增并读取的窗口计数：名 → 窗口秒数（结果以 counter:&lt;名&gt; 供规则引用）
 * @param idempotencyKey 幂等键：相同键在 TTL 内直接返回首次结果
 * @param explain       是否返回完整求值轨迹（默认 false，线上可关以省带宽）
 */
public record EvaluateRequest(
        @NotBlank @Size(max = 64) String ruleSetKey,
        @Size(max = 128) String subjectId,
        @Size(max = 64) String eventType,
        Map<String, Object> features,
        Map<String, Object> attributes,
        Map<String, Integer> counters,
        @Size(max = 128) String idempotencyKey,
        boolean explain) {
}
