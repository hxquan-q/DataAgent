# 前端 UI 壳层完全重构（DEEIX 对照 · Nuxt 原地）

> 分支：`rebuild-ui`  
> 日期：2026-07-19  
> 状态：实施中  
> 旧路径：R229–R231 / REBUILD-UI-DEEIX-PLAN 仅作考古，**不作为实施清单**

## 目标

在 `data-agent-frontend-nuxt` 内做壳层与视觉信息架构重写：保留 services、`stores/chat` SSE、axios、后端协议；对照 DEEIX-Chat 工作台（浅色 rail、答案优先、浮动 composer、空态居中、过程次要），用 Vuetify + `--da-*` 重写 workspace 聊天面 + 管理壳 + 通用表面。

## 约束

- 不换框架（禁止 Next/React/Tailwind/shadcn）
- 默认不改后端；必改时先写 `BACKEND-API-CHANGE-LOG.md`
- 每阶段：`pnpm build` → 截图/手测 → commit → push `origin/rebuild-ui`

## 阶段

| 阶段 | 交付 |
|------|------|
| A 归档 | WIP 归档 + 本文档 + 后端台账 |
| B 基座 | token/theme |
| C 布局 | workspace + default 双壳 |
| D 聊天 | 空态/composer/消息/过程/报告 |
| E 管理 | 列表/表单壳统一 |
| F 收尾 | embed + a11y + PR 说明 |

## DEEIX 落地映射

| 理念 | DataAgent |
|------|-----------|
| 轻量而不简陋 | 少装饰、高信息密度 |
| 纸感表面 | 浅底、弱描边、柔阴影 |
| 浅色工作台 | workspace 浅色 rail |
| 答案优先 | 主内容 960–1080，过程折叠 |
| 克制动效 | CSS only + reduced-motion |
| 浮动输入 | composer 圆角/软阴影 dock |

## 验证

```bash
cd data-agent-frontend-nuxt && pnpm build
# /chat 空态 · 发送/停止 · 侧栏折叠 · 管理列表 · embed
```
