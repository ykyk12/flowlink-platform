package com.flowlink.decision;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowlink.bootstrap.DemoDataInitializer;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 决策 API 集成测试：鉴权、幂等、审计追溯、配额限流。 */
@SpringBootTest
@AutoConfigureMockMvc
class DecisionApiIntegrationTest {

    private static final String DEMO_KEY = DemoDataInitializer.DEMO_API_KEY;
    private static final String RULE_SET = DemoDataInitializer.DEMO_RULE_SET_KEY;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TenantService tenantService;
    @Autowired
    private RuleSetService ruleSetService;
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 取业务 traceId（data.traceId）。
     * 不能用贪婪正则匹配 "traceId"：统一响应体里 traceId 是**最后一个**字段，
     * 贪婪匹配会取到外层 ApiResponse.traceId（本次请求的链路 id），幂等重放场景下它与首次决策不同。
     */
    private String dataTraceId(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("traceId").asText();
    }

    private String body(String subjectId, String idempotencyKey) {
        return """
                {
                  "ruleSetKey": "%s",
                  "subjectId": "%s",
                  "eventType": "ORDER_CREATE",
                  "features": { "amount": 120, "deviceAgeDays": 300, "riskLevel": "LOW" },
                  "attributes": { "channel": "app", "region": "cn" },
                  "idempotencyKey": "%s",
                  "explain": true
                }
                """.formatted(RULE_SET, subjectId, idempotencyKey);
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/decisions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("user-no-key", null)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void evaluatesAndWritesAuditTraceableByTraceId() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/decisions/evaluate")
                        .header("X-API-Key", DEMO_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("user-trace-" + System.nanoTime(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.decision").exists())
                .andExpect(jsonPath("$.data.ruleSetKey").value(RULE_SET))
                .andExpect(jsonPath("$.data.traces").isArray())
                .andReturn();

        String traceId = dataTraceId(result);

        mockMvc.perform(get("/api/v1/decisions/" + traceId).header("X-API-Key", DEMO_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.ruleVersion").exists());
    }

    @Test
    void idempotencyKeyReturnsFirstResult() throws Exception {
        String key = "idem-" + System.nanoTime();
        MvcResult first = mockMvc.perform(post("/api/v1/decisions/evaluate")
                        .header("X-API-Key", DEMO_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("user-idem", key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andReturn();
        String firstTrace = dataTraceId(first);

        MvcResult second = mockMvc.perform(post("/api/v1/decisions/evaluate")
                        .header("X-API-Key", DEMO_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("user-idem", key)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replayed").value(true))
                .andReturn();
        String secondTrace = dataTraceId(second);

        org.junit.jupiter.api.Assertions.assertEquals(firstTrace, secondTrace);
    }

    @Test
    void quotaIsEnforcedPerTenant() throws Exception {
        String rawKey = "key-quota-" + System.nanoTime();
        Tenant tenant = tenantService.createWithRawKey("配额租户", rawKey, "test", 1);
        String key = "risk_quota_" + System.nanoTime();
        ruleSetService.createSet(tenant.getId(), new RuleSetDtos.CreateSetRequest(key, "配额规则集"));
        RuleSetDtos.VersionView version = ruleSetService.createVersion(tenant.getId(), key,
                new RuleSetDtos.CreateVersionRequest("""
                        mode: FIRST_MATCH
                        fallback: { decision: PASS, score: 0 }
                        rules:
                          - id: r_block
                            decision: REJECT
                            priority: 1
                            when: { type: EXPR, field: amount, op: GT, value: 999999 }
                        """, "配额用规则"));
        ruleSetService.publish(tenant.getId(), key, version.version());

        String payload = """
                { "ruleSetKey": "%s", "subjectId": "u-quota",
                  "features": { "amount": 1 } }
                """.formatted(key);

        mockMvc.perform(post("/api/v1/decisions/evaluate").header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/decisions/evaluate").header("X-API-Key", rawKey)
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("QUOTA_EXCEEDED"));
    }
}
