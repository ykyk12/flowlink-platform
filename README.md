# FlowLink —— 企业级实时风控决策平台

> 一句话：**把"规则能改、决策可解释、变更可回放"这三件风控工程里最难的事，做成一个可运行的平台。**
> 技术上：规则 DSL + 版本治理（发布/灰度/回滚/热加载）+ 可解释决策引擎 + 决策回放差异对比 + 多租户配额 + 审计与可观测。

技术栈：Java 17 · Spring Boot 3.3 · Spring Data JPA · H2/PostgreSQL · Redis（可选特征存储）· Micrometer/Actuator · springdoc-openapi · Docker Compose · GitHub Actions · k6

## 1. 它解决什么问题

在线风控的典型痛点不是"算不出来"，而是：

| 痛点 | FlowLink 的答案 |
|---|---|
| 规则散落在代码里，改一次要发版 | 规则用 YAML/JSON DSL 描述，发布即生效（编译产物热加载） |
| 新规则上线心里没底：会影响多少历史决策？ | `/api/v1/replay` 用历史决策快照在**新版本**上重跑，输出变化条数与样本 |
| 出了误杀，运营问"为什么拒我" | 每次决策返回命中规则 + 完整条件求值轨迹（可解释） |
| 想灰度一部分用户试新规则 | 按 `subjectId` 稳定哈希灰度分流，同一用户永远落同一分支 |
| 网络重试导致重复放行/重复拒绝 | 幂等键（TTL 内返回首次结果） |
| 多业务方共用一套风控 | 多租户（API Key 摘要存储）+ 每租户每分钟配额限流 |
| 事后追责无从查起 | append-only 审计（traceId + 输入快照），可按 traceId / 决策 / 时间检索 |

## 2. 核心能力

| 能力 | 实现位置 | 说明 |
|---|---|---|
| 规则 DSL（YAML 或 JSON） | `dsl/` | 条件节点统一为 `ALL / ANY / NOT / EXPR`，未知字段直接报错 |
| 规则校验 | `dsl/RuleValidator` | 唯一 id、优先级范围、嵌套深度、`IN` 必须数组、`MATCHES` 正则合法性 |
| 版本治理 | `ruleset/RuleSetService` | 草稿 → 发布 → 灰度 → 回滚；版本内容不可变，回滚只是指针切换 |
| 热加载 | `ruleset/InMemoryRuleCache` | 发布/灰度/回滚后整体替换编译产物，决策路径无锁读缓存 |
| 决策引擎 | `engine/DecisionEngine` | `FIRST_MATCH`（首命中裁决）与 `SCORE`（评分累加 + 阈值）两种模式 |
| 可解释性 | `engine/RuleEvaluator` | 每个条件节点的路径、表达式、实际值、结果全量记录 |
| 窗口特征 | `feature/FeatureStore` | 内存分桶滑动窗口（默认）/ Redis ZSET + Lua 滑动窗口（多实例一致） |
| 幂等 | `decision/IdempotencyStore` | `idempotencyKey` 命中直接回放首次结果 |
| 审计 | `audit/` | append-only + 输入快照，支持 traceId 追溯与条件检索 |
| 回放对比 | `replay/ReplayService` | 目标版本重跑历史决策，输出变化率与逐条样本 |
| 多租户 + 配额 | `tenant/`、`security/` | API Key（SHA-256 摘要）鉴权、每租户每分钟评估配额，超限 429 |
| 可观测性 | `metrics/DecisionMetrics`、Actuator | 决策分布、耗时分位、灰度分流、幂等命中、配额拒绝 |
| 工程化 | `Dockerfile`、`docker-compose.yml`、`.github/workflows/ci.yml`、`scripts/k6-decide.js` | 一键起全栈、CI 构建测试、阶梯压测与阈值断言 |

## 3. 架构与数据流

```
                     ┌──────────────── 管理面 (Admin API) ────────────────┐
 POST /rule-sets ──► │ 规则集 CRUD → 版本(草稿/发布/灰度/回滚) → 缓存热加载    │
                     └───────────────────────┬───────────────────────────┘
                                             │ compile → RuleBundle(active+canary)
                                             ▼
 客户端 ──X-API-Key──► 鉴权拦截器 → 租户上下文 → 配额限流（滑动窗口）
                                             │
 POST /decisions/evaluate ──► 幂等检查 ──► 决策引擎（灰度路由 + 规则求值 + 命中路径）
                                             │
                          ┌──────────────────┼───────────────────┐
                          ▼                  ▼                   ▼
                    审计(decision_audit)   指标(Micrometer)   特征计数(FeatureStore)
                                             │
                     回放/差异对比 (Replay) ──┘
```

关键设计决策（详细论证见 `docs/ARCHITECTURE.md`）：

1. **条件节点不用多态**：统一结构体 + `type` 枚举，比 Jackson 多态反序列化稳，也便于校验与轨迹记录。
2. **缺失值语义**：除 `EXISTS` 外，字段缺失一律判 false —— 风控宁可不命中，不可误命中。
3. **灰度靠稳定哈希**：`hash(subjectId) % 100 < canaryPercent`，同一主体永远落同一分支，便于 A/B 与复现问题。
4. **版本不可变**：任何规则调整都产生新版本；回滚 = 切指针，因此"回滚"与"审计"都变得廉价可靠。
5. **回放不重复计数**：审计保存计数快照，回放时直接复用，保证同一历史事件可复现。
6. **特征在线传入 + 窗口计数内建**：避免决策链路查库；窗口计数由 FeatureStore 提供（内存/Redis 可切）。

## 4. 快速开始（零外部依赖）

```bash
# 需要 JDK 17+ 与 Maven
mvn spring-boot:run
```

启动后自动写入演示数据（可用 `app.bootstrap-demo-data=false` 关闭）：

- 演示租户 API Key：`demo-tenant-key`
- 演示规则集：`order_risk`（黑名单拦截 / VIP 放行 / 大额新设备复核 / 60 秒频次异常）
- 管理密钥（管理面 `/api/v1/admin/**`）：`flowlink-admin-key`

```bash
# 1) 评估一次决策（explain=true 返回完整求值轨迹）
curl -H "X-API-Key: demo-tenant-key" -H "Content-Type: application/json" \
     -d @docs/evaluate-sample.json \
     http://localhost:8090/api/v1/decisions/evaluate

# 2) 新建规则版本并发布
curl -X POST -H "X-API-Key: demo-tenant-key" -H "Content-Type: application/json" \
     -d '{"key":"order_risk_v2","name":"下单风控-新版"}' \
     http://localhost:8090/api/v1/rule-sets

# 3) 灰度 10%（按 subjectId 稳定分流）
curl -X POST -H "X-API-Key: demo-tenant-key" -H "Content-Type: application/json" \
     -d '{"percent":10}' \
     http://localhost:8090/api/v1/rule-sets/order_risk/versions/2/canary

# 4) 回放对比：历史决策在目标版本上的差异
curl -X POST -H "X-API-Key: demo-tenant-key" -H "Content-Type: application/json" \
     -d '{"ruleSetKey":"order_risk","targetVersion":2,"limit":200}' \
     http://localhost:8090/api/v1/replay

# 5) 审计追溯
curl -H "X-API-Key: demo-tenant-key" "http://localhost:8090/api/v1/audits/stats"
```

接口文档（Swagger UI）：`http://localhost:8090/swagger-ui.html`

## 5. API 一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/v1/decisions/evaluate` | 单次决策（幂等键可选） |
| POST | `/api/v1/decisions/batch` | 批量决策（上限 `app.engine.max-batch-size`） |
| GET | `/api/v1/decisions/{traceId}` | 按 traceId 追溯该次决策的审计记录 |
| POST/GET | `/api/v1/rule-sets` | 新建 / 列出规则集 |
| GET | `/api/v1/rule-sets/{key}` | 规则集详情（生效版本、灰度版本、模式、规则数） |
| POST | `/api/v1/rule-sets/{key}/versions` | 新建版本（自动校验 DSL） |
| GET | `/api/v1/rule-sets/{key}/versions` | 版本列表 |
| GET | `/api/v1/rule-sets/{key}/versions/{v}/content` | 查看某版本 DSL 原文 |
| POST | `/api/v1/rule-sets/{key}/versions/{v}/publish` | 发布版本 |
| POST | `/api/v1/rule-sets/{key}/versions/{v}/canary` | 灰度（`percent=0` 取消） |
| POST | `/api/v1/rule-sets/{key}/rollback` | 回滚到上一个版本 |
| POST | `/api/v1/replay` | 回放差异对比 |
| GET | `/api/v1/audits`、`/audits/{traceId}`、`/audits/stats`、`/audits/recent` | 审计检索、追溯、决策分布、最近记录 |
| POST/GET | `/api/v1/admin/tenants`（`X-Admin-Key`） | 租户创建/列表/轮换 Key/调整配额/启停用 |

## 6. 规则 DSL

```yaml
mode: SCORE                     # FIRST_MATCH | SCORE
fallback: { decision: PASS, score: 60, reason: 未达到风险分数阈值 }
rules:
  - id: r_blacklist             # 规则集内唯一
    name: 黑名单拦截
    priority: 5                 # 越小越先算
    decision: REJECT
    score: 100
    reason: 风险等级命中黑名单
    when:
      type: EXPR                # ALL | ANY | NOT | EXPR
      field: riskLevel
      op: IN                    # EQ NE GT GTE LT LTE IN NOT_IN CONTAINS MATCHES EXISTS
      value: [ HIGH, BLACK ]
  - id: r_burst_orders
    name: 短时下单频次异常
    priority: 40
    decision: REVIEW
    score: 65
    when: { type: EXPR, field: "counter:order_count_60s", op: GTE, value: 6 }
```

- `counter:<name>` 字段来自请求体 `counters: { "<name>": <窗口秒数> }`：服务端自增并读取窗口内累计值（例如"过去 60 秒下单次数"）。
- `FIRST_MATCH`：按优先级取第一条命中规则立即裁决。
- `SCORE`：命中规则分数累加；累加值 ≥ `fallback.score` 时取"优先级最高"的命中规则决策，否则回落兜底决策（响应里带实际分数，便于调参）。

## 7. 目录结构

```
src/main/java/com/flowlink/
├── common/    统一响应、错误码、业务异常、traceId 过滤器、id/哈希工具
├── config/    配置属性、Web 拦截器注册、OpenAPI
├── security/  API Key 鉴权 + 配额拦截
├── tenant/    租户实体/仓库/服务/管理 API、配额服务、租户上下文
├── dsl/       规则 DSL 模型、解析（YAML+JSON）、校验、编译产物
├── engine/    决策上下文、条件求值器、决策引擎、结果与轨迹
├── feature/   特征存储抽象 + 内存滑动窗口 + Redis 滑动窗口
├── ruleset/   规则集/版本实体与仓库、缓存、版本治理服务、管理 API
├── decision/  评估请求/响应、幂等存储、决策编排、决策 API
├── audit/     审计实体/仓库/服务/API
├── replay/    回放差异对比服务与 API
├── metrics/   决策指标
└── bootstrap/ 演示数据初始化
src/main/resources/  application.yml（默认零依赖）、application-prod.yml（PG+Redis）
src/test/java/...     单元测试（DSL/引擎/窗口）+ 集成测试（生命周期/决策 API/回放）
docs/                 ARCHITECTURE.md、evaluate-sample.json
scripts/              k6-decide.js（阶梯压测 + 阈值断言）
```

## 8. 测试与质量

```bash
mvn verify          # 单元 + 集成测试（H2 内存库，无需外部服务）
mvn test -Dtest=DecisionEngineTest      # 只跑引擎单测
k6 run -e BASE=http://localhost:8090 -e API_KEY=demo-tenant-key scripts/k6-decide.js
```

覆盖点：

- DSL：未知字段拦截、重复 id、非法正则、超深嵌套、`IN` 值类型
- 引擎：两种裁决模式、优先级、窗口计数字段、缺失值语义、兜底分支、轨迹非空
- 特征：窗口内累计、窗口外过期、租户/键隔离
- 集成：规则生命周期（发布→再发布→回滚→灰度）与灰度路由对决策的影响
- API：鉴权 401、决策 200、幂等回放、traceId 追溯、配额 429
- 回放：新版本重跑历史决策，变化条数/变化率/样本断言

## 9. 可观测性

| 指标 / 端点 | 说明 |
|---|---|
| `flowlink_decisions_total{decision}` | 各决策类型计数 |
| `flowlink_decision_latency` | 决策耗时（含审计落库），发布 p50/p95/p99 |
| `flowlink_canary_total{version}` | 灰度版本分流次数 |
| `flowlink_quota_rejected_total{tenant}` | 配额拒绝次数 |
| `flowlink_idempotent_hits_total` | 幂等命中次数 |
| `/actuator/health`、`/actuator/metrics`、`/actuator/prometheus` | 健康与指标抓取 |

每个请求都有 `X-Trace-Id`（可透传），日志 MDC 打印，审计表按 traceId 可追溯。

## 10. 设计取舍与已知边界

| 边界 | 现状 | 演进方向 |
|---|---|---|
| 规则匹配算法 | 线性遍历 + 优先级排序（规则上限 500） | 热点字段索引 / Rete 网络 |
| 事件来源 | 同步 HTTP 评估 | Kafka/RocketMQ 事件流 + 异步决策 |
| 多实例计数 | 内存实现单机（可切 Redis） | `app.feature-store=redis` 已提供 ZSET+Lua 实现 |
| 库表迁移 | JPA `ddl-auto=update` | 引入 Flyway 版本化迁移 |
| 权限模型 | 单一租户 Key + 管理密钥 | RBAC + 双人复核 + 操作审计 |
| 决策缓存 | 无（每次求值保证一致） | 幂等窗口内结果缓存 |

## 11. 版本记录

| 版本 | 说明 |
|---|---|
| 1.1.3 | 修正决策 API 集成测试的 traceId 取值：贪婪正则误取外层 `ApiResponse.traceId`（本次请求链路 id），改为按 `data.traceId` 结构解析——幂等重放本就应返回首次决策的 traceId |
| 1.1.2 | 修正决策接口路径：`/decisions:evaluate` → `/decisions/evaluate`（`:action` 后缀不被 Spring MVC 路由解析，请求落入静态资源处理器并抛 NoResourceFoundException） |
| 1.1.1 | 修复 `ApiResponse` 记录组件与私有静态方法重名（record 访问器必须 public）导致的编译失败 |
| 1.1.0 | 首个功能提交：规则 DSL + 版本治理 + 决策引擎 + 窗口特征 + 幂等/审计 + 回放对比 + 多租户配额 + 可观测 + 测试 + Docker/CI/k6 |
| 1.0.0 | 架构设计与文档版（无功能代码） |

## 12. License

MIT © 2026 杨锴
