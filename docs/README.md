# 文档导航

> Git 只跟踪**可协作交付**的文档。本机密钥、数据样本、Agent 会话状态不入库。

## 分层

| 层 | 路径 | 用途 | 入库 |
|----|------|------|------|
| 产品公开 | `docs/*.md`（根下中英篇） | 快速开始 / 架构 / 开发者指南 | ✅ |
| 现行开发 | [`docs/dev/`](./dev/README.md) | hybrid 热部署、v0.2、台账/过站智能体、API 联调 | ✅ |
| 研究备查 | [`docs/research/`](./research/README.md) | 竞品与控制论调研 | ✅ |
| 历史归档 | [`docs/archive/`](./archive/README.md) | v0.1 / 已取代设计稿 | ✅ |
| 本机数据 | `docs/data/`、`docs/.db.env` | 连接串、字典样本 | ❌ ignore |
| 项目规则 | [`.rule/`](../.rule/README.md) | 硬约束（元库零影响等） | ✅ |
| Agent 身份 | 根 `CLAUDE.md` / `AGENTS.md` / `.claude/` | 本机 AI 会话配置 | ❌ ignore |

## 产品文档（公开）

| 文档 | 说明 |
| :--- | :--- |
| [QUICK_START.md](./QUICK_START.md) · [en](./QUICK_START-en.md) | 快速开始 |
| [ARCHITECTURE.md](./ARCHITECTURE.md) · [en](./ARCHITECTURE-en.md) | 架构 |
| [DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md) · [en](./DEVELOPER_GUIDE-en.md) | 开发者指南 |
| [ADVANCED_FEATURES.md](./ADVANCED_FEATURES.md) · [en](./ADVANCED_FEATURES-en.md) | 高级能力 |
| [KNOWLEDGE_USAGE.md](./KNOWLEDGE_USAGE.md) · [en](./KNOWLEDGE_USAGE-en.md) | 知识用法 |

## 从哪读起

| 角色 | 入口 |
|------|------|
| 新人跑通本地 | [`dev/LOCAL_RUN_CHECKLIST.md`](./dev/LOCAL_RUN_CHECKLIST.md) + [`dev/HYBRID_HOTDEPLOY.md`](./dev/HYBRID_HOTDEPLOY.md) |
| 二次开发 / v0.2 | [`dev/README.md`](./dev/README.md) |
| 运维红线 | [`.rule/hybrid-hotdeploy-db-safe.md`](../.rule/hybrid-hotdeploy-db-safe.md) |
| 产品概览 | 根 [`README.md`](../README.md) |

运行时 API：后端 Swagger `http://localhost:8065/swagger-ui.html`。
