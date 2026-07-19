# 问数 Agent 参考项目资料（源码级）

> 本目录是「基于 **Spring AI Alibaba Graph** 构建问数（NL2SQL）Agent」的**源码级参考资料库**。
> 每张卡片都从参考项目本地源码（`/home/ubuntu/dev/reference/*`）抽取，落到具体类/方法/行号。

## 这份资料和已有文档的关系

| 文档 | 性质 | 看什么 |
| :--- | :--- | :--- |
| **本目录**（`docs/research/`） | **源码级原始资料** | 每个项目"代码里到底怎么做的"、关键源码路径、可借鉴/可规避点 |
| [`docs/dev/NL2SQL_AGENT_CYBERNETICS.md`](../dev/NL2SQL_AGENT_CYBERNETICS.md) | **控制论综合分析** | 把 9 项目归为 4 条技术路线、用工程控制论推导构建蓝图与增强优先级 |
| [`docs/ARCHITECTURE.md`](../ARCHITECTURE.md) | 当前 DataAgent 架构 | 对比基准（StateGraph 主流程图） |

> **阅读顺序建议**：先读本 README 的四路线总览 → 按需查单项目卡片 → 再读控制论综合分析看"为什么这样选"。

---

## ⚠️ 事实勘误（源码级核对，纠正既有认知）

源码卡片在核对中发现综合文档里几处技术栈描述与实际源码不符，开发时请以本目录为准：

| 项目 | 既有（错误）认知 | 源码实际 |
| :--- | :--- | :--- |
| **SQLBot** | Go 后端 + React 前端 | **Python/FastAPI** 后端 + **Vue 3** 前端；g2-ssr 是独立 Node 渲染微服务 |
| **QueryWeaver** | TS/Node 多步 Agent | **Python/FastAPI** 后端（`api/`）+ React/TS 前端（`app/`）；LLM 抽象用 **LiteLLM**（非 LangChain） |
| **pgai** | "DB 内 embedding" / 依赖 TimescaleDB | worker 仍把数据**拉出库**调 embedding API，下沉的只是"调度与一致性状态"；扩展仅依赖 `pgvector + plpython3u`，**不依赖 TimescaleDB** |
| **pgai 维护** | （未明确） | **已于 2026.02 停止维护**（README 顶部声明） |
| **vanna** | 经典 RAG NL2SQL | 2.0 把经典 NL2SQL RAG 核心**整体移入 `legacy/`**，新 `core/` 是通用 Agent 框架 |

---

## 四条技术路线总览

把 8 个参考项目（+ DataAgent 自身）归为 4 条控制论路线。**用控制论眼光看，它们是 4 种不同的控制结构**：

```mermaid
flowchart LR
    subgraph R1["路线① 开环控制（弱稳定）"]
        A1[text-to-sql-agent]
        A2[LangChain-SQL-可视化]
        A3[langchain-sql-guide]
    end
    subgraph R2["路线② 多重闭环监督（强稳定）"]
        B1["DataAgent（当前·Java/Spring AI Graph）"]
        B2[SQLBot]
        B3[QueryWeaver]
    end
    subgraph R3["路线③ 自适应/学习"]
        C1[vanna]
    end
    subgraph R4["路线④ 测量前置/传感下沉"]
        D1[pgai]
    end
    R1 -.唯一借鉴.trace/记忆.-> R2
    R3 -.借鉴.三库训练反哺.-> R2
    R4 -.借鉴.semantic catalog.-> R2
```

### 速查表

| 路线 | 控制结构 | 反馈 | 稳定性 | 自适应 | 代表 | 价值 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| ① 开环 Agent | LLM 自主循环 | 隐式 | 弱（迭代上限） | 无 | text-to-sql-agent 等 3 例 | 🟡 教学 |
| ② 多步 Graph | 多重显式闭环 | 强（抗饱和+早退+HITL） | 强 | 无 | **DataAgent** / SQLBot / QueryWeaver | 🔴 生产 |
| ③ RAG 训练 | 学习型 | 样本反哺 | 中 | **有** | vanna | 🔴 高 |
| ④ DB 原生 | 测量前置 | — | 强（DB 一致） | 无 | pgai（已停维） | 🟡 中 |

> **结论**：当前 DataAgent 已选路线②，且是 9 项目中**最完整**的（HITL + Python 深度分析 + MCP）。真正该补的是路线③的自适应能力（SQL 样例训练库）——详见控制论文档 §5。

---

## 卡片索引

### 路线② · 显式多步 Graph（与 DataAgent 同路线，最值得对照）

- [SQLBot](./sqlbot.md) — Python/FastAPI + Vue 3，**AST 级 SQL 只读校验 + 表名白名单 + 行级权限改写 + g2-ssr 图表**（生产级安全护栏标杆）
- [QueryWeaver](./queryweaver.md) — Python/FastAPI + LiteLLM，**HealerAgent ×3 自愈 + FalkorDB 图检索 schema/外键 + 破坏性 SQL 拦截**
- [Chat2DB](./chat2db.md) — **Java/Spring 同栈**（唯一），AI SQL 客户端，**分层 system prompt + 独立合规层 + 方言 JSON 元数据 + SPI 插件化**（抄工程脚手架，不抄 LLM 直出算法）

### 路线③ · RAG 训练式（自适应能力来源）

- [vanna](./vanna.md) — Python，**三库训练（question/sql/ddl/documentation）+ 向量库可插拔 Mixin + RLS**；2.0 经典 RAG 核心在 `legacy/`

### 路线④ · DB 原生

- [pgai](./pgai.md) — PG 扩展，**vectorizer worker + semantic catalog 事件触发器自维护**；⚠️ 2026.02 停维

### 路线① · 开环 Agent（教学级，合并一卡）

- [LangChain 开环 SQL Agent 三例](./langchain-openloop-agents.md) — text-to-sql-agent（LangSmith trace）/ SQL 可视化（Plotly 工具）/ langchain-sql-guide（PG 记忆 + Callback）

### 范围外

- [postgresai](./postgresai.md) — 非 NL2SQL，是 PG 可观测性工具，仅作背景

### 横向对比

- [对比矩阵](./comparison_matrix.md) — 特性 × 项目 一览表（含 DataAgent 基准）

---

## 给 Spring AI Alibaba Graph 构建者的三句话

1. **稳定性先于精度**（控制论 η₅）：先把 SQLBot 式 **AST 只读护栏 + 表名白名单** 和 QueryWeaver 式 **自愈闭环（带 attempts 上限）** 落地，再谈准确率。
2. **Schema 召回是主导极点**（η₄）：照 QueryWeaver 的**外键关系子图召回**，不要像路线①那样全量 schema 塞上下文。
3. **让系统越用越准**：补 vanna 式 **SQL 样例三库训练反哺**——这是当前 DataAgent 相对各竞品最大的可补强点，投入小收益大。

> 每条决策的控制论推导见 [NL2SQL_AGENT_CYBERNETICS.md](../dev/NL2SQL_AGENT_CYBERNETICS.md) §4。
