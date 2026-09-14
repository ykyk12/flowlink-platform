package com.flowlink.common;

import com.flowlink.bootstrap.DemoDataInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 全局异常处理兜底覆盖：缺参 / 方法不支持 / 类型不匹配，均统一为 ApiResponse。 */
@SpringBootTest
@AutoConfigureMockMvc
class GlobalExceptionHandlerIntegrationTest {

    private static final String DEMO_KEY = DemoDataInitializer.DEMO_API_KEY;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void missingRequiredParamReturnsWrapped400() throws Exception {
        // /api/v1/audits/recent 的 ruleSetKey 为必填
        mockMvc.perform(get("/api/v1/audits/recent").header("X-API-Key", DEMO_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }

    @Test
    void unsupportedMethodReturnsWrapped405() throws Exception {
        // /api/v1/audits 仅支持 GET
        mockMvc.perform(post("/api/v1/audits").header("X-API-Key", DEMO_KEY))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void typeMismatchReturnsWrapped400() throws Exception {
        // 把字符串塞进 long version
        mockMvc.perform(get("/api/v1/rule-sets/order_risk/versions/notanumber/content")
                        .header("X-API-Key", DEMO_KEY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
    }
}
