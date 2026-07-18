# rebuild-ui · Task List（DEEIX 视觉）

- [x] S0 创建分支 `rebuild-ui` + 方案文档
- [x] S1 Token / Theme / 全局壳（BaseDrawer · default layout）
- [x] S2 Chat 主表面（Sidebar · Welcome · Input · MessageList）
- [x] S3 报告 / 结果表 / 时间线
- [x] S4 管理页 page-shell 收敛
- [x] S5 a11y + reduced-motion + 窄屏
- [x] 每阶段 `pnpm build` + commit + push（S0–S5 + 持续 polish）
- [x] S6 PR：`rebuild-ui` → `main`（用户仓库）→ https://github.com/hxquan-q/DataAgent/pull/1
- [x] 持续 polish：header/embed/code/table density（多轮 push）
- [ ] 用户验收视觉后 merge PR

**冻结**：chat store SSE、graph EventSource、后端。
