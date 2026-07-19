# R230 · 系统级壳重构：Workspace / Admin 双 Layout

> 日期：2026-07-19 · 分支 `rebuild-ui` · 功能零变化（SSE / store / graph 冻结）

## 根因

R100–R229 在 token/密度/a11y 微抛光，视觉签名仍像「管理后台里的聊天」。  
对照 DEEIX-Chat 的真正差距是 **信息架构 + 布局系统**：

| DEEIX | DataAgent 旧状 |
|------|----------------|
| 单轨 workspace 侧栏（新会话 + 最近） | 全局 BaseDrawer + ChatSidebar **双轨** |
| Chat 即产品中心 | `/` → `/agent/new`；chat 与 12 项管理平级 |
| 管理入口次级 | mega nav 占主视线 |

## 方案（系统级，非微抛光）

```
layouts/workspace.vue  → /chat 单轨工作台
layouts/default.vue    → 管理壳（知识库/系统/agent/prompt）
```

### Workspace

- 品牌 + 折叠
- **新会话** 主 CTA
- Agent 单行选择
- 会话列表（`ChatSidebar variant="workspace"`，无第二 chrome）
- 底：管理后台 + 用户/退出
- 主区：MessageList + Composer 全宽

### Admin（default）

- 标题改为「管理后台」；去掉机器人头像与 `AI DATA WORKSPACE`
- 顶栏：页面标题 + **返回问答**
- 保留分组导航与 agent 切换（管理页上下文）

### 入口

- `nuxt.config`：`/` → `/chat`

## 冻结

- `stores/chat.ts` SSE 分流
- `services/graph` EventSource
- embed 协议 / 后端 / 元库
- 不新增 npm 依赖

## 验证

```bash
cd data-agent-frontend-nuxt && pnpm build  # SUCCESS
# 手测：/chat 单轨 · agent 切换 · 新会话 · 管理后台 · 返回问答
```
