# R228 聊天输入区可访问性与交互密度优化方案

> 日期：2026-07-18  
> 状态：待批准  
> 范围：`data-agent-frontend-nuxt` 聊天主路径  
> 策略：`optimize-measure`，单点改动、可验证、功能零变化

## 1. 背景与循环根因

R227 已完成后端相关单测与 API 可用性验证，但没有形成新的 UI 主度量，后续循环重复执行相同检查。因此 R228 不再重复后端验证，改为选择一个明确且可验收的前端瓶颈：

- 聊天输入区的模型/数据源选择器使用可点击 `div`，键盘用户无法稳定操作。
- 下拉选项使用普通 `div`，缺少原生焦点、激活和禁用语义。
- 现有 `focus-visible` token 已存在，但输入区状态控件没有统一使用。
- 选择器、下拉项和底部操作控件的触控尺寸不完全受现有密度 token 约束。

## 2. 目标

在不改变消息发送、SSE 流式、Pinia 状态、路由和后端行为的前提下：

1. 模型和数据源选择器可通过键盘聚焦、展开和选择。
2. 下拉菜单具备明确的 `aria-expanded`、`aria-haspopup` 和选中状态语义。
3. 键盘焦点在浅色背景上清晰可见，并复用 `--da-ring` 等已有设计 token。
4. 移动端操作控件保持稳定尺寸，避免布局跳动和误触。
5. 只保留必要的代码改动，不引入新依赖或新 UI 库。

## 3. 非目标

- 不修改 `stores/chat.ts` 的 SSE 分流、会话状态或发送逻辑。
- 不修改 `services/graph/index.ts`、后端 Graph、Prompt 或数据库。
- 不重做聊天视觉风格，不引入新的设计系统或组件库。
- 不处理当前工作树中与本任务无关的既有改动。
- 不进行 Docker 重建、后端重启或元库写操作。

## 4. 推荐方案

### 4.1 控件语义

在 `ChatInputArea.vue` 中：

- 将模型/数据源触发器从普通 `div` 调整为 `button type="button"`。
- 使用 `:disabled="store.isStreaming"` 表达流式期间不可切换。
- 增加 `aria-haspopup="listbox"` 与 `:aria-expanded`。
- 将下拉菜单标记为 `role="listbox"`。
- 将每个数据源/模型选项调整为按钮或具备完整键盘语义的控件。
- 使用 `aria-selected` 表达当前激活项，并保留现有 `active` 视觉状态。

### 4.2 视觉与密度

- 选择器和选项控件复用 `--da-control-height-sm`、`--da-radius-sm`、`--da-line-soft`、`--da-ring`。
- 为状态选择器、下拉选项和发送区已有按钮补充统一的 `:focus-visible` 焦点环。
- 保持现有颜色、文案、排列和交互路径不变，仅修正语义和焦点表现。
- 在 `prefers-reduced-motion: reduce` 下不增加任何动画。

### 4.3 兼容性处理

- 点击行为继续调用现有 `toggleDsMenu`、`toggleModelMenu`、`selectDs`、`selectModel`。
- 继续保留文档点击关闭菜单的逻辑。
- 不引入键盘 roving tabindex 等复杂抽象；优先使用原生按钮和浏览器默认 Tab 顺序。

## 5. 预计改动文件

| 文件 | 改动 | 说明 |
|---|---|---|
| `data-agent-frontend-nuxt/app/components/chat/ChatInputArea.vue` | 是 | 选择器/选项语义、ARIA、焦点与触控尺寸 |
| `data-agent-frontend-nuxt/app/assets/css/main.css` | 视需要 | 仅当现有全局焦点工具不足时补充通用规则 |
| `data-agent-frontend-nuxt/app/assets/css/tokens.css` | 否 | 复用已有 token，不新增颜色或尺寸 |
| `data-agent-frontend-nuxt/app/stores/chat.ts` | 否 | 行为边界冻结 |
| 后端文件 | 否 | 本轮不涉及 |

## 6. 实施步骤

1. 修改 `ChatInputArea.vue` 的模型/数据源选择器和菜单项语义。
2. 为新增语义属性补齐 Vue/TypeScript 可接受的绑定写法。
3. 将局部硬编码尺寸替换为已有 `--da-*` token；不改变视觉主题。
4. 对比 `git diff`，确认没有触碰 SSE、Pinia、路由和后端代码。
5. 运行前端构建与静态检查。
6. 使用浏览器在桌面和移动视口验证：
   - Tab 可以到达两个选择器和菜单项。
   - Enter/Space 可以展开和选择。
   - 流式期间选择器不可操作。
   - 焦点环可见且不造成布局跳动。
   - 原有发送、停止、缺模型/缺数据源引导仍可用。

## 7. 验收标准

### 功能不变

- 发送问题、停止生成、切换模型、切换数据源行为与改动前一致。
- `pnpm build` 成功。
- `git diff` 不包含后端、SSE、数据库或运行配置变更。

### UX/a11y

- 选择器拥有可读的按钮名称和展开状态。
- 菜单项拥有可读名称、选中状态和键盘可达性。
- 所有新增/调整控件存在 `focus-visible` 可见焦点反馈。
- 移动视口宽度 390px 下无横向溢出、菜单不撑破输入区。
- `prefers-reduced-motion` 下无新增运动效果。

### 主度量

| 指标 | 当前 | R228 目标 |
|---|---:|---:|
| 键盘可达的模型/数据源入口 | 0 | 2 |
| 选择器展开状态 ARIA | 无 | 2/2 |
| 可见焦点环覆盖 | 不统一 | 选择器与菜单项 100% |
| 后端/消息行为回归 | 未改变 | 0 项行为变更 |

## 8. 风险与回滚

| 风险 | 缓解 |
|---|---|
| `button` 默认样式影响局部布局 | 使用现有 CSS 重置 `border/background/font`，构建后截图检查 |
| disabled 后点击事件行为变化 | 只在已有 `store.isStreaming` 阻断条件下设置 disabled |
| 菜单项键盘行为与点击行为不一致 | 复用原有选择函数，使用原生按钮提交事件 |
| 触碰用户未提交改动 | 只编辑本计划列出的文件，执行前后检查 diff |

若构建或关键交互回归，回滚本轮单文件改动即可；不执行任何破坏性 Git 或数据库操作。

## 9. 验证命令

```bash
cd "data-agent-frontend-nuxt"
pnpm build
```

必要时运行：

```bash
pnpm eslint app/components/chat/ChatInputArea.vue
```

## 10. 批准门

本文件仅记录方案和实施计划，当前不修改产品代码。实施前需要确认以下范围：

- 只改聊天输入区可访问性和交互密度。
- 不改后端智能体质量/速度逻辑。
- 不引入依赖、不重启服务、不写数据库。

确认后进入执行阶段。
