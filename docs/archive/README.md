# 文档归档（archive）

本目录存放**历史/已被取代**的项目文档，保留备查但从活跃文档树移出。内容不再维护，与当前代码可能不一致——以 `docs/` 根、`docs/dev/`、`docs/research/` 的现行文档为准。

> 归档非删除：如需恢复，移回原目录即可（`git` 亦有历史）。

## 归档清单

| 文件 | 原位置 | 归档原因 |
| :--- | :--- | :--- |
| `data-agent-architecture-design.md` | `docs/` | **fork 前的"北极星"设计稿**，技术栈假设为 FastAPI + LangGraph + Claude API，与本仓库实际的 Spring AI Alibaba Graph 实现完全不符。其设计思想（"概率生成→确定性构建"）已吸收进 `docs/dev/V0.2_PRD.md`。标注 `status: 设计中`。 |
| `V0.1_PRD.md` | `docs/dev/` | v0.1 需求文档，已被 `docs/dev/V0.2_PRD.md` 取代（v0.1 工作已交付于 `v0.1-integration` 分支，v0.2 PRD §0.3 记录此谱系）。 |
| `SPRING_AI_GRAPH_IMPLEMENTATION_PLAN.md` | `docs/dev/` | v0.1 的 M1-M4 实施蓝图，已落地实现，v0.2 仅作为谱系引用。 |
| `V0.1_CODEX_WAVES.md` | `docs/dev/` | 多 codex 并行调度的会话执行日志（Wave -1/0/1/2/3 + worktree 占用锁），工作已完成，无未来参考价值。 |
| `V0.1_STATE_CONTRACT.md` | `docs/dev/` | v0.1 worker 的状态键 + skill 表 DDL 契约，概念已落入 `Constant.java` / `schema.sql`。 |
| `superpowers/` | `docs/superpowers/` | 整个目录为早期会话工作产物（superpowers 工具的 TDD 测试 / 后端 clean-code 计划与设计），对应工作已通过上游 PR（#481/#483/#550 等）落地，代码即真相。其中 `mock-strategy-guide.md`、`nodes-dependency-analysis.md` 的信息已被根 `CLAUDE.md` 的 Dispatcher/DAG 表与常量索引更完整地覆盖。 |

## 保留在活跃文档树的现行文档

- `docs/` 根：上游双语文档（ARCHITECTURE / ADVANCED_FEATURES / DEVELOPER_GUIDE / KNOWLEDGE_USAGE / QUICK_START，EN+CN）—— 准确描述 as-built Spring AI Alibaba Graph 系统。
- `docs/dev/`：v0.2 现行规格（V0.2_PRD / V0.2_ARCHITECTURE / V0.2_TASKS）+ 新手向（ENGINEERING_SYSTEM_THINKING / SYSTEM_DESIGN_TOP_DOWN / API_INTEGRATION）+ 控制论设计（NL2SQL_AGENT_CYBERNETICS）。
- `docs/research/`：参考项目研究卡（datafoundry / QueryWeaver / SQLBot / vanna / Chat2DB / GustoBot / WeKnora 等），本会话产出，现行。
