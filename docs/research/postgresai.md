# postgresai（范围外 · 非 NL2SQL）

> ⚠️ **本项目不参与问数（NL2SQL）对比**，仅作背景收录。
> 本地路径：`/home/ubuntu/dev/reference/postgresai`

## 一句话定位

postgresai 是 PostgresAI 团队的 **AI 原生 PostgreSQL 可观测性工具**——监控、健康检查（45+ 项）、根因分析（RCA），"为人类和 AI Agent 设计"。它是 [`Self-Driving Postgres`](https://postgres.ai/blog/20250725-self-driving-postgres) 计划的一部分，目标是让 Postgres 自治。

## 技术栈

- **Node CLI**（`cli/`，`npx postgresai`）+ **Python reporter**（`reporter/`、`monitoring_flask_backend/`）
- Apache 2.0；支持 PG 14–18
- 仓库内含一个 `pgai/` 子目录（与同目录的 pgai 扩展同源，见 [pgai.md](./pgai.md)）

## 为什么排除在问数对比之外

| 维度 | postgresai | 问数 Agent（NL2SQL） |
| :--- | :--- | :--- |
| 核心任务 | DB 健康/性能/RCA | 自然语言 → SQL → 结果 |
| 输入 | 连接串 + 指标 | 业务问题 |
| 输出 | 健康报告、问题清单 | SQL、数据、图表 |
| LLM 角色 | 消费结构化报告做诊断 | 生成 SQL / 代码 |

postgresai 解决的是"**给 AI Agent 提供修 DB 所需的上下文**"，不是"**把自然语言转成 SQL 查业务数据**"。两者属于不同产品类别。

## → 对 Spring AI 问数 Agent 的间接启示

postgresai 本身不可直接借鉴为问数实现，但它的存在提示一道**正交需求**：

- 问数 Agent 在生产中也需要**数据源健康观测**（连接池、慢查询、锁）——可把 postgresai 式健康检查作为 DataAgent 的「数据源巡检」旁路能力，而非主链路。
- 它"为 LLM 消费而设计的结构化报告"思路，可复刻到问数 Agent 的**错误诊断输出**：执行失败时返回结构化诊断（而非裸 SQL 报错），便于自愈环节或人工排障。

## 相关

- 范围内 8 项目的控制论综合分析：[NL2SQL_AGENT_CYBERNETICS.md](../dev/NL2SQL_AGENT_CYBERNETICS.md) 附录 A（postgresai 在附录中亦标注为"非 NL2SQL"）
