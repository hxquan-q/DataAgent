# 17 参考项目 · 工程控制论深研总表

> 用钱学森《工程控制论》的方法论（η₁ 分类定方法 + 推理四步法），把 `~/dev/reference/` 全部 17 个项目当作**控制系统**分类研究。
>
> 续 [`NL2SQL_AGENT_CYBERNETICS.md`](../dev/NL2SQL_AGENT_CYBERNETICS.md)（9 项目 4 路线）——本文扩展到 17 个，补全原范围外的 Java 编码 agent / DB 原生可观测 / 领域 agent / Python 分析库。
>
> 方法论：每个项目判 **系统类型(离散/非线性/随机) → 反馈结构 → 稳定性保障 → 自适应 → 信息效率 → 可观测 → 容错**，归入控制结构路线，评估对 DataAgent v0.2 的可迁移性。

---

## 0. 方法论（控制论四步法应用于项目分类）

```
Step1 系统类型：含 LLM 的全是「离散 + 本质非线性 + 强随机」信息控制系统
Step2 反馈结构：开环(LLM自主) / 闭环(显式重试) / 学习型(样本反哺) / 无(工具/库)
Step3 稳定性保障：迭代上限 / 重试护栏 / AST护栏 / DB一致性 / 无
Step4 工具集映射 → 路线归类
```

**6 条控制结构路线**（原 4 路线 + 新增 2 类非 NL2SQL）：

| 路线 | 控制结构 | 反馈 | 稳定性 | 自适应 | 数据 Agent? |
|:---:|---|---|---|:---:|:---:|
| ① 开环 Agent | LLM 自主循环 | 隐式 | 弱(迭代上限) | 无 | 是 |
| ② 多步 Graph | 显式闭环 | 强(抗饱和+护栏) | 强 | 无 | 是 |
| ③ RAG 训练 | 学习型 | 样本反哺 | 中 | **有** | 是 |
| ④ DB 原生 | 测量下沉 | — | 强(DB一致) | 无 | 是 |
| ⑤ 编码 Agent | tool-loop + transcript | 闭环 | 中 | 弱 | 否(同栈可鉴) |
| ⑥ 工具/可观测 | 无(库/CLI) | — | — | — | 否 |

---

## 1. 17 项目控制论画像总表

| # | 项目 | 技术栈 | 路线 | 反馈结构 | 稳定性保障 | 自适应 | 信息效率 | 可观测 | 对 v0.2 价值 | 置信度 |
|:-:|---|---|:-:|---|---|:-:|---|---|---|:-:|
| 1 | **datafoundry** | TS monorepo+Mastra+AG-UI | ② | 受控闭环+checkpoint | Data Gateway 只读护栏+AST | 无 | schema 缓存+上下文预算 | **TraceDag+证据链** | 🔴🔥 已抄(§10.1/10.4) | High |
| 2 | **QueryWeaver** | Py/FastAPI+LiteLLM+FalkorDB | ② | Healer×3 闭环 | 破坏性拦截+attempts上限 | 无 | 四路图召回+桥接表 | 进度事件 | 🔴 已抄(§10.2/10.3) | High |
| 3 | **SQLBot** | Py/FastAPI+Vue+g2-ssr | ②(弱) | **开环**(失败即止) | AST 三层+表名白名单 | 弱(RAG静态) | 三路召回+样本 | 弱 | 🔴 已抄(§10.1) | High |
| 4 | **vanna** | Py(经典RAG在legacy/) | ③ | 学习型(ask反哺) | max_tokens截断 | **有**(三库) | 三collection独立topk | 弱 | 🔴 已抄(§10.6) | High |
| 5 | **Chat2DB** | **Java/Spring Boot** | ② | 多步(产品级) | 无SQL护栏 | 无 | 业务上下文服务 | 弱 | 🟡 **工程脚手架可鉴**([源码卡](./chat2db.md)) | High |
| 6 | **Jimi** | **Java/Spring+WebFlux** | ⑤ | tool-loop+transcript | Loop六原语+多维有界 | 弱 | 三层记忆+progress恢复 | AST代码图 | 🟡 **Maker/Checker独立验证者**(架构§10.9) | High |
| 7 | **claw-code-java** | Java/Spring AI | ⑤ | tool-first loop | transcript持久化 | 无 | 上下文压缩 | JSONL transcript | 🟡 transcript/续跑/压缩 | Medium |
| 8 | **pandas-ai** | Py 库 | ①/③ | agent loop+on_error | 重试+train | **有**(train) | df 就地分析 | 弱 | 🟡 Python分析链路+train | Medium |
| 9 | **GustoBot** | Py/FastAPI+LangGraph+Neo4j | ② | 多agent+Map-Reduce | fallback cascade | 无 | GraphRAG+KB+滑窗记忆 | 弱 | 🟡 **L1双重路由+双阈值+滑窗**(架构§10.9) | High |
| 10 | **pgai** | PG扩展(pgvector+plpython3u) | ④ | — | DB一致性 | 无 | semantic catalog | — | ⚪ 已停维,仅借鉴catalog | High |
| 11 | **pilotscope** | Py(SIGMOD论文) | ④ | — | DB原生 | **有**(学习型优化器) | 查询优化下沉 | — | ⚪ Learned Query Optimizer,非NL2SQL | Medium |
| 12 | **postgresai** | Node CLI+Py | ⑥ | — | — | — | — | AI可观测 | ⚪ 非NL2SQL,PG监控 | High |
| 13 | **sqlchat** | Next.js/React | ① | LLM→SQL(轻量) | 弱 | 无 | 全量schema | 弱 | ⚪ 轻量客户端,UI可鉴 | Medium |
| 14 | **text-to-sql-agent** | Py+LangChain+Claude | ① | create_agent隐式 | 迭代上限 | 无 | 全量schema | **LangSmith** | ⚪ 教学,trace可鉴 | High |
| 15 | **LangChain-SQL-可视化** | Py+LangChain+Plotly | ① | 隐式(15iter) | 迭代上限 | 无 | 全量schema | 弱 | ⚪ Plotly=tool思路 | High |
| 16 | **langchain-sql-agent-guide** | Py+LangChain+PG | ① | 隐式 | 弱 | 无 | 全量schema | Callback | ⚪ PG记忆+callback | High |
| 17 | **claude-code-analysis** | 文档(Claude Code泄露源码静态分析) | — | — | — | — | — | — | ⚪ 文献,非机制源 | High |

> 🔴=已抄入 V0.2 架构 §10 ｜ 🟡=新发现可迁移(本轮增量) ｜ ⚪=无直接价值/已归类

---

## 2. 按路线分组详述

### 路线② 多步 Graph（生产级，v0.2 同路线，最值得对照）

**datafoundry / QueryWeaver / SQLBot** —— 已在 [`comparison_matrix.md`](./comparison_matrix.md) + 架构 §10 详述。控制论画像：多重显式闭环（重试/HITL/护栏），强稳定（抗饱和+早退+AST），信息效率高（召回 top-K），可观测强（trace）。

**Chat2DB（🆕 本轮新发现，Java 同栈）**：
- 与 DataAgent **同为 Java/Spring Boot** 栈，是 17 项目中**唯一可直接代码级参考**的（其余 NL2SQL 多为 Python，只能抄机制）。
- 成熟 AI SQL 客户端产品（社区版 chat2db-community-server + client），应有 prompt 管理 / schema 抽象 / 多数据源 / 权限模型。
- **待深挖**：AI SQL 生成模块（prompt 装配、schema 注入、方言处理）、权限模型。本轮仅 README 级，置信度 Low。**建议后续单独源码卡**。

### 路线⑤ 编码 Agent（非数据 Agent，但 Java 同栈可借鉴运行时）

**Jimi（Java/Spring+WebFlux，🆕）**：
- "Java 程序员专属 ClaudeCode"，6 sub-agent + YAML skills/hooks + MCP + AST 代码图 + RAG。
- **Loop Engineering** + **ReCAP 有界记忆**（bounded active prompt + 父子 agent 状态恢复，声称省 30-50% token）。
- **对 v0.2 价值**：DataAgent 长会话里 schema+RAG 上下文膨胀（正是 [`model-gateway-60s-timeout`](../dev/...) 记忆里的大 prompt 问题）→ ReCAP 有界上下文管理可借鉴。

**claw-code-java（Java/Spring AI，🆕）**：
- clean-room Claude Code 运行时：REPL + tool-first loop + JSONL transcript 持久化 + 上下文压缩（recent/summary/max-chars）。
- **对 v0.2 价值**：会话续跑 / transcript 持久化 / 上下文压缩配置模式（v0.2 多轮对话 + 证据链可鉴）。

### 路线① 开环 + 工具库（教学/轻量，价值低）

**pandas-ai（🆕 Python 库）**：LLM→Python 代码→pandas df 就地分析，有 `train`（类 vanna）+ `on_error` 重试 + memory。
- **对 v0.2 价值**：DataAgent 已有 PythonGenerate/Execute/Analyze 链路；pandas-ai 的 **train 反哺 Python 技能** + on_error 重试结构可对照优化（但 v0.2 范式 B 重点不在 Python 链路，价值 Medium）。

**GustoBot（🆕 菜谱领域 multi-agent）**：LangGraph + GraphRAG(Neo4j) + Text2SQL + 向量 KB。
- **分层路由 + fallback cascade**：L1 关键词+LLM 结构化双路由，L2 Map-Reduce planner 分解复杂问题并行工具调用，答案质量级联（structured→vector→external search）+ 双阈值 reranker。
- **对 v0.2 价值**：V0.2 的意图路由（IntentRecognition→范式B/专家模式）+ 复杂查询分解（Planner）可借鉴其分层路由与 fallback 设计。

**sqlchat / text-to-sql-agent / LangChain-可视化 / langchain-sql-guide**：路线①开环，全量 schema，弱稳定。价值：sqlchat 前端 SQL chat UI、text-to-sql-agent 的 LangSmith trace、可视化项目的"Plotly 即 tool"思路——均为教学/单点，v0.2 无核心迁移价值。

### 路线③/④（已归类）

**vanna(③)** 已抄 §10.6。**pgai(④)** 已停维，仅 semantic catalog 思路。**pilotscope(④🆕)**：SIGMOD 论文，Learned Query Optimizer（ML→DB 查询优化），非 NL2SQL，价值在"学习型优化器"概念（v0.3+ 查询优化可参考，非 v0.2）。**postgresai(⑥)**：AI-native PG 可观测，非 NL2SQL。

---

## 3. 对 DataAgent v0.2 的可迁移性矩阵（本轮增量）

| v0.2 需求 | 已抄(架构§10) | 本轮新发现可抄 | 抄谁 |
|---|---|---|---|
| AST 护栏 | ✅ 三层+预处理+表名白名单 | — | datafoundry/SQLBot/QueryWeaver |
| 受控拼装 | ✅ SemanticObject+模板 | Chat2DB Java 同栈 prompt/schema(待深挖) | Chat2DB |
| 自愈闭环 | ✅ heal_and_execute | — | QueryWeaver |
| Schema 召回 | ✅ 四路+BFS | — | QueryWeaver |
| 训练库 | ✅ 三库反哺 | pandas-ai Python 技能 train | vanna/pandas-ai |
| 证据链/Trace | ✅ TraceDag+EvidenceRef | claw-code transcript 持久化 | datafoundry/claw-code |
| **长会话上下文膨胀** | ❌ 未覆盖 | **ReCAP 有界记忆** | **Jimi** 🆕 |
| **意图分层路由** | ❌ 未覆盖 | **L1/L2 分层+fallback cascade** | **GustoBot** 🆕 |
| 行级权限 | ✅(架构§6) | Chat2DB 权限模型(待深挖) | SQLBot/Chat2DB |

---

## 4. 结论与置信度

**① 分类（η₁）**：17 项目归 6 路线。DataAgent v0.2 属路线②（多步 Graph），生产级对照 = datafoundry/QueryWeaver/SQLBot（已抄）+ **Chat2DB（Java 同栈，待深挖）**。

**② 本轮增量（vs 架构 §10）**：
- **Jimi ReCAP 有界记忆** → 补 v0.2 "长会话上下文管理"空白（schema+RAG 膨胀是已知痛点，见 model-gateway-60s-timeout 记忆）
- **GustoBot 分层路由** → 补 v0.2 "意图路由 + 复杂查询分解"设计
- **Chat2DB** → 唯一 Java 同栈，建议单独源码卡深挖 AI SQL 模块

**③ 无价值项（η₂ 排除）**：claude-code-analysis(文档)/postgresai(PG监控)/pilotscope(查询优化器)/路线①四例(教学) —— 不纳入 v0.2 选型。

**④ 置信度**：
- 路线归类与已抄 4 项：**High**（源码级）
- Chat2DB 价值：**Low**（仅 README，待深挖）
- Jimi/GustoBot/pandas-ai 可迁移性：**Medium**（README+结构，机制未深读源码）

---

## 5. 后续建议

1. **Chat2DB 源码卡**（最高优先）：Java 同栈，深挖 AI SQL 生成 / prompt / schema / 权限，补 `docs/research/chat2db.md`。
2. **Jimi ReCAP**：若 v0.2 长会话上下文膨胀成瓶颈，深挖其有界记忆实现。
3. **GustoBot 路由**：若 v0.2 Planner/意图路由需增强，参考其分层 + fallback。

> 控制论推导见 [`NL2SQL_AGENT_CYBERNETICS.md`](../dev/NL2SQL_AGENT_CYBERNETICS.md)；已抄具体设计见 [`V0.2_ARCHITECTURE.md`](../dev/V0.2_ARCHITECTURE.md) §10；对比矩阵见 [`comparison_matrix.md`](./comparison_matrix.md)。
