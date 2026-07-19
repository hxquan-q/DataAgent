# 混合热部署 + 元库零影响

> 目标：IDEA + JRebel 随时热更；**任何日常重启都不改写元库数据**。  
> **强制规则（Agent/人）**：`.rule/hybrid-hotdeploy-db-safe.md`（Claude 摘要：`.claude/rules/hybrid-hotdeploy-db-safe.md`）  
> 项目总览：`CLAUDE.md`「数据平面」「混合开发」

## 双平面

| 平面 | 组件 | 允许 | 禁止 |
|------|------|------|------|
| **数据平面** | `data-agent-mysql-inner` + volume `data-agent-mysql-inner-data`；模拟 MySQL `mysql-data` | `start`/`stop`、备份 dump、灌模拟库 SQL | `down -v`、无卷 recreate、`SQL_INIT=always` |
| **开发平面** | IDEA:8065、Nuxt:3000、proxy:3301 | Restart / JRebel / HMR | 为「清环境」动元库 |

## 库职责（现行）

| 资源 | 地址 | 说明 |
|------|------|------|
| 元库 `nl2sql_db` | `127.0.0.1:3306` | 配置/会话；禁丢卷 |
| 模拟 MySQL | `127.0.0.1:3307` → product_db / enterprise_db | 无数据卷；重建后重灌 `docker-file/config/mysql/*.sql` |
| 远程台账 | `docs/.db.env` → asd_standard | 只读；智能体 #6 |
| PG 模拟 | `:5433`（可选） | 现行默认**不绑**元库 datasource |

## 日常命令

```bash
# 1) 进入 hybrid（停 Docker 前后端，起转发+代理；默认不写库）
bash scripts/dev-hybrid.sh up
bash scripts/dev-hybrid.sh status

# 2) 前端
bash scripts/dev-hybrid.sh frontend

# 3) IDEA：DataAgent-local · profiles=local · JDK17 · JRebel · :8065

# 4) 元库备份（改配置 / 风险操作前）
bash scripts/backup-meta-db.sh

# 5) 模拟库若空（recreate 后）
docker exec -i data-agent-mysql-datasource mysql -uroot -proot \
  < docker-file/config/mysql/product_db.sql
docker exec -i data-agent-mysql-datasource mysql -uroot -proot \
  < docker-file/config/mysql/enterprise_db.sql

# 6) 台账配置丢失
bash scripts/restore-inventory-agent.sh   # 需后端已起
# 详见 docs/dev/INVENTORY_STOCK_FLOW_AGENT.md
```

## 配置红线

| 项 | 正确值 | 说明 |
|----|--------|------|
| IDEA profile | **`local`** | `application-local.yml` → `spring.sql.init.mode=never` |
| Docker backend env | **`DATA_AGENT_DATASOURCE_SQL_INIT=never`** | compose 已固定 |
| 元库 volume | **`data-agent-mysql-inner-data` external** | 禁止 `docker volume rm` / `down -v` |
| datasource host 写库 | **默认关闭** | 仅 `WRITE_HOSTS=1` |

## 业务源地址（hybrid）

- 模拟 MySQL：`127.0.0.1:3307`（UI/元库 datasource 建议长期用此 host）
- 远程台账：直连 `110.41.20.224:3306`（不经 portfwd）

全栈 Docker 演示时容器内访问模拟库需服务名 `mysql-data`：

```bash
WRITE_HOSTS=1 bash scripts/dev-hybrid.sh down
# 且须先停 IDEA 释放 8065
```

## JRebel

- `data-agent-management/src/main/resources/rebel.xml` → `target/classes`
- 可热更：Service/Controller 方法体、多数类变更
- 需 Restart：`StateGraph` 大改、核心 yml、部分 Mapper 签名

## 事故复盘

1. compose 注释 MySQL volume → 数据进容器层  
2. 恢复 volume 时建**新空卷** → 配置全丢  
3. 无定时备份  

对策：external 固定卷 + `SQL_INIT=never` + `backup-meta-db.sh` + hybrid 默认不写库。

## 相关路径

| 路径 | 用途 |
|------|------|
| `scripts/dev-hybrid.sh` | hybrid 进出 / status |
| `scripts/backup-meta-db.sh` | 元库 dump/restore |
| `scripts/restore-inventory-agent.sh` | 台账智能体恢复 |
| `docker-file/docker-compose.yml` | 数据平面 + Docker 演示 |
| `.run/DataAgent-local.run.xml` | IDEA 本地启动 |
| `application-local.yml` | local · sql.init=never |
| `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md` | 台账配置事实 |
| `docs/dev/LOCAL_RUN_CHECKLIST.md` | 可运行 checklist |
