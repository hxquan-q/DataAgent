# R229 · DEEIX 高冲击视觉重构

> 日期：2026-07-18 · 分支 `rebuild-ui` · 功能零变化

## 根因（打破 r227 微抛光循环）

S0–S5 + R100–R228 已完成 token/a11y/密度微调，但**视觉签名仍偏「机器人欢迎卡 + 多色 chip 条 + 文字发送钮」**，与 DEEIX-Chat 的：

- 大号 economist 问候标题（无头像）
- 一体 `rounded-3xl` pure 浮动 composer
- 圆形/方圆图标发送
- 消息区 open canvas（无两侧 avatar）

差距仍大。用户感知「效果很差」= 缺签名差异，不是缺 a11y 边角。

## 方案（只借鉴理念，不抄框架）

| DEEIX | DataAgent 落地 |
|---|---|
| `ChatEmptyState` 22–32px display 标题 | `ChatWelcome` 时段问候 + 静默 agent badge |
| `InputGroup` rounded-3xl / border 0.5 / pure bg | `ChatInputArea` dock + soft composer shadow |
| 圆形/size-8 发送 | native `button.send-btn` 32×32 上箭头 |
| 消息无左右 avatar | `ChatMessageList` 去掉全部 `v-avatar` |
| answer 居中宽 | 保持 `--da-answer-max: 880px` |

## 改动文件

- `app/components/chat/ChatWelcome.vue` — 大标题空态
- `app/components/chat/ChatMessageList.vue` — 去头像、气泡/间距
- `app/components/chat/ChatInputArea.vue` — 圆形发送、ghost option chips、dock 阴影
- `app/pages/chat.vue` — 底部 fade 加长
- `app/assets/css/tokens.css` — composer radius/shadow

**冻结**：`stores/chat.ts` · `services/graph` · 后端 · embed 协议

## 验证

```bash
cd data-agent-frontend-nuxt && pnpm build
# 手测 /chat 空态 · 有消息 · 发送/停止
```

## 刻意跳过

- 不引入 Tailwind/shadcn/motion
- 不改 SSE 分流
- 不重写管理页（本轮只推聊天主表面签名）
