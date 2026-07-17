# DataAgent 本地可运行 Checklist（IDEA + hybrid）

> 目标：从「能打开页面」到「能完成一次数据问答」。  
> 推荐：`bash scripts/dev-hybrid.sh up` + IDEA `profiles=local` + Nuxt `:3000`。  
> 红线：`docs/dev/HYBRID_HOTDEPLOY.md` · `.rule/hybrid-hotdeploy-db-safe.md` · 总览：`CLAUDE.md`

## 0. 基础设施

- [ ] `bash scripts/dev-hybrid.sh up`（停 Docker backend/frontend，起 portfwd + `:3301` 代理）
- [ ] `bash scripts/dev-hybrid.sh status`：元库 healthy；`:8065` 留给 IDEA
- [ ] 元库：`127.0.0.1:3306` / `nl2sql_db` / root/root
- [ ] `spring.sql.init.mode=never`（`application-local.yml`）——空库才手工导入 schema/data
- [ ] **不要** `docker compose down -v`
- [ ] 风险操作前：`bash scripts/backup-meta-db.sh`

## 1. 后端（IDEA / JRebel）

- [ ] Run：`DataAgent-local` · Active profile = **`local`** · JDK **17** · 端口 **8065**
- [ ] JRebel：`rebel.xml` → `target/classes`
- [ ] `curl -s http://127.0.0.1:8065/api/agent/list` → 200
- [ ] 改 `application-local.yml` 后 **Restart**（YAML 一般不热更）
- [ ] 本地推荐项（`application-local.yml`）：
  - `max-sql-retry-count: 3`
  - `enable-sql-result-chart: false`
  - `enable-concurrent-steps: true`（无依赖 SQL 波次并发；取舍见 `ConcurrentSqlStepExecutor`）

## 2. 前端

- [ ] `bash scripts/dev-hybrid.sh frontend` 或 `pnpm dev --host 0.0.0.0 --port 3000`
- [ ] 公网：`http://<host>:3301`（proxy → 3000/8065）
- [ ] 侧栏可进：数据问答 / 模型 / 数据源 / 智能体

## 3. 模型（硬依赖）

1. `/system/model-config` 添加并**激活** CHAT  
2. `GET /api/model-config/check-ready` → chat 就绪  
3. 数据问答 chip 显示模型名  

## 4. 数据源（现行范围）

| 用途 | 配置 | hybrid 地址 |
|------|------|-------------|
| 模拟业务 | DS「模拟业务库-product_db」 | `127.0.0.1:3307` / product_db |
| 库存台账 | DS「ASD标准库-库存台账」 | `docs/.db.env` 远程 asd_standard |

- [ ] `POST /api/datasource/1/test` 与 `/4/test` 成功  
- [ ] 模拟库若空：重灌 `docker-file/config/mysql/product_db.sql`  
- [ ] 智能体 **#6 库存台账**：绑定 DS4 + 表初始化  
- [ ] 丢台账配置：`bash scripts/restore-inventory-agent.sh`  

详见 `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md`。

## 5. 一次冒烟

1. 智能体列表 → **库存台账智能体**  
2. 确认模型 + 数据源 chip  
3. 预设：「库存台账一共有多少条流水？」→ 期望约 **100**  
4. （可选）「按仓库汇总出入库操作数量」  

## 6. 常见故障

| 现象 | 处理 |
|------|------|
| agent list 空 / 表不存在 | 导入 schema；或 restore-inventory + backup restore |
| 8065 起不来 | `dev-hybrid.sh up` 停 Docker backend；勿双开 |
| 模型 chip「未配置」 | 激活 CHAT |
| 模拟库连失败 / 无表 | 重灌 product_db.sql；确认 3307 portfwd |
| 台账连失败 | 查 `docs/.db.env` 与网络；远端只读 |
| INSERT denied root@x.x.x.x | GRANT root@`%`（见台账文档 §11） |
| 改 yml 不生效 | IDEA Restart |
| embed 403 | 配置 allowedOrigins（禁用 `*`） |

## 7. 相关文档

| 路径 | 用途 |
|------|------|
| `CLAUDE.md` | 项目总上下文 + 数据平面 |
| `docs/dev/HYBRID_HOTDEPLOY.md` | 热部署手册 |
| `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md` | 台账智能体 |
| `.rule/hybrid-hotdeploy-db-safe.md` | 强制红线 |

## 8. 相关迭代（历史）

- R149–R177：模型/数据源引导、并发、发送守卫等  
- R196–R201：体量日志 / 选表警告 / 初始化禁用 / 流式秒表  
- 2026-07-17：hybrid 零写库 + SQL_INIT=never + 台账恢复文档对齐  
