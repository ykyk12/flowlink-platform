package com.flowlink.bootstrap;

import com.flowlink.config.FlowLinkProperties;
import com.flowlink.ruleset.RuleSetDtos;
import com.flowlink.ruleset.RuleSetService;
import com.flowlink.tenant.Tenant;
import com.flowlink.tenant.TenantRepository;
import com.flowlink.tenant.TenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 演示数据初始化：首次启动写入演示租户 + 示例规则集（含窗口计数规则），
 * 让 clone 下来的人不改一行配置就能 curl 出完整决策链路。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    /** 演示用固定 Key（仅演示租户；生产请用管理接口创建租户并轮换 Key） */
    public static final String DEMO_API_KEY = "demo-tenant-key";
    public static final String DEMO_RULE_SET_KEY = "order_risk";
    public static final String DEMO_ADMIN_KEY = "flowlink-admin-key";

    private final FlowLinkProperties properties;
    private final TenantRepository tenantRepository;
    private final TenantService tenantService;
    private final RuleSetService ruleSetService;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isBootstrapDemoData()) {
            return;
        }
        if (tenantRepository.count() > 0) {
            log.info("已存在租户，跳过演示数据初始化");
            return;
        }
        Tenant tenant = tenantService.createWithRawKey("演示租户", DEMO_API_KEY, "demo",
                properties.getDefaultQuotaPerMinute());
        ruleSetService.createSet(tenant.getId(), new RuleSetDtos.CreateSetRequest(DEMO_RULE_SET_KEY, "下单实时风控"));
        RuleSetDtos.VersionView version = ruleSetService.createVersion(tenant.getId(), DEMO_RULE_SET_KEY,
                new RuleSetDtos.CreateVersionRequest(DEMO_DSL, "初始版本：黑名单/大额新设备/短时频次/VIP 放行"));
        ruleSetService.publish(tenant.getId(), DEMO_RULE_SET_KEY, version.version());

        log.info("演示数据就绪：租户 Key={}，规则集={}，管理 Key={}", DEMO_API_KEY, DEMO_RULE_SET_KEY, DEMO_ADMIN_KEY);
        log.info("示例：curl -H \"X-API-Key: {}\" -H \"Content-Type: application/json\" -d @docs/evaluate-sample.json {}/api/v1/decisions/evaluate",
                DEMO_API_KEY, "http://localhost:8090");
    }

    static final String DEMO_DSL = """
            description: 下单实时风控示例规则集
            mode: SCORE
            fallback:
              decision: PASS
              score: 60
              reason: 未达到风险分数阈值
            rules:
              - id: r_blacklist
                name: 黑名单拦截
                priority: 5
                decision: REJECT
                score: 100
                reason: 风险等级命中黑名单
                when:
                  type: EXPR
                  field: riskLevel
                  op: IN
                  value: [ HIGH, BLACK ]
              - id: r_vip_pass
                name: VIP 放行
                priority: 10
                decision: PASS
                score: 0
                reason: VIP 用户且非高风险
                when:
                  type: ALL
                  children:
                    - type: EXPR
                      field: vip
                      op: EQ
                      value: true
                    - type: NOT
                      children:
                        - type: EXPR
                          field: riskLevel
                          op: EQ
                          value: HIGH
              - id: r_high_amount_new_device
                name: 大额新设备复核
                priority: 30
                decision: REVIEW
                score: 70
                reason: 大额交易且设备注册不足 30 天
                when:
                  type: ALL
                  children:
                    - type: EXPR
                      field: amount
                      op: GT
                      value: 5000
                    - type: EXPR
                      field: deviceAgeDays
                      op: LT
                      value: 30
              - id: r_burst_orders
                name: 短时下单频次异常
                priority: 40
                decision: REVIEW
                score: 65
                reason: 60 秒内下单达到 6 次及以上
                when:
                  type: EXPR
                  field: "counter:order_count_60s"
                  op: GTE
                  value: 6
            """;
}
