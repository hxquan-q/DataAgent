# rebuild-ui · DEEIX-Chat 视觉语言移植方案

> 分支：`rebuild-ui`  
> 日期：2026-07-18  
> 状态：S0–S5 完成 + 持续 polish · PR #1 open · 待用户验收后 merge  

> 约束：**不影响当前功能实现**（SSE / Pinia / graph / 后端冻结）

## 1. 目标

在 DataAgent（Nuxt 4 + Vuetify 3 + Pinia）上，**只重构前端视觉与交互质感**，借鉴 `~/dev/reference/DEEIX-Chat` 的设计理念：

| DEEIX 理念 | 含义 | DataAgent 落地 |
|---|---|---|
| 轻量而不简陋 | 少装饰、高信息密度 | 去重阴影/硬边，统一 token |
| 纸感表面 | 暖/冷纸底 + 软边 | 浅底、弱描边、柔阴影 |
| 浅色工作台 | 侧栏与内容同调 | 侧栏由深 slate → 浅纸感 |
| 答案优先 | 主内容居中、宽约 960–1080 | 保持 960 内容列，强化气泡/报告层次 |
| 克制动效 | 短 ease-out，可关 | CSS only + reduced-motion |
| 浮动输入 | 输入区像悬浮卡片 | ChatInputArea 圆角/阴影/内边距 |

**不做**：换框架、引入 Tailwind/shadcn/motion、改后端、改 SSE 协议。

## 2. 调色板决策

DEEIX 默认主题偏暖赭，另有 `azure` 主题。DataAgent 问数产品更适合 **azure 理念 + 我们的 `--da-*` 命名**：

- Primary / Ring：清透蓝（DEEIX azure primary）
- Surface：近白纸感
- Sidebar：**浅色** rail（对齐 DEEIX，告别纯深色管理栏）
- Radius：偏大（10–16px 控件，气泡更圆）
- Shadow：低对比多层软阴影

精确 hex 写在 `tokens.css`，`nuxt.config` Vuetify theme 同步。

## 3. 阶段 Task

| Stage | 名称 | 文件焦点 | 完成标准 |
|:---:|---|---|---|
| **S0** | 分支 + 方案 | `rebuild-ui` · 本文档 · task 列表 | 分支存在；方案落盘 |
| **S1** | Token / Theme 壳 | `tokens.css` · `main.css` · `nuxt.config.ts` · `BaseDrawer` · `default.vue` | 全局色板/侧栏纸感；build 过 |
| **S2** | Chat 主表面 | `ChatSidebar` · `ChatWelcome` · `ChatInputArea` · `ChatMessageList` · `chat.vue` | 空态/输入/消息层次对齐 DEEIX |
| **S3** | 输出工件 | `ChatMarkdownReport` · `ChatStreamingReport` · `ChatResultSet` · `ChatWorkflowTimeline` | 报告/表/过程次要层次 |
| **S4** | 管理页收敛 | system 页 `page-shell` 与按钮密度 | 壳一致，不改业务逻辑 |
| **S5** | 迭代抛光 | a11y 焦点、reduced-motion、390 宽 | 截图/手测清单绿 |
| **S6** | 合并准备 | PR 说明 · 与 `main`/`dev` 对比 | 阶段满意后合主分支 |

每 Stage：**改 → build → commit → push origin rebuild-ui**。

## 4. 硬冻结（功能）

- `stores/chat.ts` SSE 分流、会话状态
- `services/graph/index.ts` EventSource
- embed HMAC / 协议
- 后端 Java / yml / SQL
- 不新增 npm 依赖

## 5. 验证

```bash
cd data-agent-frontend-nuxt && pnpm build
# 手测 /chat · 侧栏折叠 · 发送/停止 · 管理列表页
```

## 6. 合并策略

- 日常：`rebuild-ui` 持续 push
- 阶段满意：`rebuild-ui` → `dev` 或用户指定的主工作分支；再择机进 `main`
- 不 force-push `main`/`dev` 已有历史

## 7. 参考路径

- DEEIX globals：`/home/ubuntu/dev/reference/DEEIX-Chat/frontend/app/globals.css`
- Chat empty / input / sidebar：`features/chat/**` · `features/layouts/**`


## 8. 持续 polish 日志（`origin/rebuild-ui`）

- 管理页统一 `KnowledgePageHeader` + paper `page-shell` 卡片
- 聊天：flat paper bubbles、过程次要化、代码块/报告头/结果操作按钮 token 化
- Embed 默认主题改 azure，纸感顶栏 + 浮动输入
- 全局：pill 搜索框、segmented tabs、dialog paper、soft scrollbars、table density
- header height token · drawer scroll · query evidence paper
- metric-version diff 列 · markdown table paper · 语义色板 lighten/darken 清理
- PR：https://github.com/hxquan-q/DataAgent/pull/1 （待用户验收 merge）
