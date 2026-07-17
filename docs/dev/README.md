# 开发文档（dev）

本目录存放二次开发与联调相关文档。

| 文档 | 说明 |
| :--- | :--- |
| [V0.2_PRD.md](./V0.2_PRD.md) | **v0.2 需求规格（现行）**：范式 B = NL2Semantic2SQL 全迁移 + 4 支柱（AST 护栏 / 语义层 / 样例库 / 证据链） |
| [V0.2_ARCHITECTURE.md](./V0.2_ARCHITECTURE.md) | v0.2 架构落地：语义层四表、受控拼装、安全护栏、召回接线、参考项目抄录（§10） |
| [V0.2_TASKS.md](./V0.2_TASKS.md) | v0.2 任务分解：M0-M4 工作流任务（WS）拆分 |
| [ENGINEERING_SYSTEM_THINKING.md](./ENGINEERING_SYSTEM_THINKING.md) | 工程化系统思维入门：用《工程控制论》四步法读懂现有 DataAgent 代码（新手向） |
| [SYSTEM_DESIGN_TOP_DOWN.md](./SYSTEM_DESIGN_TOP_DOWN.md) | 从顶层目标到代码落地的系统实现讲解 |
| [NL2SQL_AGENT_CYBERNETICS.md](./NL2SQL_AGENT_CYBERNETICS.md) | 问数 Agent 的控制论分析：竞品对比与构建蓝图（设计/评估向）；`docs/research/FULL_CONTROL_THEORY_SURVEY.md` 是其扩展 |
| [API_INTEGRATION.md](./API_INTEGRATION.md) | 前后端 API 对接：代理配置、模块映射、接口明细、SSE/上传约定、联调清单 |
| [INVENTORY_STOCK_FLOW_AGENT.md](./INVENTORY_STOCK_FLOW_AGENT.md) | **库存台账智能体（agentId=6）**：asd_standard 连库搭建、质量优化、指标/语义别名、图表报告实测记录 |

> 历史/已取代文档（v0.1 PRD、图谱实施蓝图、会话日志、北极星设计稿、superpowers 计划等）已移至 [`../archive/`](../archive/README.md)，备查不再维护。

运行时完整 OpenAPI 以后端 Swagger 为准：`http://localhost:8065/swagger-ui.html`。

## Local run

- [LOCAL_RUN_CHECKLIST.md](./LOCAL_RUN_CHECKLIST.md) — IDEA + Docker MySQL 可运行路径（模型/数据源/重启）
