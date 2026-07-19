# R231 Task · DEEIX 对话主表面高保真移植（方案 B）

> **用途**：新开对话直接执行本文件，不要再做 R229 式 token/a11y/侧栏微抛光。  
> **日期**：2026-07-19 · 分支建议：`rebuild-ui`（或从当前工作树开新分支）  
> **用户决策**：B — 按 DEEIX 组件树对照移植对话主表面；后端可适当调整。

---

## 0. 新对话启动提示词（复制即用）

```text
/goal 按 docs/tap/R231-deeix-chat-surface-task.md 执行 DEEIX 对话主表面高保真移植（方案 B）。
约束：对照 ~/dev/reference/DEEIX-Chat 的 chat empty/input/message 结构移植到 data-agent-frontend-nuxt；
不追求 shadcn/Tailwind 换栈；SSE 协议尽量保留；功能不回归。
禁止：再做侧栏密度/token 微调循环。
验收：空态=大标题+一体 composer 居中；有消息=open canvas+底栏 dock；过程时间线默认折叠不占主视线。
```

---

## 1. 目标（成功长什么样）

对照 `~/dev/reference/DEEIX-Chat`，把 DataAgent **对话主界面**做到「同一产品语言」，不是「颜色像一点」。

| 状态 | 必须像 DEEIX 的点 |
|------|-------------------|
| **空态** | 垂直居中：大号 economist 问候标题（可选静默 agent badge）→ **同一** floating composer 嵌在标题下方；无机器人大卡、无双轨会话栏、无重 checklist 卡片 |
| **有消息** | open canvas：用户气泡轻、助手答案主列无 avatar；composer **底栏** pure dock（rounded-3xl / border 0.5 / pure 白底 / soft shadow） |
| **过程** | 工作流步骤 **默认折叠/次要**，不与答案并列抢屏（当前 8 步时间线全开 = 最大违和） |
| **报告** | 答案优先：报告/Markdown 为主阅读面；去重边框卡感，过程可展开查看 |

**非目标**

- 不搬 DEEIX 的附件/语音/MCP/skills/截图分享等无关能力  
- 不强制引入 Tailwind / shadcn / motion  
- 不重做全部管理页（R230 双壳可保留）

---

## 2. 现状与误区（执行前必读）

### 2.1 已完成（可保留）

| 项 | 路径 |
|----|------|
| 双 Layout 壳 | `app/layouts/workspace.vue`（/chat 单轨）· `default.vue`（管理 + 返回问答） |
| 入口 | `nuxt.config.ts`：`/` → `/chat` |
| Chat 会话轨嵌入 | `ChatSidebar variant="workspace"` |
| 文档 | `docs/tap/R230-system-shell-dual-layout.md` · `REPORT-R230.md` |

### 2.2 半成品 / 方向错误（新对话要收尾或重写）

工作树可能含未完成 empty-stage 改动，**以本任务验收为准，不要延续微抛光**：

- `pages/chat.vue`：`isEmptyCanvas` / `chat-empty-stage`（结构对，需 polish 到 DEEIX 构图）  
- `ChatMessageList`：欢迎已挪出列表，但 **timeline/report 仍重卡**  
- `ChatInputArea`：圆形发送已有，但 **整体仍是 status+textarea+option 三层 Vuetify 味**  
- `ChatWorkflowTimeline`：过程默认展开占主视线  

### 2.3 真差（截图根因）

DataAgent 当前有消息态 ≈ **报告卡 + 过程 8 步 + 工具条**。  
DEEIX ≈ **答案文本/工件 + 轻 meta + 底栏一体 InputGroup**。

继续改 `--da-*` / padding **达不到效果**。必须改 **消息呈现模型 + composer 结构**。

---

## 3. 参考地图（只读对照，禁止整仓复制）

| DEEIX | 路径 | DataAgent 对应 |
|-------|------|----------------|
| Empty | `frontend/features/chat/components/sections/chat-empty.tsx` | `ChatWelcome.vue` + `chat.vue` empty stage |
| Input | `.../sections/chat-input.tsx` + `components/ui/input-group.tsx` | `ChatInputArea.vue` |
| Area 组合 | `.../app-chat-area.tsx`（空态嵌套 Input） | `pages/chat.vue` |
| User msg | `.../message/message-user.tsx` | `ChatMessageList` user 分支 |
| Bot msg | `.../message/message-bot.tsx` | assistant / report / timeline 分支 |
| Process | `.../message/message-process-trace.tsx` 等 | `ChatWorkflowTimeline.vue` |
| Tokens | `frontend/app/globals.css`（pure / economist / shadow-xs） | `assets/css/tokens.css` |

**移植原则**：抄 **结构与层级**（DOM 角色、默认折叠、空态嵌套），不抄 React 运行时。

---

## 4. 工作包（建议顺序 · 每包可度量）

### WP0 · 基线（30min）

1. `git status`：确认 `rebuild-ui`；决定 commit 现有 R230 或 stash 半成品 empty-stage  
2. 浏览器截图：空态、有消息（含 timeline）、composer  
3. 手测：登录 → `/chat?agentId=*` → 发一问 → 看过程/报告  

**产出**：`docs/tap/R231-baseline-shots.md`（或放图路径）

### WP1 · 页面编排（核心结构）

**文件**：`app/pages/chat.vue`

```
empty:
  [ChatWelcome 仅标题/badge/可选 chips]
  [ChatInputArea 同一实例，嵌在中间]

conversation:
  [ChatMessageList 仅消息]
  [ChatInputArea 底栏 dock]
```

- `layout: 'workspace'` 保持  
- `isEmptyCanvas`：`!streaming && messages.length===0`  
- 首条消息：无 session 时 `createNewSession` 再 `sendMessage`（`ChatInputArea.handleSend`）

**验收**：空态垂直居中一体；发消息后自动切到底栏布局。

### WP2 · Composer 一体 InputGroup 形态

**文件**：`ChatInputArea.vue` · 必要时 `tokens.css`

对照 DEEIX `InputGroup`：

1. **单容器** pure 白底 · `border 0.5` · `radius ~24px` · `shadow-xs`  
2. **上**：模型/数据源 — ghost pill，不要彩色重 chip  
3. **中**：textarea 15px / line-height ~1.5 · 无内框  
4. **下**：次要选项（SQL/NL2SQL/反馈）ghost 文字 · 右下 32 圆发送/停止  
5. 空态与会话态 **同一组件**，仅外层 margin 不同  

**禁止**：再叠一层「状态栏卡片」。  
**验收**：截图对照 DEEIX dock 轮廓一致（功能控件可保留 DataAgent 语义）。

### WP3 · 消息 open canvas

**文件**：`ChatMessageList.vue` · `ChatMarkdownReport.vue`（轻改）

1. 用户：轻气泡/无边重影 · 无 avatar  
2. 助手答案（markdown-report / html / text）：**透明/无卡**主列阅读  
3. result-set：可保留轻 table 容器，但不要双层 elevation  
4. 顶 status strip：极简一行或移入 composer 已有选择器，避免第三条 chrome  

**验收**：有消息页主视线 = 问答内容，不是灰框套灰框。

### WP4 · 过程降噪（最大违和点）

**文件**：`ChatWorkflowTimeline.vue` · MessageList 中 timeline 分支

1. **默认折叠**为一行 meta：`过程 · N 步 · 点击展开`  
2. 展开后才显示节点列表；单节点细节默认收起  
3. 流式中：可用细进度/一行「分析中」，不要整棵树常驻  
4. timeline 与 report 同屏时：**report 在上/主**，timeline 在下/次  

**可选后端**（若前端折叠仍乱）：

- 持久化 timeline JSON 结构不变  
- 或增加 `processSummary` 字段仅推摘要（非必须，WP4 前端优先）

**验收**：默认截图几乎看不到 8 步全树。

### WP5 · Welcome 瘦身

**文件**：`ChatWelcome.vue`

1. 只保留：时段问候 h1 + 可选 agent badge  
2. 预设问题 chips：轻 outline，无重影  
3. readiness：一行文字 + 链接即可，**去掉**大 checklist 卡  
4. 无机器人图标 hero  

**验收**：空态 = DEEIX empty 的「一句话 + 输入框」心智。

### WP6 · 回归与冻结检查

```bash
cd data-agent-frontend-nuxt && pnpm build
```

手测清单：

- [ ] 登录 → `/` 进 `/chat`  
- [ ] 切换 agent（workspace 下拉）会话重载  
- [ ] 新会话 · 发消息 · 停止 · 人工反馈（若开启）  
- [ ] 模型/数据源切换  
- [ ] 历史会话切换  
- [ ] 管理后台 ↔ 返回问答  
- [ ] embed 页未误伤（`layout:false`）  

**硬冻结（除非 WP4 可选后端）：**

- `services/graph` EventSource URL/事件名  
- embed HMAC/协议  
- 元库 / `SQL_INIT` / docker volume  

**允许改：**

- `Chat*` 组件模板/CSS  
- `pages/chat.vue`  
- 展示层 store 字段（如默认折叠 flag）  
- 必要时 timeline 渲染数据裁剪  

---

## 5. 刻意不做（防再进循环）

- ❌ 再开 R232「侧栏再窄 8px」  
- ❌ 全站 design-system 大翻  
- ❌ 引入 Tailwind/shadcn 只为像 DEEIX  
- ❌ 重写 SSE 业务语义（除非验收卡死）  
- ❌ 同时大改管理 12 页  

---

## 6. 交付物

| 交付 | 说明 |
|------|------|
| 代码 | WP1–WP5 落地，`pnpm build` 绿 |
| 报告 | `.ccg/tasks/ui-ux-agent-optimize-loop/REPORT-R231.md` |
| 方案 | 本文件末尾「偏离记录」一节补实际 diff 摘要 |
| 截图 | 空态 / 有消息-折叠过程 / composer 三张 |

---

## 7. 建议提交节奏

1. `chore(ui): snapshot before R231 chat surface`（可选，保 R230）  
2. `feat(ui): DEEIX empty stage nests composer`  
3. `feat(ui): composer pure dock + demote process timeline`  
4. `feat(ui): open canvas messages and quiet report chrome`  

---

## 8. 风险

| 风险 | 缓解 |
|------|------|
| timeline 折叠丢调试信息 | 展开完整保留；开发者可用「展开过程」 |
| 空态/会话双挂载 Input 丢输入 | **同一路由下 v-if 切换**会销毁实例——可接受；或 `v-show` 保 draft |
| 与 R230 workspace 冲突 | chat 只用 workspace；MessageList 不再渲染第二侧栏 |
| 用户说「还是不像」 | 对照截图查 WP4 过程是否仍展开；优先砍过程不是加阴影 |

---

## 9. 完成定义（DoD）

同时满足：

1. 空态截图：标题 + 一体 composer 居中，无双轨、无机器人大卡  
2. 有消息截图：答案主列 + 底栏 dock；过程默认不抢屏  
3. `pnpm build` SUCCESS  
4. 上列手测清单全勾  
5. 无元库/SSE 协议破坏  

---

## 10. 给执行 Agent 的优先级口令

> **η₁ 分类**：这是对话主表面结构问题，不是 token 问题。  
> **η₄ 主导因素**：过程时间线全开 + 报告重卡 + 空态/composer 分离。  
> **一次只推主导瓶颈**：先 WP4 过程降噪 + WP1 空态嵌套，再 WP2 composer。  
> **禁止**：R229 循环动作（focus-visible / 侧栏 28px / 再写一篇 polish 报告）。
