package com.flowlink.dsl;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.flowlink.common.BizException;
import com.flowlink.common.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * DSL 解析器：用 YAML 解析器统一处理 YAML 与 JSON（JSON 是 YAML 的子集）。
 * 未知字段直接报错，避免拼错字段被静默忽略。
 */
@Component
public class RuleDslParser {

    private final ObjectMapper yamlMapper;

    public RuleDslParser() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    }

    public RuleSetDsl parse(String content) {
        if (content == null || content.isBlank()) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则内容为空");
        }
        try {
            RuleSetDsl dsl = yamlMapper.readValue(content, RuleSetDsl.class);
            if (dsl == null) {
                throw new BizException(ErrorCode.RULE_INVALID, "规则内容解析结果为空");
            }
            return dsl;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.RULE_INVALID, "规则内容无法解析：" + e.getMessage());
        }
    }
}
