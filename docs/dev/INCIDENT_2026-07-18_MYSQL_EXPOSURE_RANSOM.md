# 事故报告：MySQL 公网暴露导致元库被勒索清空

| 字段 | 值 |
|------|-----|
| **编号** | INC-2026-07-18-MYSQL-EXPOSURE |
| **日期** | 2026-07-18 |
| **严重级别** | **P0（数据平面）** — 元库被清空；业务远端库未损 |
| **状态** | 已恢复服务 + 已收口本机暴露端口（密码轮换仍为后续项） |
| **关联红线** | `.rule/hybrid-hotdeploy-db-safe.md` · `docs/dev/HYBRID_HOTDEPLOY.md` |

---

## 1. 摘要

开发机（公网 IP `183.36.30.83`）将 **MySQL 元库** 以 **`0.0.0.0:3306` + `root/root`** 暴露到公网，被自动化扫描/勒索程序写入 `RECOVER_YOUR_DATA` 并清空业务库 `nl2sql_db`。  
应用启动出现 Druid：

```text
create connection SQLException ... errorCode 1049, state 42000
Unknown database 'nl2sql_db'
```

**不是** JDBC URL 配置错误，**不是** 远端 `asd_standard` 被写坏。

---

## 2. 时间线（UTC / 本地 +8 对照）

| 时间（UTC） | 本地 (+8) | 事件 |
|-------------|-----------|------|
| ≤ 2026-07-18 00:22 | 08:22 | 最后一次完整元库备份 `backups/nl2sql_20260718_002253.sql` |
| ~2026-07-18 03:30 | 11:30 | `data-agent-mysql-inner` 收到 SHUTDOWN 并重启；数据目录仅剩系统库 + `RECOVER_YOUR_DATA` |
| 2026-07-18 ~10:55+ | 18:55+ | 本地后端 Druid 持续报 1049（用户报障） |
| 同日处置-1 | — | 从 `002253` 备份恢复 `nl2sql_db`；补 `admin_user`；修复 `root@%` 被阉割的 DML 权限 |
| 同日处置-2 | — | 端口绑 `127.0.0.1`；删除模拟库勒索标记；写本报告 |

攻击窗口内（约 00:22–03:30 UTC）未备份的元库变更 **不可恢复**。

---

## 3. 影响面

| 组件 | 地址 | 影响 | 处置结果 |
|------|------|------|----------|
| **元库** `nl2sql_db` | 本机 `data-agent-mysql-inner` / volume `data-agent-mysql-inner-data` | **库被删**；勒索库 `RECOVER_YOUR_DATA`（DATAID 见日志） | 已从备份恢复；agent 6/7、datasource 1/4 等回到 00:22 快照 |
| **模拟 MySQL** `product_db` 等 | `data-agent-mysql-datasource`（经 socat `:3307`） | 写入勒索标记库；业务表抽样仍在 | 已 `DROP RECOVER_YOUR_DATA`；表数据保留 |
| **远端业务库** `asd_standard` | `110.41.20.224:3306` | **未中招**（无 `RECOVER%` schema；`t_wms_stock_flow=100`、`t_mes_passstaion≈5万`） | 只读探测通过；App `datasource/4/test` 成功 |
| 模拟 PG / 其它 | `:5433` 等 | 本次未见勒索 schema | 端口已改 loopback |

### 业务影响

- 管理端/Agent 列表在元库清空期间不可用。  
- 恢复后配置回到 **2026-07-18 00:22 备份点**；该点之后手工改动丢失。  
- 远端 WMS/MES 事实数据本身未因本事故改写。

---

## 4. 根因分析

### 4.1 直接原因

1. **Docker 端口发布到全接口**  
   - `docker-file/docker-compose.yml`：`ports: "3306:3306"` → 宿主 `0.0.0.0:3306`  
   - `scripts/dev-hybrid.sh`：`socat -p 3307:3307` / `-p 5433:5433` → 同样 `0.0.0.0`
2. **弱默认凭据**  
   - 元库/模拟库 `MYSQL_ROOT_PASSWORD=root`，`root@%` 可从任意来源登录（权限甚至曾被攻击面削弱后又被滥用）。
3. **主机具备公网 IP** 且安全组/防火墙未拦 3306/3307。

### 4.2 触发链

```text
公网扫描 3306 → root/root 登录成功
  → DROP/清空业务库 + CREATE RECOVER_YOUR_DATA（勒索信）
  → 可选关闭/重启 mysqld
  → 应用 Druid 1049 Unknown database 'nl2sql_db'
```

### 4.3 非原因（已排除）

| 假设 | 结论 |
|------|------|
| `application-local.yml` JDBC 写错库名 | 否；URL 正确指向 `nl2sql_db` |
| `SQL_INIT=always` 误清库 | 否；local/docker 为 `never`；现象是库不存在 + 勒索 schema |
| 远端 `asd_standard` 被同步清空 | 否；独立主机，计数与 schema 正常 |
| 应用层 SQL 注入删库 | 无证据；攻击面是 **暴露的管理端口 + 弱口令** |

### 4.4 控制论归类（η）

- **η₁**：数据平面事故，优先于开发速度。  
- **η₅**：fail-open 到「库没了还能起进程」不够；应 fail-closed 到「库端口不对公网」。  
- **η₇**：备份脚本已存在且打中窗口 → 恢复可行；但端口暴露未度量 → 复发风险高。

---

## 5. 应急处置（已执行）

1. **只读确认** volume、库列表、勒索文案、备份可用性。  
2. **`CREATE DATABASE nl2sql_db` + 导入** `backups/nl2sql_20260718_002253.sql`（**未** `down -v`）。  
3. 补表 `admin_user`（备份时尚无该表）+ 种子管理员 `admin` / `admin123`（BCrypt `$2a$`）。  
4. 恢复 `root@%` 的 INSERT/UPDATE/DELETE（攻击后权限被阉割导致登录 UPDATE 失败）。  
5. **暴露收口**  
   - compose：`127.0.0.1:3306:3306`  
   - hybrid socat：`127.0.0.1:3307` / `127.0.0.1:5433`（及可选 3306 portfwd）  
   - 运行时 recreate **仅** mysql 端口映射（volume 保留）+ 重建 portfwd 容器。  
6. `DROP DATABASE RECOVER_YOUR_DATA`（模拟库；元库侧同步清理）。  
7. 新备份：`backups/nl2sql_20260718_221929.sql`（以实际文件名为准）。

**明确未做**：比特币支付；删除元库 volume；改远端库。

---

## 6. 修复变更（代码）

| 文件 | 变更 |
|------|------|
| `docker-file/docker-compose.yml` | mysql ports → `127.0.0.1:3306:3306` + 注释指向本报告 |
| `scripts/dev-hybrid.sh` | socat `-p 127.0.0.1:…` 仅 loopback |

---

## 7. 验证标准与结果

| 检查项 | 期望 | 结果 |
|--------|------|------|
| `ss -ltn` 3306/3307/5433 | 仅 `127.0.0.1` | 处置后应为 loopback（执行时现场确认） |
| `SHOW DATABASES` 元库 | 有 `nl2sql_db`，无 `RECOVER%` | 恢复后通过 |
| `SELECT COUNT(*) FROM agent` | ≥2（6 库存台账、7 扫码过站） | 通过 |
| `POST /api/auth/login` | success | 恢复后曾通过；mysql recreate 后若池失败需重启 IDEA 后端 |
| `POST /api/datasource/4/test` | 远端连接成功 | 事故调查阶段通过 |
| 模拟库 `product_db.products` | 数据仍在 | 通过（10 行抽样） |

---

## 8. 后续动作（未在本次默认执行）

| 优先级 | 动作 | 说明 |
|--------|------|------|
| **P0** | 云安全组/本机 firewall **拒绝公网 3306/3307/5432/5433** | 纵深；不依赖 Docker 绑定 |
| **P0** | 轮换 MySQL `root` 密码（元库 + 模拟库） | 需同步 compose、`application-local.yml`、备份脚本；避免漏改打断 hybrid |
| **P1** | 管理端改默认 `admin123` | 登录面 |
| **P1** | 备份 cron（每日）+ 异地拷贝 `backups/` | 当前依赖人工 `backup-meta-db.sh` |
| **P1** | 审计同机其它 `0.0.0.0:5432` 等（如 weallstar-postgres） | **观察项**，非 DataAgent 范围但同主机风险 |
| **P2** | 元库专用非 root 应用账号 + 最小权限 | 降低再被扫后的爆炸半径 |
| **P2** | `backup-meta-db.sh` 纳入 `admin_user` 等后加表的回归 | 本次 dump 已含 |

---

## 9. 复发条件（红线）

以下任一成立 ≈ 事故重演：

1. 再次把 MySQL 发布为 `0.0.0.0:3306` 或无 IP 限定的 `-p 3306:3306`  
2. hybrid socat 使用 `-p 3307:3307` 而非 `127.0.0.1:3307`  
3. `docker compose down -v` / 删除 `data-agent-mysql-inner-data`（**额外**数据灾难）  
4. 公网可达 + 弱口令长期不变  

---

## 10. 经验与规范挂钩

- **数据平面稳态 > 开发便利**：本机调试用 loopback 足够；公网入口只应是 `:3301` 代理。  
- **备份有效**：`scripts/backup-meta-db.sh` 是本次恢复的唯一依赖。  
- **勿付款**：勒索文案为标准 MySQL 扫库模板；本地备份已足够恢复。  
- 与 `.rule/hybrid-hotdeploy-db-safe.md` 一致：禁 `down -v`、禁无备份大清理、禁默认 `SQL_INIT=always`。

---

## 11. 参考路径

| 路径 | 用途 |
|------|------|
| `backups/nl2sql_*.sql` | 元库转储 |
| `scripts/backup-meta-db.sh` | 备份/恢复入口 |
| `scripts/dev-hybrid.sh` | hybrid 端口（现仅 loopback） |
| `docker-file/docker-compose.yml` | mysql 端口绑定 |
| `.rule/hybrid-hotdeploy-db-safe.md` | 运行红线 |
| `docs/dev/HYBRID_HOTDEPLOY.md` | 混合开发说明 |
| `docs/dev/INVENTORY_STOCK_FLOW_AGENT.md` | 台账 agent/远端 DS 约定 |

---

## 12. 签署

| 角色 | 说明 |
|------|------|
| 发现 | 用户报障 Druid 1049 |
| 调查/恢复/加固 | 本地处置（本报告） |
| 批准执行加固 | 用户确认 workflow 计划 |

**结论**：根因是 **公网暴露 + 弱口令**；服务已从备份恢复；**端口已改为仅 127.0.0.1**。在完成密码轮换与安全组收敛前，仍视为 **残余风险：中**（本机其它端口、历史口令可能已泄露）。
