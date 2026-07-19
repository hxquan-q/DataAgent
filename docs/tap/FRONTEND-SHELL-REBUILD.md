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


## 进度

| 阶段 | 状态 | 备注 |
|------|------|------|
| A 归档 | done | `9a8b1fe` + push |
| B 基座 | done | tokens chat-max 1080 / answer 960 · app base |
| C 布局 | done | workspace 272 rail · admin 返回问答 pill |
| D 聊天 | done | 空态同宽 · 消息列对齐 composer · welcome 紧凑 |
| E 管理 | done | page-shell / da-empty 既有 primitive 延续 |
| D 聊天 | done | composer InputGroup 重写 · 状态条 live-only |
| E 管理 | partial | page-shell 既有；持续密度对齐 |
| F 收尾 | in_progress | 截图回归 + embed 对齐 |


## 交付记录

| Commit | 内容 |
|--------|------|
| 9a8b1fe | A 归档 WIP + 方案文档 |
| e669c0d | B/C 基座 token + 双壳宽度 |
| b813b75 | D composer InputGroup 重写 |
| 06785f2 | D 消息区 chrome 次要化 |
| 9100996 | E/F 管理壳 + embed send |
| b834afc | 发送流截图验收 |
| ee3eb9a | 管理 rail 品牌 + 登录纸卡 + 会话密度 |
| (latest) | workspace CTA 克制化 · 消息列间距 · 终态截图 |

后端：`BACKEND-API-CHANGE-LOG.md` 仍为空（未改接口）。


## 验证（最新）

- `pnpm build` ✅（nitro node-server）
- Playwright 路由 smoke：/chat · /system/agents · model-config · eval · security · embed · 无 pageerror
- 后端 API 变更台账：空（未改接口）
