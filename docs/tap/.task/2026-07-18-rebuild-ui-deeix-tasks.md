# rebuild-ui · Task List（DEEIX 视觉）

- [x] S0 创建分支 `rebuild-ui` + 方案文档
- [x] S1 Token / Theme / 全局壳（BaseDrawer · default layout）
- [x] S2 Chat 主表面（Sidebar · Welcome · Input · MessageList）
- [x] S3 报告 / 结果表 / 时间线
- [x] S4 管理页 page-shell 收敛
- [x] S5 a11y + reduced-motion + 窄屏
- [x] 每阶段 `pnpm build` + commit + push（S0–S5 + 持续 polish）
- [x] S6 PR：`rebuild-ui` → `main`（用户仓库）→ https://github.com/hxquan-q/DataAgent/pull/1
- [x] 持续 polish：header/embed/code/table density/nav/security/dialogs（多轮 push）
- [x] 停止微 polish 循环（边际收益过低）
- [x] **answer-first 对话排版**（DEEIX 消息层级 · `9507101`）
- [x] **floating composer dock + welcome empty**（DEEIX InputGroup 语义）
- [x] **sidebar densify + process secondary**（DEEIX nav/process 层级）
- [x] **report prose + quiet chrome**（报告正文 15px + 状态条去胶囊）
- [x] **result-set ghost meta**（结果表工具条 ghost · DEEIX message-meta）
- [x] **thinking demote + admin table quiet + expand fab**
- [x] **login quiet + header meta**（DEEIX 居中登录 / 顶栏去 chip）
- [x] **global nav densify**（侧栏 h-8 导航行 · 弱新建 agent）
- [x] **page header ink + dialog soft**（管理页标题去 primary · 对话框 10px 按钮）
- [x] **admin cards quiet + denser segmented**（模型卡去抬升 · 分段控件 36px）
- [x] **embed answer-first dock**（嵌入聊天对齐主 chat 层级）
- [x] **agent create form + toast soft**（新建表单扁平 · snackbar 轻边）
- [x] **empty states quiet + dashboard soft**（空态无重虚线框 · 看板无卡片阴影）
- [ ] **用户验收视觉后 merge PR**（下一门控，需人工）

**冻结**：chat store SSE、graph EventSource、后端。

**下一阶段建议**（等用户选一）：
1. 人工验收 `rebuild-ui` 后 merge PR #1
2. 或切到「后端智能体质量/速度」实质性迭代（勿再堆 CSS 微改）
