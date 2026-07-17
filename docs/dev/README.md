# 开发文档（dev）

本目录存放二次开发与联调相关文档。总导航见 [`../README.md`](../README.md)。

## 运行与环境

| 文档 | 说明 |
| :--- | :--- |
| [HYBRID_HOTDEPLOY.md](./HYBRID_HOTDEPLOY.md) | **hybrid 热部署**：IDEA+JRebel + Docker 元库；数据平面红线 |
| [LOCAL_RUN_CHECKLIST.md](./LOCAL_RUN_CHECKLIST.md) | 本地可运行 checklist（模型 / 数据源 / 冒烟） |
| [RUNNABILITY_PHASE.md](./RUNNABILITY_PHASE.md) | 可运行性阶段记录 |
| [API_INTEGRATION.md](./API_INTEGRATION.md) | 前后端 API：代理、模块映射、SSE/上传、联调清单 |

硬约束源：[`.rule/hybrid-hotdeploy-db-safe.md`](../../.rule/hybrid-hotdeploy-db-safe.md)。

## v0.2（现行）

| 文档 | 说明 |
| :--- | :--- |
| [V0.2_PRD.md](./V0.2_PRD.md) | 需求规格：范式 B = NL2Semantic2SQL + 4 支柱 |
| [V0.2_ARCHITECTURE.md](./V0.2_ARCHITECTURE.md) | 架构落地：语义层、受控拼装、护栏、召回 |
| [V0.2_TASKS.md](./V0.2_TASKS.md) | 任务分解 M0–M4 |
| [V0.2_EMBED_DESIGN.md](./V0.2_EMBED_DESIGN.md) | Embed 嵌入设计（HMAC / origins） |

## 智能体实例

| 文档 | 说明 |
| :--- | :--- |
| [INVENTORY_STOCK_FLOW_AGENT.md](./INVENTORY_STOCK_FLOW_AGENT.md) | 库存台账智能体（agentId=6） |
| [MES_PASS_STATION_AGENT.md](./MES_PASS_STATION_AGENT.md) | 扫码过站智能体（agentId=7） |

## 设计与方法论

| 文档 | 说明 |
| :--- | :--- |
| [ENGINEERING_SYSTEM_THINKING.md](./ENGINEERING_SYSTEM_THINKING.md) | 工程化系统思维入门（控制论读代码） |
| [SYSTEM_DESIGN_TOP_DOWN.md](./SYSTEM_DESIGN_TOP_DOWN.md) | 从顶层目标到代码落地 |
| [NL2SQL_AGENT_CYBERNETICS.md](./NL2SQL_AGENT_CYBERNETICS.md) | 问数 Agent 控制论分析；扩展见 [`../research/FULL_CONTROL_THEORY_SURVEY.md`](../research/FULL_CONTROL_THEORY_SURVEY.md) |

> 历史/已取代文档（v0.1 PRD、图谱实施蓝图、superpowers 计划等）→ [`../archive/`](../archive/README.md)。

运行时 OpenAPI：`http://localhost:8065/swagger-ui.html`。
