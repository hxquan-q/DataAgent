---
title: 问数智能体 · 架构设计文档
version: v1.0
date: 2026-06-29
author: 架构团队
status: 设计中
scope: Phase 1 MVP — 独立 Web 系统
---

# 1 问数智能体 · 架构设计文档

> **设计哲学**：将概率生成转化为确定性构建。模型做语义理解，系统做 SQL 生成——"人把轨道铺好，模型只负责把乘客放到正确车厢。"

---

## 1.1 目录

1. [架构总览](#1-架构总览)
2. [设计原则与理论框架](#2-设计原则与理论框架)
3. [系统分层架构](#3-系统分层架构)
4. [核心模块设计](#4-核心模块设计)
5. [语义层设计](#5-语义层设计)
6. [Agent 流水线设计（LangGraph）](#6-agent-流水线设计langgraph)
7. [五道防线机制](#7-五道防线机制)
8. [三大硬骨头与应对策略](#8-三大硬骨头与应对策略)
9. [多数据源路由设计](#9-多数据源路由设计)
10. [数据模型与ER关系](#10-数据模型与er关系)
11. [工程结构与代码组织](#11-工程结构与代码组织)
12. [技术栈选型](#12-技术栈选型)
13. [演进路线（Phase 1 → Phase 2 → Phase 3）](#13-演进路线phase-1--phase-2--phase-3)
14. [验收标准与成功指标](#14-验收标准与成功指标)

---

## 1.2 架构总览

### 1.2.1 系统全景图

```
┌────────────────────────────────────────────────────────────────────────┐
│                          消费层 (Presentation)                        │
│                                                                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────────┐     │
│  │ 上下文选择器      │  │  对话窗口 (SSE)  │  │  图表渲染 (ECharts)  │     │
│  │ [系统▼][模块▼]   │  │  流式文字+图表   │  │  柱状/折线/饼/卡片   │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────────┘     │
│                                                                        │
│  Phase 1: 独立 Web UI          Phase 2: 嵌入式 Widget (iframe+postMsg) │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ SSE / REST
┌──────────────────────────────┼─────────────────────────────────────┐
│                        Agent 服务层 (FastAPI)                        │
│                              │                                       │
│  ┌──────────────────────────┼──────────────────────────────────┐    │
│  │              LangGraph Agent 编排层 (9 节点)                  │    │
│  │                                                             │    │
│  │  ① 上下文过滤 → ② 问题重写 → ③ 语义解析 (NL2Semantic)       │    │
│  │      → ④ 完整性检查 → ⑤ 字段校验 → ⑥ SQL拼装 (受控)          │    │
│  │      → ⑦ 数据查询 → ⑧ 结论生成 → ⑨ 图表+返回                 │    │
│  │                                                             │    │
│  │  分支: 不完整时 → 反问用户 → 等待补充 → 重新进入流水线          │    │
│  └──────────────────────────┼──────────────────────────────────┘    │
│                              │                                       │
│  ┌──────────────┐  ┌────────┴────────┐  ┌───────────────────┐       │
│  │  LLM 调用层   │  │ 语义配置管理器    │  │ 查询日志/反馈采集  │       │
│  │ (Claude API)  │  │ (四表热加载)     │  │ (dq_query_log)    │       │
│  └──────────────┘  └─────────────────┘  └───────────────────┘       │
└──────────────────────────────┬─────────────────────────────────────┘
                               │ 只读连接
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   ┌──────────┐         ┌──────────┐          ┌──────────┐
   │ 语义配置库 │         │ ERP DB   │          │ MES DB   │
   │(MySQL/PG)│         │ (只读)   │          │ (只读)   │
   │ 四表+日志 │         │          │          │          │
   └──────────┘         └──────────┘          └──────────┘
                              ↑
                        ┌──────────┐
                        │ WMS DB   │
                        │ (只读)   │
                        └──────────┘
```

### 1.2.2 核心架构决策

| 决策点 | 选择 | 理由 |
|--------|------|------|
| 技术范式 | NL2Semantic2SQL（非 NL2SQL） | 概率生成→确定性构建，口径一致可审计 |
| SQL 生成方式 | 后端受控拼装（非 LLM 直出） | 杜绝 SQL 注入，口径零漂移 |
| Agent 框架 | LangGraph 状态图 | 节点编排清晰，条件分支自然，社区验证 |
| 语义层形态 | 四表配置（Metric/Dim/Alias/MetricVer） | 轻量可控，渐进演化为完整语义层 |
| 对话模式 | SSE 流式返回 | 先文字结论→再图表→最后明细，体验流畅 |

---

## 1.3 设计原则与理论框架

### 1.3.1 深水区公式（架构设计的北极星）

```
企业 Agentic 效果 = 语义层 × Skill × Agent 框架 × LLM
                           ↑        ↑          ↑          ↑
                        四表配置   提示词工程   LangGraph   Claude
                        (Phase1)   (Phase1)    (Phase1)    (Phase1)
```

**乘法不是加法——任一为 0 则整体为 0。**

- **语义层 = 0**：没有语义层，模型基于错误数据自主推导比不做还糟
- **Skill = 0**：没有好的提示词和反问机制，语义层定义再好也无法正确消费
- **Agent 框架 = 0**：没有状态编排，多轮对话、反问、纠错无法实现
- **LLM = 0**：没有大模型，自然语言理解无法实现

MVP 阶段四层都取最小可用形态，但结构必须完整。

### 1.3.2 三层价值递进

```
What (发生了什么)  →  Why (为什么)  →  How (怎么办)
  问数 (Phase 1)      归因 (Phase 2)    决策建议 (Phase 3)
```

MVP 锚定 **What 层**——确保问得准、答得可信。

### 1.3.3 三层基石（可信分析工作流核心）

| 基石 | MVP 落地形态 | Phase 2+ 演进 |
|------|-------------|--------------|
| **可信** — 敢不敢信 | 四表语义层 + 受控 SQL + 口径唯一 | 标准指标优先检索 + 证据链追溯 |
| **能干** — 能不能干 | LangGraph 9 步流水线 + 3 种 LLM 调用 | Agentic Harness + 多工具路由 |
| **可承接** — 能不能落 | 查询日志 + 用户反馈采集 | 权限审计 + Skill 沉淀 + 报告交付 |

### 1.3.4 分工哲学

```
大模型擅长的事                    系统确定性做的事
─────────────                    ─────────────────
理解自然语言意图         →        SQL 拼装与执行
多轮指代消解             →        字段/值校验与枚举约束
从数据中生成分析结论      →        口径版本选择与路由
                          →        权限边界控制
                          →        查询超时与降级
```

---

## 1.4 系统分层架构

### 1.4.1 四层架构（对标语义层四层理论）

```
┌───────────────────────────────────────────────────────────┐
│  消费层 (Consumption)                                     │
│  独立 Web UI · 对话窗口 · ECharts · 上下文选择器            │
│  Phase 2: iframe Widget · postMessage · 宿主上下文注入       │
├───────────────────────────────────────────────────────────┤
│  Agent 编排层 (Agent Framework)                           │
│  LangGraph 状态图 · 9 节点流水线 · 条件分支                 │
│  SSE 流式响应 · 对话历史管理 · 错误处理链                    │
├───────────────────────────────────────────────────────────┤
│  语义治理层 (Semantic Governance)                         │
│  四表配置 (Metric/Dim/Alias/MetricVer)                     │
│  热加载 · 版本管理 · 口径唯一性保障 · 别名消歧               │
│  五道防线: 候选过滤 → 约束选择 → 完整性检查 → 字段校验 → SQL白名单 │
├───────────────────────────────────────────────────────────┤
│  数据访问层 (Data Access)                                  │
│  多数据源连接池 · 只读账号 · 参数化查询 · 超时控制            │
│  白名单表名 · SELECT-only · WHERE 时间条件强制 · LIMIT 1000  │
└───────────────────────────────────────────────────────────┘
```

### 1.4.2 跨层数据流

```
用户自然语言
    │
    ▼
[消费层] 接收 + 渲染
    │ query_text + context(system, module, filters)
    ▼
[Agent层] LangGraph 编排
    │
    ├──→ [语义层] 加载候选列表 (按 system+module 过滤)
    │         加载别名映射
    │         校验字段/值
    │         获取 SQL 模板
    │
    ├──→ [LLM] 语义解析 (从候选列表选择，非自由生成)
    │         问题重写 (指代消解)
    │         结论生成 (基于结果，非凭空推测)
    │
    └──→ [数据层] 路由到目标 DB → 参数化执行 → 返回结果
              │
              ▼
         查询结果 + 语义对象 → 图表配置 + 文字结论 → SSE 流式返回
```

---

## 1.5 核心模块设计

### 1.5.1 模块总览

| 模块 | 职责 | 所在层 | LLM 依赖 |
|------|------|--------|----------|
| ContextFilter | 根据上下文过滤候选指标/维度 | 语义层 | 无 |
| QueryRewrite | 多轮指代消解 | Agent 层 | 有 |
| SemanticParse | NL→语义对象 (核心) | Agent 层 | 有 |
| Validate | 完整性检查 + 字段校验 | 语义层 | 无 |
| BuildSQL | 语义对象→受控 SQL | Agent 层 | 无 |
| ExecuteSQL | 多源路由 + 参数化查询 | 数据层 | 无 |
| GenerateConclusion | 数据→分析结论 | Agent 层 | 有 |
| BuildChart | 语义对象→ECharts 配置 | Agent 层 | 无 |
| FormatResponse | SSE 流式组装 | Agent 层 | 无 |

**关键设计：9 个节点中仅 3 个调用 LLM（SemanticParse / QueryRewrite / GenerateConclusion），其余 6 个是确定性逻辑——最大化可控性。**

### 1.5.2 LLM 调用策略

| LLM 调用节点 | 模型选择 | 调用频率 | 延迟预算 |
|-------------|---------|---------|---------|
| QueryRewrite | Haiku (轻量) | 每次多轮对话 | ≤1s |
| SemanticParse | Sonnet (主力) | 每次查询 (核心) | ≤3s |
| GenerateConclusion | Sonnet | 每次查询 | ≤2s |

**端到端延迟预算**: LLM ≤6s + SQL ≤3s + 其他 ≤1s = **≤10s**

---

## 1.6 语义层设计

### 1.6.1 四表语义层核心设计理念

语义层是整个系统的"业务大脑"——它决定了**算什么、口径一致**。

四表设计借鉴语义层四大语义要素（业务实体/维度/度量/关系），以最小可用形态落地：

```
┌─────────────┐     ┌─────────────┐
│   METRIC    │────→│ METRIC_VER  │  (1:N) 一个指标多个口径版本
│  指标定义    │     │  口径版本    │
│             │     │             │
│ ·编码·名称   │     │ ·版本编码    │
│ ·聚合方式    │     │ ·时间字段    │
│ ·来源表/库   │     │ ·过滤条件    │
│ ·SQL模板     │     │ ·默认标记    │
└──────┬──────┘     └─────────────┘
       │
       │ 与维度动态关联
       │
┌──────┴──────┐     ┌─────────────┐
│     DIM     │     │    ALIAS    │  (独立映射表 — "决胜关键")
│   维度定义   │     │   别名映射   │
│             │     │             │
│ ·编码·名称   │     │ ·用户说法    │
│ ·来源字段    │     │ ·映射目标    │
│ ·类型/枚举   │     │ ·匹配类型    │
└─────────────┘     │ ·优先级     │
                     └─────────────┘
```

### 1.6.2 METRIC 表 — 指标定义

| 字段 | 类型 | 说明 | 设计要点 |
|------|------|------|---------|
| metric_code | VARCHAR(100) PK | 指标编码: `wms_stock_in_qty` | 全局唯一，按系统前缀命名 |
| metric_name | VARCHAR(200) | 中文名: 入库数量 | 用于候选列表展示 |
| source_system | VARCHAR(50) | 来源系统: ERP/WMS/MES | **路由关键** |
| source_module | VARCHAR(100) | 来源模块: inbound/outbound | **上下文过滤关键** |
| source_table | VARCHAR(200) | 来源表: wms_stock_in_detail | SQL 拼装目标 |
| source_db | VARCHAR(200) | 数据库连接标识: wms_db | **多源路由** |
| agg_field | VARCHAR(200) | 聚合字段: quantity | |
| agg_func | VARCHAR(50) | 聚合函数: SUM/COUNT/AVG | |
| default_time_field | VARCHAR(200) | 时间字段: inbound_date | 缺省时间条件 |
| sql_template | TEXT | SQL 模板(可选) | 复杂指标使用 |

### 1.6.3 ALIAS 表 — 别名映射（决胜关键）

别名表是连接"业务语言"与"系统定义"的桥梁。没有它，用户说"收了多少货"系统无法匹配到"入库数量"。

| 别名类型 | 示例 | 匹配策略 |
|---------|------|---------|
| 指标别名 | "中标"→contract_count, "收了多少"→stock_in_qty | exact/fuzzy |
| 维度别名 | "行业"→industry, "大区"→region | exact |
| 口径别名 | "按签的"→signed_ver, "按批的"→batch_ver | exact |
| 值别名 | "已签约"→status='SIGNED', "作废"→status='CANCELLED' | exact |

**设计要点**：priority 字段解决多匹配冲突——优先级高的胜出。

### 1.6.4 METRIC_VER 表 — 口径版本

企业级场景中"金额"可能有 6 种定义（含税/不含税/含退款/...），口径版本表从机制上杜绝歧义：

- 同一指标多个口径版本，用户未指明 → **触发反问**
- 有默认口径标记（is_default），首次查询可使用
- 每个口径版本可指定不同的时间字段和过滤条件

### 1.6.5 语义层配置加载策略

```
启动时:
  ┌─── 全量加载四表到内存 ───┐
  │                          │
  │  METRIC  → Map<code, M>  │
  │  DIM     → Map<code, D>  │
  │  ALIAS   → Map<text, A>  │
  │  VER     → Map<mc, List<V>> │
  └──────────────────────────┘

运行时:
  1. 根据上下文 (system+module) 过滤 candidate_metrics / candidate_dims
  2. 从候选列表构建 prompt 注入 LLM
  3. LLM 只能从候选列表中选择 — 不能自由发明

热更新:
  · 管理端 CRUD 操作 → 写入数据库
  · 内存版本延迟 60s 刷新 (定时轮询 updated_at)
  · 或通过 Redis Pub/Sub 即时通知
```

---

## 1.7 Agent 流水线设计（LangGraph）

### 1.7.1 流水线全景

```
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
│ 1.上下文  │ -> │ 2.问题重写│ -> │ 3.语义解析│ -> │ 4.完整性 │
│   过滤    │    │  (LLM)   │    │  (LLM)   │    │   检查   │
└──────────┘    └──────────┘    └──────────┘    └────┬─────┘
                                                     │
                                              ┌──────┴──────┐
                                              │ 不完整:反问   │──→ 等待用户
                                              │ 完整:继续     │    补充信息
                                              └──────┬──────┘    └───┘
                                                     │
┌──────────┐    ┌──────────┐    ┌──────────┐    ┌────┴─────┐
│ 9.返回   │ <- │ 8.结论   │ <- │ 7.数据   │ <- │ 5.字段   │
│   结果   │    │ 生成(LLM)│    │   查询   │    │   校验   │
└──────────┘    └──────────┘    └──────────┘    └──────────┘
    │
┌───┴────┐
│ 6.SQL  │
│  拼装  │
└────────┘
```

### 1.7.2 状态对象设计

```python
class QueryState(TypedDict):
    # 输入
    user_input: str                    # 用户原始问题
    context: QueryContext               # 系统上下文 (system/module/filters)
    conversation_history: list          # 最近 N 轮对话 (N≤5)

    # 中间状态
    rewritten_query: str                # 指代消解后的独立问题
    candidate_metrics: list              # 上下文过滤后的候选指标
    candidate_dims: list                # 上下文过滤后的候选维度
    semantic_object: dict | None       # LLM 输出的语义对象 JSON
    needs_clarification: bool           # 是否需要反问
    clarification_message: str          # 反问消息

    # 执行
    generated_sql: str                  # 受控拼装的 SQL
    sql_params: dict                    # 参数化查询参数
    target_db: str                      # 目标数据库连接标识
    query_result: list                  # 查询结果
    execution_time_ms: int              # 执行耗时

    # 输出
    conclusion: str                     # 分析结论
    chart_config: dict                   # ECharts 配置
    error: str | None                   # 错误信息
```

### 1.7.3 各节点规格

#### 1.7.3.1 Node 1: 上下文过滤 (ContextFilter)

```
输入: state.context (system, module)
输出: state.candidate_metrics, state.candidate_dims
LLM:  无
逻辑: SELECT * FROM dq_metric WHERE source_system = ? AND source_module = ?
```

**核心作用**：缩小 LLM 的选择空间，从全量指标中只暴露当前上下文相关的候选——降低幻觉概率。

#### 1.7.3.2 Node 2: 问题重写 (QueryRewrite)

```
输入: user_input + conversation_history (最近5轮)
输出: rewritten_query (独立完整问题)
LLM:  Haiku (轻量)
示例: "按行业分" + 上文"今年杭州商机" → "今年杭州商机按行业分布"
```

#### 1.7.3.3 Node 3: 语义解析 (SemanticParse) — 核心

```
输入: rewritten_query + candidate_metrics + candidate_dims + aliases
输出: semantic_object (结构化 JSON)
LLM:  Sonnet (主力)
```

**关键约束**：
- LLM **只能从注入的候选列表中选择**，不能自己发明指标或维度
- 候选列表中无匹配 → `needs_clarification: true`
- 口径版本不唯一且用户未指明 → `needs_clarification: true`
- 所有查询必须有时间范围，未指定 → 默认"近30天"

**输出语义对象示例**：

```json
{
  "metrics": [{"metric_code": "wms_stock_in_qty", "metric_ver": "by_inbound_date"}],
  "dimensions": [{"dim_code": "material", "type": "group_by"}],
  "time_range": {"field": "inbound_date", "start": "2026-06-01", "end": "2026-06-25"},
  "filters": [{"field": "warehouse", "operator": "=", "value": "华东仓"}],
  "analysis_type": "trend",
  "chart_type": "line"
}
```

#### 1.7.3.4 Node 4: 完整性检查 (Validate)

```
校验项:
  □ 必填字段: metrics 非空, time_range 非空
  □ 时间范围: 不超过最大限制 (默认3年)
  □ 逻辑一致性: analysis_type 与 metrics/dimensions 匹配

通过 → Node 5
不通过 → 反问用户 (列出缺失项 + 可选选项)
```

#### 1.7.3.5 Node 5: 字段校验 (FieldValidate)

```
校验项:
  □ metric_code 存在于 dq_metric 且 status='active'
  □ dim_code 存在于 dq_dim 且 status='active'
  □ metric_ver 存在于 dq_metric_ver
  □ 过滤值在 enum_values 范围内 (如果配置了枚举)
  □ 时间字段类型正确
```

#### 1.7.3.6 Node 6: SQL 拼装 (BuildSQL)

```
规则:
  · 不由 LLM 生成 — 后端根据 semantic_object + dq_metric.sql_template 拼装
  · 参数化查询 (PreparedStatement)
  · 强制限制:
    ✗ 只允许 SELECT
    ✓ 必须有 WHERE 时间条件
    ✓ 默认 LIMIT 1000
    ✓ 白名单表名校验
```

#### 1.7.3.7 Node 7: 数据查询 (ExecuteSQL)

```
规则:
  · 路由到 semantic_object 中指标所属的 source_db
  · 超时 5 秒
  · 只读账号
  · 异常时返回友好消息 (不暴露原始 SQL 错误)
```

#### 1.7.3.8 Node 8: 结论生成 (GenerateConclusion)

```
输入: query_result (结构化) + semantic_object
LLM:  Sonnet
输出: 2-4 句分析结论
约束:
  · 只描述数据呈现的事实
  · 不允许推测因果关系
  · 包含: 汇总值 + Top N + 趋势方向 + 异常提示
```

#### 1.7.3.9 Node 9: 格式化返回 (FormatResponse)

```
SSE 流式顺序:
  1. 文字结论 (text/event-stream)
  2. 图表配置 (ECharts option JSON)
  3. 查询来源 (表/字段/时间/耗时)
  4. [展开明细] [导出Excel] [👍👎]
```

---

## 1.8 五道防线机制

准确性不是寄托模型不犯错，而是一套工程机制。从用户输入到 SQL 执行，层层收敛：

```
防线 1: 上下文候选过滤
━━━━━━━━━━━━━━━━━━━━━━
  全量指标 → 按系统+模块过滤 → 仅暴露相关候选
  效果: 缩小 LLM 选择空间，降低"选错"概率

防线 2: LLM 约束选择
━━━━━━━━━━━━━━━━━━━━━━
  Prompt 明确: 只能从候选列表选择，不能自由发明
  无匹配 → needs_clarification=true (触发反问)
  多口径 → needs_clarification=true (触发反问)

防线 3: 完整性 + 字段校验
━━━━━━━━━━━━━━━━━━━━━━
  必填字段检查 / 时间范围校验 / 枚举值校验
  不通过 → 友好反问而非硬执行

防线 4: 受控 SQL 拼装
━━━━━━━━━━━━━━━━━━━━━━
  后端确定性拼装 (非 LLM 生成)
  SELECT-only + WHERE 时间 + LIMIT + 白名单表名
  参数化查询杜绝注入

防线 5: 查询卡片 + 反馈闭环
━━━━━━━━━━━━━━━━━━━━━━
  每次回答附带: 查询来源表、字段、时间范围、过滤条件、执行耗时
  用户 👍👎 反馈 → 持续优化提示词 + 补充别名
```

---

## 1.9 三大硬骨头与应对策略

### 1.9.1 硬骨头一：业务口径不统一

**问题**："金额"有 N 种定义（含税/不含税/含退款），口口相传

**应对**：

| 机制 | 具体做法 | 阶段 |
|------|---------|------|
| 口径版本表 (dq_metric_ver) | 同一指标多个口径版本，一键切换 | MVP |
| 反问机制 | 用户未指明口径 → 列出可选版本请用户选择 | MVP |
| 口径说明 (description) | 每个版本附带自然语言解释 | MVP |
| 默认口径 (is_default) | 新用户首次使用默认版本，降低门槛 | MVP |
| 口径溯源 | 查询卡片展示使用了哪个口径版本 | Phase 2 |

### 1.9.2 硬骨头二：业务黑话/别名

**问题**：用户说"中标"、"签了"、"有了"，系统不知道对应哪个指标

**应对**：

| 机制 | 具体做法 | 阶段 |
|------|---------|------|
| 别名映射表 (dq_alias) | 业务黑话 → 标准编码映射 | MVP |
| 匹配类型分级 | exact(精确) / fuzzy(模糊) + priority 优先级 | MVP |
| 反馈驱动补充 | 用户反馈 👎 → 运营补充新别名 | MVP |
| 上下文消歧义 | 同一别名在不同系统/模块下映射不同目标 | Phase 2 |

### 1.9.3 硬骨头三：复杂查询覆盖

**问题**：用户问"去年Q4华东区供应商A的退货率同比"，涉及多维度+同比+筛选

**应对**：

| 机制 | 具体做法 | 阶段 |
|------|---------|------|
| SQL 模板 (sql_template) | 复杂指标预定义 SQL 模板 | MVP |
| 语义对象扩展 | 支持 derived_metrics (同环比/占比/排名) | MVP |
| 八类分析模式 | trend/compare/ranking/extreme/distribution/detail/explore/attribution | Phase 2 |
| Golden Query Set | 标杆查询集，回归测试保底 | MVP |
| 无法处理时 | 友好告知"暂不支持，建议..."而非硬猜 | MVP |

---

## 1.10 多数据源路由设计

### 1.10.1 路由架构

```
语义对象.metrics[].source_db
         │
         ▼
  ┌─────────────────────┐
  │   路由决策器          │
  │                     │
  │  单系统 → 直接路由    │
  │  多系统 → 拒绝+提示   │ (MVP 不支持跨系统)
  │  未知库 → 报错       │
  └──────────┬──────────┘
             │
    ┌────────┼────────┐
    ▼        ▼        ▼
┌──────┐ ┌──────┐ ┌──────┐
│erp_db│ │wms_db│ │mes_db│
│只读   │ │只读   │ │只读   │
└──────┘ └──────┘ └──────┘
```

### 1.10.2 连接池管理

```python
DB_CONNECTIONS = {
    "erp_db": AsyncEngine("mysql+aiomysql://readonly:***@erp-host:3306/erp"),
    "wms_db": AsyncEngine("mysql+aiomysql://readonly:***@wms-host:3306/wms"),
    "mes_db": AsyncEngine("mysql+aiomysql://readonly:***@mes-host:3306/mes"),
}

def route(semantic_object: dict) -> AsyncEngine:
    systems = {m["source_db"] for m in semantic_object["metrics"]}
    if len(systems) > 1:
        raise CrossSystemQueryError(f"涉及 {systems} 多系统，MVP 暂不支持")
    return DB_CONNECTIONS[systems.pop()]
```

**设计要点**：
- 只读账号，不可写
- 每个源独立连接池
- 连接信息通过环境变量注入，不入代码库

---

## 1.11 数据模型与ER关系

### 1.11.1 ER 关系图

```
┌───────────────┐       ┌───────────────┐
│    METRIC     │──────→│  METRIC_VER   │  (1:N)
│   指标定义     │       │  口径版本      │
│               │       │               │
│ PK: id        │       │ PK: id        │
│ UK: code      │       │ UK: (code,    │
│    system     │       │      ver_code)│
│    module     │       │    time_field │
│    table      │       │    filter_cond│
│    db         │       │    is_default │
│    agg_*      │       │               │
│    sql_tmpl   │       └───────────────┘
└───────┬───────┘
        │ N:M (动态关联)
        │
┌───────┴───────┐       ┌───────────────┐
│     DIM       │       │    ALIAS      │  (独立映射)
│   维度定义     │       │   别名映射     │
│               │       │               │
│ PK: id        │       │ PK: id        │
│ UK: code      │       │    alias_text │ ← 用户说法
│    field      │       │    target_type│ ← metric/dim/ver/filter
│    type       │       │    target_code│
│    enum_vals  │       │    match_type │
│               │       │    priority   │
└───────────────┘       └───────────────┘

┌───────────────┐
│  QUERY_LOG    │  查询日志 (审计 + 反馈 + 优化)
│               │
│ PK: id        │
│    session_id │
│    user_query │
│    semantic   │  (JSON)
│    generated  │  (SQL)
│    exec_time  │
│    feedback   │  👍👎
└───────────────┘
```

### 1.11.2 MVP 核心表 DDL

详见 PRD 文档 4.3.1 节完整建表语句（`dq_metric`, `dq_dim`, `dq_alias`, `dq_metric_ver`, `dq_query_log`）。

---

## 1.12 工程结构与代码组织

```
data-agent/
├── docker-compose.yml
├── .env.example                    # 环境变量模板
├── pyproject.toml
│
├── backend/
│   ├── main.py                     # FastAPI 入口
│   ├── config.py                   # 环境变量配置管理
│   │
│   ├── api/
│   │   ├── chat.py                 # /chat/query (SSE), /chat/history
│   │   ├── feedback.py             # /chat/feedback
│   │   └── config.py               # /config/* (语义层管理端)
│   │
│   ├── agent/                     # ★ 核心: LangGraph Agent
│   │   ├── graph.py                # 状态图定义 + 条件路由
│   │   ├── nodes/
│   │   │   ├── context_filter.py   # Node 1: 上下文过滤
│   │   │   ├── query_rewrite.py    # Node 2: 问题重写 (LLM)
│   │   │   ├── semantic_parse.py   # Node 3: 语义解析 (LLM·核心)
│   │   │   ├── validate.py         # Node 4: 完整性+字段校验
│   │   │   ├── build_sql.py        # Node 5: SQL 拼装
│   │   │   ├── execute.py          # Node 6: 数据查询
│   │   │   ├── conclude.py         # Node 7: 结论生成 (LLM)
│   │   │   └── build_chart.py      # Node 8: 图表配置
│   │   └── prompts/
│   │       ├── query_rewrite.md    # 问题重写提示词模板
│   │       ├── semantic_parse.md   # ★ 语义解析提示词模板 (最关键)
│   │       └── conclusion.md       # 结论生成提示词模板
│   │
│   ├── semantic/                   # ★ 核心: 语义层
│   │   ├── models.py              # ORM: Metric/Dim/Alias/MetricVer
│   │   ├── loader.py              # 内存热加载 + 刷新策略
│   │   └── manager.py             # CRUD (管理端)
│   │
│   ├── db/
│   │   ├── connections.py          # 多源连接池
│   │   ├── query_executor.py       # SQL 执行 + 超时 + PreparedStatement
│   │   └── sql_validator.py       # SQL 合法性校验
│   │
│   ├── schemas/
│   │   ├── chat.py                 # 请求/响应 Pydantic models
│   │   └── semantic.py             # SemanticObject schema
│   │
│   └── utils/
│       ├── time_parser.py          # 自然语言时间解析
│       └── logger.py               # 查询日志
│
├── frontend/
│   ├── index.html                  # 独立 Web UI
│   ├── js/
│   │   ├── app.js                  # 主逻辑
│   │   ├── chat.js                 # SSE 对话管理
│   │   ├── chart.js                # ECharts 渲染
│   │   └── context.js             # 系统/模块选择器
│   └── embed/                     # Phase 2: 嵌入组件
│       └── agent-widget.js
│
├── config/                         # ★ 初始化配置
│   ├── init_wms_metrics.sql         # WMS 指标初始数据
│   ├── init_wms_aliases.sql         # WMS 别名初始数据
│   └── golden_queries.json          # Golden Query Set (回归测试)
│
└── tests/
    ├── test_semantic_parse.py       # 语义解析单元测试
    ├── test_sql_builder.py          # SQL 拼装单元测试
    ├── test_query_executor.py       # 数据查询单元测试
    └── test_golden_queries.py       # Golden Query 回归测试
```

---

## 1.13 技术栈选型

| 层次 | 技术 | 版本 | 选型理由 |
|------|------|------|---------|
| **LLM** | Claude API | Sonnet 4 / Haiku | 社区验证 text2sql 场景最佳 |
| **Agent 框架** | LangGraph | latest | 状态图编排清晰，条件分支自然 |
| **后端** | FastAPI | ≥0.110 | 异步支持好，SSE 原生支持 |
| **数据库驱动** | aiomysql / asyncpg | - | 异步多数据源访问 |
| **图表** | ECharts | 5.x | 功能全面，中文友好 |
| **前端** | 纯 HTML/JS (Web Component) | - | 无框架依赖，可嵌入任意系统 |
| **语义配置存储** | MySQL / PostgreSQL | - | 独立于业务数据库 |
| **部署** | Docker + docker-compose | - | 简化部署 |

---

## 1.14 演进路线（Phase 1 → Phase 2 → Phase 3）

```
Phase 1 (MVP)                    Phase 2                           Phase 3
════════════                     ════════                          ════════
独立 Web 系统                     嵌入式 + 深度分析                   可信分析工作流

✅ 四表语义层                     ✅ 语义层升级 (自动发现+血缘)        ✅ Skill 沉淀
✅ NL2Semantic2SQL               ✅ 智能归因 (Why层)                 ✅ 报告自动生成
✅ LangGraph 9节点               ✅ 跨系统查询 (JOIN路由)            ✅ What-if 模拟
✅ WMS 入库/出库/库存              ✅ ERP 采购/销售接入                ✅ A2A 多Agent协作
✅ 五道防线                       ✅ iframe 嵌入宿主表单               ✅ 权限审计
✅ 反问机制                       ✅ 宿主上下文自动注入                ✅ 知识库接入
✅ Golden Query Set              ✅ 查询卡片就地纠错                  ✅ 经验→资产沉淀
✅ 用户反馈闭环                   ✅ 多源融合 (CSV+Excel+明细)        ✅ 三方协同 (业务/分析/IT)

目标: 3个模块, 8个指标             目标: 6+ 模块, 30+ 指标            目标: 全域覆盖, 自助率 70%+
SQL准确率 ≥85%                    SQL准确率 ≥92%                     端到端准确率 ≥95%
响应时间 ≤10s                     响应时间 ≤8s                      响应时间 ≤5s
```

### 1.14.1 Phase 1 → Phase 2 关键跃迁

| 维度 | Phase 1 | Phase 2 |
|------|---------|---------|
| 访问方式 | 浏览器打开独立 URL | 宿主表单内唤起侧边栏 |
| 上下文来源 | 用户手动选择系统/模块 | 宿主页面自动注入 |
| 过滤条件 | 用户手动输入或自然语言 | 自动带入表单当前条件 |
| 目标用户 | 数据岗 + 主动查询的业务人员 | 一线操作人员 (不离开表单) |
| 开发重点 | 核心链路 + 多数据源 + 完整 UI | 嵌入通信 + 上下文感知 + 归因 |

---

## 1.15 验收标准与成功指标

### 1.15.1 技术指标

| 指标 | MVP 目标值 | 测量方式 |
|------|-----------|---------|
| SQL 生成准确率 | ≥85%（简单查询 ≥95%） | Golden Query Set + 人工抽检 100 条 |
| 平均响应时间 | ≤10 秒 | 端到端计时（SSE 首字节到图表渲染完成） |
| 系统可用性 | ≥99% | Docker 健康检查 |

### 1.15.2 业务指标

| 指标 | MVP 目标值 | 测量方式 |
|------|-----------|---------|
| 用户自主查询占比 | 减少 50% 手工 SQL 请求 | 对比上线前后数据岗工单量 |
| 结论可接受率 | ≥80% 用户认为"结论合理" | 👍👎 反馈 |
| 反问准确率 | ≥90% 反问有针对性 | 人工评估 |

### 1.15.3 Golden Query Set (标杆查询集)

MVP 必须覆盖的基准查询，用于回归测试和准确率度量：

```
简单查询 (目标 ≥95%):
  1. "本月入库数量" → SUM(quantity) WHERE month=current
  2. "华东仓今天出了多少" → SUM(qty) WHERE wh=华东 AND date=today

中等复杂 (目标 ≥85%):
  3. "最近3个月各仓库入库趋势" → GROUP BY wh, month, trend
  4. "按物料分类的出库金额TOP10" → GROUP BY material, ORDER BY, LIMIT

带过滤 (目标 ≥85%):
  5. "供应商A上个月的到货及时率" → specific supplier + time + rate formula

边界/反问 (目标 ≥80%):
  6. "看看情况" → 反问: 您想看哪个模块？入库/出库/库存？
  7. "6月数据" → 反问: 您想看6月的什么指标？入库数量/入库金额/...
```

---

## 1.16 附录 A: 语义解析提示词模板 (核心)

详见 PRD 4.5.2 节完整模板。以下是设计要点摘要：

```
提示词结构:
  ┌──────────────────────────────────────────┐
  │ 1. 角色: 企业 WMS 系统的数据分析助手       │
  │ 2. 核心约束: 只能从候选列表选择，不能发明    │
  │ 3. 当前上下文: 系统/模块/日期               │
  │ 4. 候选指标列表 (Markdown 表格)            │
  │ 5. 候选维度列表 (Markdown 表格)            │
  │ 6. 别名映射列表                            │
  │ 7. 用户问题                                │
  │ 8. 输出格式 (JSON Schema)                  │
  └──────────────────────────────────────────┘
```

**关键设计**：
- 候选列表动态注入（按系统+模块过滤），非全量灌入——降低 token 消耗和幻觉
- JSON Schema 明确定义每个字段，减少自由发挥空间
- `needs_clarification` 标志位让 LLM 主动表达"我不确定"

---

## 1.17 附录 B: 可信分析工作流完整闭环参考

MVP 聚焦 What 层，但架构设计预留 Why/How 扩展点：

```
MVP (What层)           Phase 2 (Why层)          Phase 3 (How层)
━━━━━━━━━━━            ━━━━━━━━━━━━             ━━━━━━━━━━━━
问数 → 结论+图表        问数 → 归因分析            问数 → 决策建议
   ↓                       ↓                       ↓
SSE流式返回             多维归因 (维度下钻)        What-if 模拟
查询卡片展示            因子归因 (GMV=单价×客数)    行动建议
反馈闭环                根因定位效率10x+           自动报告生成

架构预留:
  semantic_object 中 analysis_type 已预留:
    summary / ranking / trend / distribution / attribution / what_if
  LangGraph 状态图可扩展归因子图节点
```
