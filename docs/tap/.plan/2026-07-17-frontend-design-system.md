# 方案：前端 UI/UX 设计体系重建（Design System Rebuild）

> 日期：2026-07-17  
> 状态：**W0a 已落地**（2026-07-18 · `pnpm build` 绿）；W0b/W1–W4 待另批  
> 会审：UI/UX 有条件通过 · 前端工程 有条件通过 · 架构 有条件通过  
> 增量：用户明确 **支持重构**（见 §0.1 / §3.3）  
> 目标：在**不影响正常使用**的前提下，重建统一、可扩展的前端设计体系；可吸收 [shadcn](https://ui.shadcn.com/) 方法论（Vue 栈用 [shadcn-vue](https://www.shadcn-vue.com/docs/installation/nuxt)）

---

## 0. 会审结论摘要（已锁定）

| 项 | 锁定 |
|----|------|
| 战略 | **选项 C 修订版**：Token-first + Vuetify 保留 + shadcn-vue **可选**渐进岛 |
| 重构立场 | **支持 UI 层重构**（布局/页壳/展示组件拆合/token 化）；**禁止**业务/SSE/协议层重构混进 DS PR |
| W0 拆分 | **W0a 必选**（tokens + Vuetify bridge）· **W0b 可选闸门**（Tailwind + shadcn） |
| 「体系已建立」最低定义 | **W0a + 冒烟绿**（未装 shadcn 也算体系落地） |
| Tailwind preflight | **默认关**（theme + utilities only） |
| 首批 Ui 组件 | **button / card / badge / separator**；dialog·dropdown·input·sonner **按调用方再 add** |
| 前缀 | **Ui** = primitive · **Ds** = 业务封装 · 禁止无前缀 Button |
| W2 chat | **允许展示层重构**；**禁止**改 store/SSE/七分支语义；输入发送契约不变 |
| W4 试点 | 默认 **`system/security.vue`**；login **不由 DS 抢建**（auth 主导） |
| Toast | **tips store only**；不装 Sonner |
| 冻结（逻辑） | chat SSE / MessageList 消息语义 / typewriter 算法 / echarts 生命周期 / markdown 插件协议 / embed 协议 |

### 0.1 重构边界（「支持重构」的可执行定义）

| 层 | 是否允许重构 | 示例 |
|----|--------------|------|
| **A. 设计体系 / 视觉壳** | **允许且鼓励** | tokens、theme bridge、`default.vue` 壳、page-shell、页头抽取 `DsPageHeader`、重复 class→token、侧栏样式结构 |
| **B. 展示组件结构** | **允许**（须冒烟） | 拆合纯展示子组件、slot 化 actions、统一空态/状态 chip；**不改**对外 props 语义时优先兼容 |
| **C. 页面模板换皮** | **允许**（按 Wave） | system 列表页布局重排、密度/间距；CRUD 仍走原 service |
| **D. Chat 展示壳** | **有限允许** | Sidebar/Welcome/Input **外壳** DOM 可调；发送/停止仍调同一 store action；**不改** `nodeBlocks`/`messageType` 分支含义 |
| **E. 业务与实时链路** | **禁止**（本方案） | `stores/chat.ts` 分流、`graph` SSE、EventSource URL、embed postMessage/CSP、API DTO |
| **F. 依赖大爆炸** | **禁止** | 全量删 Vuetify、上第三 UI 库、为美观重写 MessageList 七分支 |

**原则**：重构 = 让 UI 更一致、更可维护；**用户可感知路径不断、数据契约不变**。每波可回滚。

---

## 1. 问题与目标

### 1.1 现状（证据）

| 项 | 事实 |
|----|------|
| 技术栈 | **Nuxt 4.3 SPA**（`ssr:false`）+ **Vue 3.5** + **Vuetify 3.11** + Pinia + ECharts + markdown-it |
| 组件面 | 约 **40** 个 `.vue`；管理 CRUD 页重度 `v-*` |
| 既有 token | `main.css` 半套 `--da-*`；**未与 Vuetify theme 对齐**；散落 hex 多 |
| 主题 | `nuxt.config` 仅 `VBtn.outlined`，无完整 theme |
| 部署 | hybrid `:3000` / Docker `:3301` generate；**不改后端、不动元库** |
| 并行 | admin-auth 方案并行（见 §4.8 文件锁） |

### 1.2 真实目标

1. **统一设计语言**：色/字阶/间距/圆角/阴影/动效单一 Source of Truth  
2. **不影响现用**：chat SSE、报告/ECharts、CRUD、embed 全程可用  
3. **可扩展**：新页可按 DS 组件拼装  
4. **吸收 shadcn 方法论**（copy-in、CSS 变量、拥有源码）——**不强制 W0 装全套**  
5. **支持 UI 层重构**（§0.1）：壳与展示结构可演进，业务/实时链路冻结  
6. **可验证、可回滚**

### 1.3 非目标

- 全量拆除 Vuetify / 无波次一次性重写 40 页  
- 引入 React / 第三套 UI 库（Nuxt UI、Element 等）  
- 改业务逻辑、API、SSE、Graph **语义与协议**（展示壳可重构）  
- 全面 Dark Mode 产品化（仅预留 `.dark` token）  
- 改 Docker 数据面 / SQL_INIT / 后端配置  
- 引入第二套 Toast / 新数据表库  
- 借 DS 名义做无关功能开发

---

## 2. 关键约束（η）

| η | 落实 |
|---|------|
| η₁ 分类 | **仅开发面前端** |
| η₂ 最少自由度 | 先 tokens + bridge；shadcn 非假想刚需不绑死 |
| η₅ 先稳后优 | W0a 可独立合入；每 Wave 可停 |
| η₇ 有度量 | build + 冒烟 + 体积阈值 + a11y 抽检矩阵 |

---

## 3. 方案比较与推荐

| 选项 | 结论 |
|------|------|
| A 全量 → shadcn | **否**（破坏现用） |
| B 仅堆 CSS | **不够** |
| **C 修订：Token + Vuetify + 可选岛** | **推荐** |
| D 换 Nuxt UI | **否** |

### 3.1 架构形态（会审后）

```
W0a（必选，可独立交付价值）
  tokens.css  ──►  main.css 别名  ──►  Vuetify theme bridge
  「体系已建立」= 此项 + 冒烟绿

W0b（可选闸门，需再次确认）
  Tailwind theme+utilities（无 preflight）
  shadcn-nuxt + Ui 最小集
  若 2 试点周期无真实消费 → 可删停 W0b，仅保留 tokens+Vuetify

W1–W3  现页换皮（仍 Vuetify + tokens）
W4     明确试点页才 Ui* 为主
```

### 3.2 设计方向（成立）

| 维 | 决策 |
|----|------|
| 风格 | Soft UI Evolution：轻阴影、清晰对比、非玩具 |
| 密度 | Dashboard 偏密；用 **density token**（control-height / row） |
| 动效 | 150–300ms；废弃 0.45s 作默认交互；降 spring 使用面 |
| 主色 | Primary `#1E40AF` · Accent `#3B82F6` · 琥珀仅状态/图 |
| 表面 | bg `#F8FAFC` · surface `#FFFFFF` · ink `#0F172A` · muted 实测加深若 caption 不达标 |
| 字体 | 本版不强制 CDN；token 预留 |
| 图标 | 存量 MDI；岛内 Lucide；禁 emoji 图标 |
| 签名 | **证据感**：hairline、`.table-tag`、流式光标 |

---

## 4. 技术设计

### 4.1 Token 分层（T1 完整清单）

```
app/assets/css/
  tokens.css     # 唯一色/字阶/间距/圆角/阴影/z/动效（:root + .dark 预留）
  main.css       # 工具类、chat 动效、page-shell（@import tokens）
  tailwind.css   # 仅 W0b：theme + utilities，**无 preflight**
```

**必须包含的 token 组**

| 组 | 示例 |
|----|------|
| 色 | ink, muted, line, line-soft, surface, surface-soft, primary, primary-soft, accent, danger, success, warning, info |
| on-\* | on-primary, on-danger（按钮字色对比） |
| 侧栏暗区 | sidebar-bg, sidebar-ink, sidebar-muted, sidebar-line |
| Focus | ring, ring-offset |
| 密度 | space-\*, control-height-sm/md, table-row-density |
| 半径/阴影 | radius-sm/md/lg, shadow-sm/md |
| 动效 | dur-fast 150ms, dur-base 200–250ms, dur-slow 300ms, ease-out |
| 层叠 | z-dropdown, z-dialog, z-toast, z-fullscreen-report |
| Overlay | overlay |
| Disabled | disabled-opacity 或 surface/ink 变体 |

图表色板：单文件常量（可引用 token），**禁止**业务组件内新增主色 hex。

**Vuetify bridge 同步**：`nuxt.config` theme 使用**与 tokens 相同的静态 hex**（文件头注释：改色先 tokens 再 bridge）。不依赖运行时读 CSS 变量。

### 4.2 W0a · Vuetify Theme Bridge

- `theme.themes.light.colors` 映射 primary/surface/error/success/warning  
- 侧栏深色用 sidebar-\* token，消灭 `#1e293b` 散落  
- **不改**业务组件 API  

### 4.3 W0b · Tailwind + shadcn-vue（可选）

按 [shadcn-vue Nuxt](https://www.shadcn-vue.com/docs/installation/nuxt)：

1. `tailwindcss` + `@tailwindcss/vite`  
2. `shadcn-nuxt`，`prefix: 'Ui'`，`componentDir: '@/components/ui'`  
3. `tailwind.css`：

```css
@layer theme, base, components, utilities;
@import "tailwindcss/theme.css" layer(theme);
/* 不引入 preflight — 保护 Vuetify */
@import "tailwindcss/utilities.css" layer(utilities);
```

4. CSS 顺序：`tokens → main → tailwind`  
5. 最小组件：`button` `card` `badge` `separator`  
6. 岛根 class：`.ds-island`（utility 尽量落在岛内）  
7. **必须提交 `pnpm-lock.yaml`**（Docker `frozen-lockfile` + `generate`）  
8. `nuxt.config` **手工合并**；PR 硬审 `routeRules` proxy / `ssr` / vuetify module  

**utility 碰撞**：`border`/`rounded-lg` 等与现有 class 同名 → W0b 门禁 A/B 截图；失败则缩 scope，**不**开 preflight 硬修全站。

### 4.4 迁移波次

| Wave | 范围 | 风险 |
|------|------|------|
| **W0a** | tokens + bridge + 文档 | 极低 |
| **W0b** | Tailwind 无 preflight + Ui 最小集 | 中（独立闸门） |
| **W1** | `layouts/default.vue` **壳重构**（token + 可调 DOM/样式结构）；**禁止改菜单数据源/路由表/导航业务逻辑** | 低 |
| **W2** | chat **展示壳重构**（Sidebar/Welcome/InputArea 外壳）；store action 与 SSE **契约不变**；MessageList **仅样式/token**，七分支语义不动 | 中（高频） |
| **W3** | system 列表页 **模板重构**（壳/密度/页头）；service CRUD 不变；收敛 `KnowledgePageHeader`→`DsPageHeader` | 中 |
| **W4** | 试点页可 **Ui 岛重构**（security；或 auth 已有 login 的样式 PR） | 中低 |

**冻结清单（逻辑/协议 · 任意 Wave）**  
`stores/chat.ts` 分流与持久化语义 · `services/graph/**` · `ChatMessageList` 的 `messageType` 语义 · `useTypewriter` 算法 · `useEchartsRenderer` 生命周期 · markdown 插件协议 · embed postMessage/CSP · EventSource 契约  

**允许重构清单（UI）**  
`layouts/default.vue` 壳 · `components/ds/*` · 各 system 页模板壳 · chat 三壳组件 DOM（契约不变） · token/theme/CSS

### 4.5 目录契约

```
app/assets/css/tokens.css
app/assets/css/main.css
app/assets/css/tailwind.css          # 仅 W0b
app/components/ui/                   # Ui* shadcn 源码
app/components/ds/                   # Ds* 业务薄封装（≥2 页重复才抽）
app/lib/utils.ts                     # cn()，W0b
components.json                      # W0b
```

### 4.6 一页一主体系

- 同一 `.vue`：**禁止** `v-dialog` + `UiDialog` 混用  
- 存量 CRUD：**继续 Vuetify**  
- 新岛/试点：**Ui* + Ds***  
- 颜色间距：**只认 tokens**  

### 4.7 架构硬约束

1. 严格前端-only；禁 `down -v` / SQL_INIT / 后端 yml  
2. 禁擅改 `routeRules` proxy  
3. 前端镜像仅 rebuild `frontend` 服务  
4. 每 Wave 独立可合并；建议单 PR ≤12 文件或 ≤800 行非生成代码（`components/ui` 可另计）  
5. 体积：相对 T0 基线 W0b **+15% 解释 / +25% 砍组件**  
6. 双轨退出：2 试点周期无消费 → 停 W0b  
7. 默认不 git commit/push，除非用户指令  

### 4.8 与 admin-auth 并行（文件锁）

| 面 | 所有者 |
|----|--------|
| `nuxt.config.ts` | **串行**；W0b 时装 module 时 auth 暂停同文件 |
| `layouts/default.vue` | DS 只视觉；auth 后续用户菜单另 PR |
| `/login` | **auth 主导**；DS 禁止先建空壳 |
| `system/security.vue` | **DS 试点** |
| axios 401 | auth only |
| package/lock | 合并窗口协调 |

---

## 5. 风险与缓解

| 风险 | 级 | 缓解 |
|------|----|------|
| Tailwind preflight / utility 撞 Vuetify | **高** | 默认关 preflight；A/B 截图；岛内 scope |
| W0 过载绑 shadcn | **高** | W0a/W0b 拆分 |
| 双库心智 / 第三套滑落 | 中高 | 文档 + 一页一体系 + 禁第三库 |
| auth 共享文件冲突 | 中 | §4.8 文件锁 |
| W2 scope creep | 中 | CSS-only 硬锁 |
| lockfile / Docker generate 挂 | 中 | 必交 lock；DoD 含 generate 路径说明 |
| z-index 双 overlay | 中 | 单视图单 overlay 栈 |
| 包体积 | 中 | 阈值门禁；禁 `import *` lucide |

---

## 6. 验证标准

### 6.1 硬门禁

- [ ] `pnpm build` 绿；交付路径知悉 `pnpm generate` + Docker rebuild frontend  
- [ ] 冒烟：`/agent/new` `/chat` `/system/agents` `/system/data-sources` `/system/metrics` + **embed 单列**  
- [ ] chat：发问 → 流式 → 报告/结果集  
- [ ] `nuxt.config` proxy/ssr/modules 未误伤  
- [ ] 无冻结清单的非样式 diff  

### 6.2 设计 / a11y 硬门禁

- [ ] 色值来自 tokens；禁止新增业务主色 hex  
- [ ] 对比矩阵抽检：ink@surface、muted@surface（含 caption）、on-primary、sidebar ink@bg、table-tag、warning/error 横幅  
- [ ] `:focus-visible` 用 `--da-ring`；禁止无替代 `outline:none`  
- [ ] **全部**动效进 `prefers-reduced-motion`（含 `breathe`、page transition、stream cursor 可静态）  
- [ ] 主操作热区 chat 发送/停止 ≥ 40–44px  
- [ ] 图标非 emoji  

### 6.3 文档门禁

- [ ] plan/task 状态更新  
- [ ] `data-agent-frontend-nuxt/CLAUDE.md` Design System 小节  

---

## 7. 节奏

| Phase | 内容 | 估 |
|-------|------|----|
| P0 | 用户批准本修订稿 | — |
| P1 | **W0a** tokens + bridge | 0.5–1d |
| P1b | **W0b**（仅当用户确认需要岛） | 1–1.5d |
| P2 | W1 +（可选）W2 CSS | 1–2d |
| P3 | W3 + W4 试点 | 1–2d |
| P4 | 收口 a11y/体积/文档 | 0.5d |

**可停点**：W0a 合入即有交付价值；可不做 W0b/W2/W4。

---

## 8. 决策锁定（请用户确认）

1. 采用 **选项 C 修订版 v1.1**（Token-first；shadcn **非** W0 必选）  
2. **支持 UI 层重构**（§0.1 A–D）；**禁止** E/F（业务链路与依赖大爆炸）  
3. **不**无波次全量拆除 Vuetify  
4. shadcn = **shadcn-vue**，非 React 直装  
5. preflight **关**；Ui 最小集；试点 **security**  
6. 执行顺序：批准 → **先 W0a** → 再议 W0b → W1–W4 重构波次  
7. 默认不 commit/push  

---

## 9. 参考

- 现栈：`package.json`、`nuxt.config.ts`、`app/assets/css/main.css`  
- shadcn-vue Nuxt：https://www.shadcn-vue.com/docs/installation/nuxt  
- 并行：`docs/tap/.plan/2026-07-17-admin-auth.md`  
- 任务：`docs/tap/.task/2026-07-17-frontend-design-system-tasks.md`  

---

## 10. 会审焦点 · 已决议

| # | 问题 | 决议 |
|---|------|------|
| 1 | preflight | **默认关**；theme+utilities only |
| 2 | 首批组件 | **button/card/badge/separator**；其余按需 |
| 3 | W2 结构 | **展示壳可重构**；store/SSE/七分支语义 **禁止** |
| 4 | 前缀 | **Ui** + **Ds** |
| 5 | login 试点 | **否**；默认 security；auth 主导 login |
