# DataAgent 本地可运行 Checklist（IDEA + Docker MySQL）

> 目标：从「能打开页面」到「能完成一次数据问答」。  
> 适用：`--spring.profiles.active=local`，MySQL 映射 `127.0.0.1:3306`，库 `nl2sql_db`。

## 0. 基础设施

- [ ] MySQL 容器/进程可用（compose 中 `data-agent-mysql-inner`）
- [ ] 宿主可连：`mysql -h127.0.0.1 -P3306 -uroot -proot nl2sql_db`
- [ ] `spring.sql.init.mode=never`：空库需手动导入 `data-agent-management/src/main/resources/sql/schema.sql` + `data.sql`
- [ ] **不要**对 MySQL 执行 `docker compose down -v`（会丢元库）

## 1. 后端（IDEA / JRebel）

- [ ] Active profile = `local`
- [ ] 端口 `8065`：`curl -s http://127.0.0.1:8065/api/agent/list`
- [ ] 修改 `application-local.yml` 后 **Restart**（YAML 一般不热更）
- [ ] 本地已推荐配置（`application-local.yml`）：
  - `max-sql-retry-count: 3`
  - `enable-sql-result-chart: false`
  - `enrich-sql-result-timeout: 2000`
  - `enable-concurrent-steps: true`（无依赖 SQL 波次并发；跳过逐步语义校验换墙钟）

### 并发 SQL 说明

启用后，PlanExecutor 在「连续无依赖 SQL 步骤」波次上走 `ConcurrentSqlStepExecutor`。  
**取舍**：并发路径不做逐步语义一致性与逐步图表（见类注释）。默认生产仍为 `false`。

## 2. 前端

- [ ] `data-agent-frontend-nuxt`：`pnpm dev`（常见 3000 / 代理到 8065）
- [ ] 侧栏可进：数据问答 / 模型服务 / 数据源配置 / 智能体

## 3. 模型（硬依赖）

聊天页无 CHAT 模型时：

1. 点模型 chip →「去配置模型」→ `/system/model-config`
2. **添加对话模型**（Base URL、API Key、模型名；maxTokens 默认 1536，可按需调大）
3. **激活**至少一个 CHAT
4. 回到数据问答，确认 chip 显示模型名

API：`GET /api/model-config/check-ready` 应体现 `chatModelReady=true`。

当前库若 `model_config` 行数为 0，**无法**做真实 LLM E2E。

## 4. 数据源（硬依赖）

路径：

1. 智能体列表 →「配置数据源」→ `/system/data-sources?agentId=N`
2. 若全局无连接：先「添加数据源」并测试连接
3. 操作列「设为当前」→ 右上角「初始化当前智能体数据源」
4. 聊天页数据源 chip 应显示库名

聊天页守卫：

- 无模型 → 打开模型菜单并阻断发送  
- 无数据源 → 打开数据源菜单并阻断发送  

## 5. 一次冒烟（有模型后）

1. 智能体列表 → 数据问答  
2. 确认模型 + 数据源 chip 均非空  
3. 发送简单问题（如「有哪些表」）  
4. 观察过程时间线与结果表/报告  
5. （可选）对比 concurrent 开启前后墙钟  

## 6. 常见故障

| 现象 | 处理 |
|------|------|
| agent list 空 / 表不存在 | 导入 schema + data.sql |
| 模型 chip「未配置模型」 | 配置并激活 CHAT |
| 数据源「未绑定」 | data-sources?agentId= 设为当前 + 初始化 |
| 改 yml 不生效 | IDEA Restart（非仅 JRebel reload） |
| 报告仍五段注水 | 确认后端加载了新 `report-generator-plain.txt` / 全局 optimization |

## 7. 相关迭代

- R149 模型引导 + concurrent  
- R150 模型配置空态  
- R151–R153 数据源引导与路由  
- R154/R156 智能体列表：问答 / 数据源 / 模型  
- R155 本文档  
- R157–R160 无 agent 空态 / 切换 / Welcome 清单 / 侧栏链  
- R161 新建智能体 → 绑定数据源  
- R162–R163 激活/初始化后进问答  
- R164 默认 published + 阶段图  
- R165–R166 状态条就绪胶囊（可点）  
- R169–R170 发送禁用 + 阻塞原因  
- R171–R176 maxTokens/并发日志/无模型错误  
- R177 流错误中文化  
- R196–R201 体量日志 / 选表警告 / 初始化禁用 / 流式秒表  

