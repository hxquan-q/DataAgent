---
name: hybrid-hotdeploy-db-safe
description: 数据平面稳态 + 开发平面热部署红线。任何涉及 Docker/IDEA 启动、重启、compose、数据库、dev-hybrid 的操作必须遵守。
globs:
  - docker-file/**
  - scripts/dev-hybrid.sh
  - scripts/backup-meta-db.sh
  - data-agent-management/src/main/resources/application*.yml
  - .run/**
alwaysApply: true
---

# 混合热部署 · 元库零影响（强制规则）

> 工程控制论：η₅ 先稳后优。数据平面稳态优先于开发便利。  
> 详设：`docs/dev/HYBRID_HOTDEPLOY.md`

## 1. 双平面（分类定方法）

| 平面 | 组件 | 可频繁操作 | 禁止 |
|------|------|-----------|------|
| **数据平面** | 元库 `nl2sql_db`（mysql-inner + external volume）；模拟 MySQL `mysql-data`（product_db）；远程 `asd_standard` 只读 | `start`/`stop`；`backup-meta-db.sh`；重灌模拟 SQL | 见 §2 |
| **开发平面** | IDEA+JRebel `:8065`；Nuxt `:3000`；proxy `:3301` | Restart、JRebel reload、HMR、重建 proxy | 为「清环境」动元库 |

## 2. 绝对禁止（P1 · 违反即数据事故）

1. **`docker compose down -v`**（会删卷，配置全丢）
2. **无 volume 启动 / force-recreate `data-agent-mysql-inner`**
3. **`docker volume rm data-agent-mysql-inner-data`**
4. 将 Docker backend 的 **`DATA_AGENT_DATASOURCE_SQL_INIT` 改回 `always`**（空库一次性初始化除外，且须先备份）
5. 在未备份的情况下对元库执行 DROP / TRUNCATE / 批量 DELETE
6. 默认 hybrid 流程里 **`UPDATE datasource` 写 host**（仅允许 `WRITE_HOSTS=1` 显式开启）

## 3. 强制正确配置

| 项 | 值 | 位置 |
|----|-----|------|
| IDEA Active profiles | **`local`** | `.run/DataAgent-local.run.xml` |
| 本地 SQL 初始化 | **`spring.sql.init.mode: never`** | `application-local.yml` |
| Docker backend SQL 初始化 | **`DATA_AGENT_DATASOURCE_SQL_INIT=never`** | `docker-file/docker-compose.yml` |
| 元库 volume | **`data-agent-mysql-inner-data` external** | compose `volumes` |
| 后端热部署 | **IDEA + JRebel**，`rebel.xml` → `target/classes` | 开发平面 |

## 4. 标准操作（Agent / 人）

```bash
# 进入 hybrid（停 Docker 前后端；默认不写元库）
bash scripts/dev-hybrid.sh up
bash scripts/dev-hybrid.sh status

# 前端
bash scripts/dev-hybrid.sh frontend

# 风险操作前备份
bash scripts/backup-meta-db.sh

# 仅当全栈 Docker 需要内网 datasource host 时才写库
WRITE_HOSTS=1 bash scripts/dev-hybrid.sh down
```

- **IDEA**：Run `DataAgent-local`，JDK 17，端口 8065，启用 JRebel。
- **禁止**同时跑 Docker `data-agent-backend` 与 IDEA（抢 8065）。
- 改 Graph 装配 / 核心 yml：允许 Restart；**Restart 不得改变 sql.init**。

## 5. Agent 行为约束

当用户或任务涉及「启动 / 重启 / 部署 / 清环境 / compose / 数据库」时：

1. **先判平面**：操作是否触及 `mysql-inner` 或元库数据？触及则先 `backup-meta-db.sh` 或拒绝危险命令。
2. **默认不写元库**：不执行会改 `agent`/`datasource`/`metric` 等表的 SQL，除非用户明确要求恢复（如 `restore-inventory-agent.sh`）。
3. **不改 SQL_INIT 为 always**，除非用户明确说「空库初始化」且已备份。
4. **不建议** `docker compose down` 带 `-v`；检测到用户意图时必须警告。
5. 文档与脚本变更须与本规则一致：`dev-hybrid` 默认 `WRITE_HOSTS=0`。

## 6. 事故模式（已知）

- volume 被注释 → 数据进容器层 → 换新卷 = 空库（2026-07 已发生）。
- `SQL_INIT=always` + 未来有人加 DROP/硬编码 UPDATE = 静默损坏。
- hybrid 默认改 host → 元库被开发脚本反复写。

对策：本规则 + compose never + hybrid 默认不写库 + 备份脚本。

## 7. 现行业务绑定（勿再灌无关种子 Agent）

| 对象 | 说明 |
|------|------|
| Agent 6 | 库存台账 · published · 绑 asd_standard |
| DS 1 | 模拟 product_db @ 127.0.0.1:3307 |
| DS 4 | ASD 台账 @ 远程（docs/.db.env） |
| 恢复 | `scripts/restore-inventory-agent.sh` + 见 `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md` |

## 8. 相关路径

- `CLAUDE.md`（数据平面总表）
- `docs/dev/HYBRID_HOTDEPLOY.md` · `docs/dev/LOCAL_RUN_CHECKLIST.md` · `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md`
- `scripts/dev-hybrid.sh` · `scripts/backup-meta-db.sh` · `scripts/restore-inventory-agent.sh`
- `docker-file/docker-compose.yml` · `application-local.yml`
