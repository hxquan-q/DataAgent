# 任务分解：前端 UI/UX 设计体系重建（会审修订 v1.1）

> 对应方案：`docs/tap/.plan/2026-07-17-frontend-design-system.md`  
> 状态：**W0a 执行中/已完成**（用户批「执行，不影响功能」→ 仅 W0a；W0b 未开）  
> 依赖：无后端/元库；与 admin-auth 并行见 plan §4.8  

---

## 重构纪律（批准后执行时遵守）

| 允许 | 禁止 |
|------|------|
| 布局/页壳/展示 DOM 拆合、token 化、Ds 抽取 | 改 SSE/store 分流/messageType 语义 |
| system 页模板换皮、密度、页头统一 | 改 API DTO / embed 协议 / graph service |
| chat Sidebar/Welcome/Input **外壳**重构 | 改发送 payload、EventSource 契约 |
| 按 Wave 渐进，每波可回滚 | 单 PR 无波次重写 40 页或拆掉 Vuetify |

---

## 执行闸门

| 闸 | 条件 | 未满足 |
|----|------|--------|
| **G0** | 用户明确批准「选项 C 修订版 v1.1（含 UI 重构）」 | **禁止写业务代码** |
| **G1** | T0 基线 build +（建议）体积记录 | 禁止合入宣称完成 |
| **G2** | 仅先开 **W0a**；W0b 需**再次**确认 | 未过 W0a 冒烟禁止 W0b/W2 |
| **G3** | 每 PR：build 绿 + 冒烟 + proxy 审 + **无冻结区逻辑 diff** | 不合并 |

---

## T0 · 会审与基线 [P0]

- [x] T0.1 专家会审（UI/UX + 前端工程 + 架构）→ 结论已回写 plan  
- [x] T0.2 用户批「执行，不影响功能」→ 范围锁定 **仅 W0a**（不含 W0b）  
- [x] T0.3 基线：既有 `.output/public` ~3.8M；W0a 后 `pnpm build` 成功  
- [x] T0.4 确认不 `down -v` / 不改 SQL_INIT  

**DoD**：用户批准；T0.3 有记录。

---

## T1 · Design Tokens 中枢 = W0a 前半 [P0]

- [x] T1.1 新增 `app/assets/css/tokens.css`（完整组见 plan §4.1）  
  - 色 + on-\* + sidebar-\* + ring + space/control-height/table-row  
  - radius/shadow + dur-fast/base/slow + z-\* + overlay + disabled  
  - `.dark` 预留  
- [x] T1.2 `main.css` `@import` tokens；现有 `--da-*` **别名到同一值**  
- [x] T1.3 收敛重复 `:root`；统一 duration（弃 0.45s 默认交互）  
- [x] T1.4 `breathe`/page transition 等纳入 `prefers-reduced-motion`  
- [x] T1.5 注释：禁止组件新增业务主色 hex  

**DoD**：全站仍正常；token 单源；build 绿。

---

## T2 · Vuetify Theme Bridge = W0a 后半 [P0]

- [x] T2.1 `nuxt.config` theme.light 静态 hex **与 tokens 同步**  
- [x] T2.2 保留 VBtn outlined defaults  
- [x] T2.3 `default.vue` + `BaseDrawer` 侧栏改用 sidebar-\* token（菜单数据/路由逻辑未改）  
- [x] T2.4 `pnpm build` 绿（浏览器冒烟待本地 hybrid 确认）  
- [x] T2.5 Design System 约定写入 `data-agent-frontend-nuxt/CLAUDE.md`  

**DoD**：主色对齐；W0a build 过 → **体系已建立（W0a）**。

---

## T3 · Tailwind + shadcn-vue = W0b [P1 · 可选闸门]

> **仅当用户确认需要 shadcn 岛后执行**。参考 https://www.shadcn-vue.com/docs/installation/nuxt  

- [ ] T3.0 `tailwind.css` **不** import preflight（注释写明保护 Vuetify）  
- [ ] T3.1 安装 tailwindcss、@tailwindcss/vite  
- [ ] T3.2 shadcn-nuxt；`prefix: 'Ui'`；验证与 `pathPrefix: false` 无裸名冲突  
- [ ] T3.3 `@theme inline` 映射 tokens；CSS 顺序 tokens→main→tailwind  
- [ ] T3.4 A/B 截图：`/chat` `/system/agents` `/system/metrics` + 侧栏；失败则缩 scope  
- [ ] T3.5 `components.json` + `app/lib/utils.ts`（`cn`）  
- [ ] T3.6 CLI **仅** add：`button` `card` `badge` `separator`  
- [ ] T3.7 `nuxt.config` 手工合并；硬审 proxy/ssr/vuetify modules  
- [ ] T3.8 **提交/保留** `pnpm-lock.yaml`；文档注明 Docker frozen-lockfile + generate  
- [ ] T3.9 体积对比 T0；+15% 解释 / +25% 砍组件  
- [ ] T3.10 可选 dev playground `/dev/ds-playground`（不进菜单）验证 UiButton  

**DoD**：build 绿；旧页无样式雪崩；隔离页可见 Ui\*。

**明确不做（W0b）**：dialog / dropdown-menu / input（无调用方）/ sonner。

---

## T4 · 业务薄封装 `components/ds` [P1 · 依赖 W1/W3 真实重复]

- [ ] T4.1 `DsPageHeader`：**对齐**现有 `KnowledgePageHeader` API，避免双页头  
- [ ] T4.2 至少 1 个现有页替换；旧组件注释 deprecated  
- [ ] T4.3 可选 DsEmptyState / DsStatusChip（≥2 处重复才抽）  
- [ ] T4.4 禁止无调用方空组件；auth 文件锁遵守 plan §4.8  

---

## T5 · Wave UI 重构迁移 [P1]

- [ ] T5.1 **W1** `layouts/default.vue`：**允许壳重构**（DOM/样式/token）；菜单数据源与路由表不动  
- [ ] T5.2 **W2** chat 展示壳：`ChatSidebar` / `ChatInputArea` / `ChatWelcome`  
  - **允许**外壳 DOM/样式重构（证据感：hairline/chip/光标）  
  - 发送/停止仍调用同一 store action；**禁止**改 store/SSE/payload  
  - `ChatMessageList`：**仅**样式/token；七分支语义不动  
  - 回归：流式、结果集、markdown 报告、ECharts、embed  
- [ ] T5.3 **W3** 2–3 个 system 列表页 **模板重构**（page-shell/表头/密度）；service 不变  
- [ ] T5.4 冻结区 **零逻辑/协议 diff**（允许同文件纯样式 hunk）  
- [ ] T5.5 若单文件展示+逻辑耦合过重：先抽展示子组件再换皮，避免改 action

---

## T6 · shadcn 试点 [P2 · 依赖 W0b]

- [ ] T6.1 默认试点 **`system/security.vue`**  
- [ ] T6.2 若 auth 已建 login → 另 PR 样式跟进（DS 不抢建 login）  
- [ ] T6.3 试点以 Ui\* 为主；不混重型 Vuetify 浮层  
- [ ] T6.4 按需再 add dialog/input 等（有调用方）  
- [ ] T6.5 约定写入前端 CLAUDE  

---

## T7 · 质量与收口 [P1]

- [ ] T7.1 触达文件收敛裸主色 hex；禁止新增  
- [ ] T7.2 a11y：对比矩阵 + focus + reduced-motion 全覆盖结果附注  
- [ ] T7.3 bundle 对比 T0  
- [ ] T7.4 更新 `data-agent-frontend-nuxt/CLAUDE.md` Design System  
- [ ] T7.5 更新本 task；plan 状态 → 已落地（W0a / +W0b…）  
- [ ] T7.6（可选）双轨退出评估：无岛消费则停 W0b  

---

## 执行纪律

1. **批准前不改业务代码**（本文档修订除外）  
2. 每完成 Ti：`pnpm build`  
3. 最小 diff；不顺手重构 Graph/SSE  
4. 不 commit 除非用户要求  
5. 危险操作禁止  

---

## 冒烟清单

| 路由 | 检查 |
|------|------|
| `/agent/new` | 渲染 |
| `/chat` | 发消息、流式、停止、报告/结果集 |
| `/system/agents` | 列表、编辑 |
| `/system/data-sources` | 列表、对话框 |
| `/system/metrics` | 列表 |
| embed | 壳不崩 |
| 试点/playground | Ui 可见（若已做 W0b） |

---

## 会审待决 → 已锁定

| # | 决议 |
|---|------|
| 1 preflight | **关** |
| 2 前缀 | **Ui** + **Ds** |
| 3 W2 | **展示壳可重构**；SSE/store 语义禁止 |
| 4 W4 | **security**；login 非强制 |
| 5 sonner | **不装** |
| 6 W0 | **W0a 必选 / W0b 可选** |
