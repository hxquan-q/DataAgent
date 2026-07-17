# 可运行性阶段总结（R149–R163+）

## 目标
让用户在**不读代码**的情况下，能从「打开管理后台」走到「发送一条数据问答」。

## 已打通的产品路径

```
智能体列表
  ├─ 新建 → 数据源页(?agentId) → 设为当前 → 初始化 → 自动进聊天
  ├─ 数据问答 → /chat?agentId=
  ├─ 配置数据源 → /system/data-sources?agentId=
  └─ 模型配置 → /system/model-config

聊天页
  ├─ 无 agentId → 选择智能体 / 模型 / 数据源
  ├─ Welcome 就绪清单（模型 + 数据源）
  ├─ 输入区守卫（无模型/无 DS 阻断）
  ├─ 状态条「切换智能体」
  └─ 侧栏：模型 · 数据源 · 智能体
```

## 本地性能 opt-in（application-local.yml）

- `max-sql-retry-count: 3`
- `enable-sql-result-chart: false`
- `enrich-sql-result-timeout: 2000`
- `enable-concurrent-steps: true`（需 **IDEA Restart** 生效）

详见 [LOCAL_RUN_CHECKLIST.md](./LOCAL_RUN_CHECKLIST.md)。

## 仍需人工完成

1. 配置并激活 **CHAT** 模型（API Key）— 当前库 `model_config` 可能为 0  
2. 重启后端以加载 concurrent-steps  
3. 按智能体绑定数据源并初始化表结构  

## 代码质量/速度杠杆（更早轮次）

报告 prompt 瘦身、PromptHelper 有界、schema 列/样本裁剪、全局 optimization 合并等见各 `REPORT-R*.md` / V1–V5。
