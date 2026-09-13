# FlowLink 架构设计

> 本文是设计决策的权威记录；README 的功能表与 API 说明均以本文与代码为准（README 不写「详见本文」了事，而是转述结论）。

## 1. 问题与定位

风控决策是典型的企业级后端场景：**低延迟在线决策** + **规则频繁变更** + **决策必须可解释可追溯**。
FlowLink 把这三件事做成一个平台：规则以 DSL 描述并版本化管理，在线决策走内存编译后的规则树，每次决策落审计并支持按历史事件回放对比。

## 2. 分层与数据流

```
                     ┌──────────────── 管理面 (Admin API) ────────────────┐
 POST /rule-sets ──► │ 规则集 CRUD → 版本(草稿/发布/灰度/回滚) → 缓存热加载    │
                     └───────────────────────┬───────────────────────────┘
                                             │ compile → RuleBundle(active+canary)
                                             ▼
 客户端 ──X-API-Key──► 鉴权拦截器 → 租户上下文 → 配额限流（滑动窗口）
                                             │
 POST /decisions:evaluate ──► 幂等检查 ──► 决策引擎（灰发路由 + 规则求值 + 命中路径）
                                             │
                          ┌──────────────────┼───────────────────┐
                          ▼                  ▼                   ▼
                    审计(decision_audit)   指标(Micrometer)   特征计数(FeatureStore)
                                             │
                     回放/差异对比 (Replay) ──┘
```

## 3. 关键设计决策

### 3.1 规则 DSL：一份内容两种形态
- 写入用 **YAML/JSON**（人可读、可 review），存储为 TEXT，运行时**编译**为规则树。
- 条件节点统一为一种结构体（`ALL` / `ANY` / `NOT` / `EXPR`），避免多态反序列化的脆弱性：
  - `ALL`：所有子条件为真（AND）
  - `ANY`：任一子条件为真（OR）
  - `NOT`：子条件为假
  - `EXPR`：`field + operator + value` 的原子比较（EQ/NE/GT/GTE/LT/LTE/IN/NOT_IN/CONTAINS/MATCHES/EXISTS）
- 校验器在**发布前**拦截：规则 id 唯一、优先级范围、条件深度、规则数上限、字段未在特征里出现的处理策略（缺失值语义 = 比较一律为 false，`EXISTS` 除外）。

### 3.2 版本治理：草稿 → 发布 → 灰度 → 回滚
- `RuleSet` 持有：`activeVersionId`、`canaryVersionId`、`canaryPercent`。
- `RuleVersion` 状态机：`DRAFT → ACTIVE / CANARY → RETIRED`。发布只切换指针，**不改历史版本内容**（可回滚、可审计）。
- 灰度路由：`hash(subjectId) % 100 < canaryPercent` → 走 canary 版本。哈希对同一 subject 稳定，便于 A/B 对比与复现问题。
- 变更即热加载：`RuleCache` 在发布/灰度/回滚后重建编译产物，决策路径只读缓存（无锁读）。

### 3.3 决策引擎：两种裁决模式 + 可解释
- `FIRST_MATCH`：按 `priority` 升序取第一条命中规则（风控常见：高优先级直接拒绝）。
- `SCORE`：命中规则分数累加，最后与 `fallback` 阈值比较（评分卡场景）。
- 每次求值产出 `EvaluationTrace`：每个条件节点的类型、字段、操作符、实际值、结果，以及命中规则列表与分数贡献 —— 这就是"为什么给我这个决定"的答案。
- 拒绝理由链（reason）随决策返回，可直接展示给运营/客服。

### 3.4 特征与窗口计数
- `FeatureStore` 抽象两种能力：**滑动窗口计数**（`incr/get`，用于"过去 N 秒下单次数"类规则）与**点值**（由调用方在请求里传入，避免在线查库）。
- 默认 `InMemoryFeatureStore`：分桶滑动窗口（按秒分桶 + 环形数组），零依赖可跑、可压测。
- 可选 `RedisFeatureStore`：Lua 脚本在 Redis 内完成"窗口内计数 + 过期"，多实例部署时计数一致。

### 3.5 幂等、审计与回放
- 幂等：`X-Idempotency-Key` 命中则直接返回**首次**的决策结果（避免网络重试导致重复拒绝/重复放行）。
- 审计：每次决策落 `decision_audit`（traceId、租户、规则集版本、输入快照、决策、分数、耗时）。
- 回放：`/api/v1/replay` 取历史审计的输入快照，用**目标版本**重跑并输出差异报告（总条数、变化条数、变化率、样本明细）——上线新规则前用它评估"如果按新规则跑，历史决策会怎么变"。

### 3.6 多租户与配额
- 租户以 `X-API-Key` 识别（Key 以 SHA-256 摘要存储，不落明文）；每个请求构造 `TenantContext`。
- 配额：按租户每分钟评估次数限流（滑动窗口计数），超限返回 429 + `Retry-After`。
- 数据隔离：所有实体带 `tenantId`，查询一律带租户条件（本项目不做数据库级隔离，生产可升级为 schema/库级隔离）。

### 3.7 可观测性
- Micrometer 指标：`flowlink_decisions_total{decision}`、`flowlink_decision_latency`（Timer）、`flowlink_canary_total{version}`、`flowlink_quota_rejected_total`。
- Actuator：`/actuator/health`、`/actuator/metrics`、`/actuator/prometheus`。
- 结构化日志：`TraceIdFilter` 为每个请求生成/透传 traceId 并写入 MDC，响应头 `X-Trace-Id` 回传。

## 4. 已知边界与演进路线

| 边界 | 现状 | 演进 |
|---|---|---|
| 规则匹配算法 | 线性遍历 + 优先级排序（规则数上限 500） | 热点字段索引/Rete 网络 |
| 事件来源 | 同步 HTTP 评估 | Kafka/RocketMQ 事件流 + 异步决策 |
| 多实例计数 | 内存实现单机 | Redis 实现已提供，可切 `app.feature-store=redis` |
| 库表迁移 | JPA `ddl-auto=update` | 引入 Flyway 版本化迁移 |
| 权限模型 | 单一租户 Key + 粗粒度角色 | RBAC + 审计双人复核 |
| 决策缓存 | 无（每次都求值，保证一致性） | 幂等窗口内的结果缓存 |

## 5. 性能预期与验证方法

- 目标：单实例 `p95 < 120ms`、`p99 < 300ms`（含持久化审计）。
- 验证：`scripts/k6-decide.js` 提供 10→200 VU 的阶梯压测与阈值断言；结果写入 `scripts/results/`（不入库）。
