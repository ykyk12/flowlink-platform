package com.flowlink.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 回归：默认零依赖模式（app.feature-store=memory）且本机没起 Redis 时，
 * /actuator/health 必须返回 200 + UP。
 *
 * 背景（本轮真实踩到的 bug）：
 *  spring-boot-starter-data-redis 在 classpath 上，Spring Boot 自动装配 RedisHealthIndicator。
 *  6379 连不上它就报 DOWN，再把整体健康度聚合为 DOWN → 健康端点 503。
 *  但内存模式下 Redis 根本没被用到（真正的 Redis 特征存储只在 app.feature-store=redis / prod 才启用），
 *  应用自己的 flowlinkDecision 指示器是 UP 的，不该被一个没连上的旁路依赖摘流。
 *
 * 修法：application.yml 默认 management.health.redis.enabled=false（可 REDIS_HEALTH_ENABLED=true 打开）；
 *  生产 profile（application-prod.yml，feature-store=redis）显式置 true。
 */
@SpringBootTest
@AutoConfigureMockMvc
class HealthEndpointDegradationTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void Redis未启动时健康端点仍为UP() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void 内存模式下Redis探针不参与聚合() throws Exception {
        // 默认 memory 模式不连 Redis：健康聚合里不应出现 redis 组件
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.redis").doesNotExist());
    }

    @Test
    void 决策子系统指示器为UP且后端为memory() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(jsonPath("$.components.flowlinkDecision.status").value("UP"))
                .andExpect(jsonPath("$.components.flowlinkDecision.details.featureStoreBackend").value("memory"));
    }
}
