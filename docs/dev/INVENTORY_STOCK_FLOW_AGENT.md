# 库存台账智能体（agentId=6）— 搭建与质量优化记录

> 日期：2026-07-17 · 分支：`dev` · 方式：纯配置/API（未改 Graph 代码）  
> 输入材料：`docs/.db.env` · `docs/data/db.csv` · `docs/data/asd_standard测试库数据库字典.xlsx`

> 最近全量重配：2026-07-17 — prompt/逻辑外键/报告优化/语义模型/术语/预设/指标7/别名20/sql_example9/Schema re-init；冒烟 COUNT=100 ✓（~21s）

## 1. 目标

基于 ASD 标准库 `t_wms_stock_flow`（库存台账）搭建可问答智能体，并持续提升输出质量：

1. 正确连库、选表、Schema 初始化  
2. 提升 SQL/JOIN/口径稳定性  
3. 报告高质量叙述 + echarts 图表讲述  
4. 配置指标（metric）与语义别名（semantic_alias）

## 2. 资源清单（运行时）

| 资源 | ID / 值 | 说明 |
|------|---------|------|
| Agent | **6** · 库存台账智能体 | `status=published` · `workflowMode=nl2sql` |
| Datasource | **4** · ASD标准库-库存台账 | `110.41.20.224:3306/asd_standard` · 用户 `ASD` |
| 选中表 | `t_wms_stock_flow`, `t_wms_stock`, `t_warehouse`（**不含** t_material 向量化） | 流水/库存/仓；物料仅 SQL JOIN |
| 语义模型字段 | 20 | 流水+仓库+即时库存关键列中文业务名 |
| 业务术语 | 7+ | 含标准指标目录；向量已 refresh |
| 预设问题 | 5 | 条数/类型/按仓/最近流水/即时库存 |
| 逻辑外键 | 2 | flow→warehouse · stock→warehouse |
| sql_example | 9 · reviewed=1 | few-shot 反哺 `sql_example` 表 |
| report 优化配置 | `369a1de0-793e-4c98-8806-6477da9ba782` | `promptType=report-generator` · agentId=6 · priority=100 |
| metric | 10 + 默认口径版本 | 见 §5 / §12 |
| semantic_alias | 20 | METRIC/DIM/FILTER 映射，见 §6 |

### 2.1 连接信息来源

```text
# docs/.db.env
110.41.20.224:3306/asd_standard
账号：ASD，密码：asd@2014
```

> 生产环境请轮换密码；本文仅记录开发期配置事实。

### 2.2 核心表与关联

**事实表 `t_wms_stock_flow`（样本约 100 行）**

| 字段 | 业务含义 | 备注 |
|------|----------|------|
| F_id | 主键 | |
| FBarCode | 条码号 | |
| FBusinessType | 业务类型 | 可为空；样本含「入库回滚」 |
| FCreateTime | 操作时间 | 时间过滤优先字段 |
| FCreateUserID | 操作人 | |
| FFlowType | 流转类型 | `in` 入库 / `out` 出库 |
| FKdNumber | ERP 单据编码 | |
| FMaterialID | 物料 ID | 与 `t_bd_material.F_id` 样本不可直接 JOIN |
| FQty | 操作数量 | **出库常为负** |
| FRemainderQty | 剩余数量 | 操作后结余 |
| FSourceNumber | 来源单据编码 | |
| FSourceQty | 原始数量 | |
| FStockId | 仓库 ID（字符串） | 需 CAST 后关联仓库 |
| FStockIdLocId | 储位 | |

**关联**

```sql
-- 流水 → 仓库名
CAST(t_wms_stock_flow.FStockId AS UNSIGNED) = t_warehouse.F_id

-- 即时库存 → 仓库
t_wms_stock.FStockId = t_warehouse.F_id
```

**样本仓库**：电子成品仓 / 手机原料仓 / 手机成品仓。

## 3. 搭建步骤（可复现 API）

```bash
BASE=http://localhost:8065

# 1) 数据源
curl -s -X POST $BASE/api/datasource -H 'Content-Type: application/json' -d '{
  "name":"ASD标准库-库存台账","type":"mysql",
  "host":"110.41.20.224","port":3306,"databaseName":"asd_standard",
  "username":"ASD","password":"<secret>","status":"active",
  "description":"docs/.db.env asd_standard；主表 t_wms_stock_flow"
}'
# → datasourceId=4；POST /api/datasource/4/test 期望 success

# 2) Agent
curl -s -X POST $BASE/api/agent -H 'Content-Type: application/json' -d '{
  "name":"库存台账智能体",
  "description":"WMS 库存台账分析",
  "status":"draft","workflowMode":"nl2sql","category":"仓储物流",
  "tags":"库存台账,WMS,出入库,t_wms_stock_flow",
  "prompt":"..."
}'
# → agentId=6

# 3) 绑定 + 选表 + Schema 初始化
curl -s -X POST $BASE/api/agent/6/datasources/4
curl -s -X POST $BASE/api/agent/6/datasources/tables -H 'Content-Type: application/json' -d '{
  "datasourceId":4,
  "tables":["t_wms_stock_flow","t_wms_stock","t_warehouse"]
}'
curl -s -X POST $BASE/api/agent/6/datasources/init

# 4) 语义模型批量导入、业务术语、预设问题
# POST /api/semantic-model/batch-import
# POST /api/business-knowledge
# POST /api/agent/6/preset-questions

# 5) 发布
curl -s -X POST $BASE/api/agent/6/publish
```

任务过程目录：`.ccg/tasks/inventory-stock-flow-agent/`。

## 4. 输出质量优化（两阶段）

任务过程目录：`.ccg/tasks/inventory-agent-output-quality/`。

### 4.1 阶段 A — 正确性与抗注水（基线）

| 杠杆 | 动作 |
|------|------|
| Agent prompt | 表模型、JOIN、FQty 口径、只读规则 |
| 逻辑外键 | `t_wms_stock_flow.FStockId → t_warehouse.F_id`；`t_wms_stock.FStockId → t_warehouse.F_id` |
| sql_example | 6 条 MANUAL + reviewed=1（元库 `nl2sql_db.sql_example`） |
| 业务术语 | 出入库/仓库/即时库存/数量口径 |
| Schema re-init | 关系进向量 |

**阶段 A 度量（简）**

| 问题 | 结果 |
|------|------|
| 流水条数 | `COUNT(*)=100` ✓ |
| 出入库合计 | in 248/50 笔；out -406/50 笔 ✓ |
| 按仓库汇总 | JOIN 仓库名 ✓ |

### 4.2 阶段 B — 高质量长报告 + 图表讲述（现行）

用户反馈：报告可以长，但质量要高，并配合图表；同时要配指标与语义别名。

**报告优化指令**（`/api/prompt-config/save`，agentId=6，priority=100）要点：

1. 结论先讲（数字 + 业务含义）  
2. Markdown 结果表  
3. **2+ 分组维度必须出 ` ```echarts ` 纯 JSON option**，并解读图意  
4. 口径与方法写清  
5. 洞察须有数据支撑；禁套话/禁编造  

**阶段 B 实测**（问：按仓库名称汇总出入库操作数量，并给出图表分析）

| 项 | 结果 |
|----|------|
| SQL | JOIN `t_warehouse` + CASE in/out 聚合 ✓ |
| 结果 | 手机成品仓 200/200；电子成品仓 48/46；手机原料仓 0/160 |
| 报告 | ~4k 字：结论/占比/表格/**echarts 柱状对比**/口径/建议 |
| echarts | 标题「各仓库入库与出库总量对比」，系列 入库/出库 |
| 全链路 | 含 Python 分析节点；complete；无 error |

## 5. 指标配置（metric）

API：`POST /api/metric` · 候选：`GET /api/metric/candidates?agentId=6&datasourceId=4`  
口径版本：`POST /api/metric-version`（每个指标 1 条 `verCode=default`）

| metricCode | metricName | 表 | 口径摘要 |
|------------|-------------|-----|----------|
| stock_flow_count | 库存流水条数 | t_wms_stock_flow | COUNT(*) |
| stock_flow_qty_sum | 库存变动数量合计 | t_wms_stock_flow | SUM(FQty) 净变动 |
| stock_in_qty | 入库数量 | t_wms_stock_flow | SUM(FQty) WHERE FFlowType='in' |
| stock_out_qty | 出库数量 | t_wms_stock_flow | SUM(ABS(FQty)) WHERE FFlowType='out' |
| stock_flow_by_type | 按出入库类型统计 | t_wms_stock_flow | GROUP BY FFlowType |
| stock_flow_by_warehouse | 分仓库出入库量 | t_wms_stock_flow + t_warehouse | JOIN 后按仓/类型 |
| stock_onhand_qty | 即时库存总量 | t_wms_stock | SUM(FQty) 快照 |

> 当前 Agent `workflowMode=nl2sql`：指标主要作为**口径目录**写入 prompt/术语，约束自由 SQL。  
> 若切 `workflowMode=semantic`，则走 `nl2sqlSemanticGraph`（SemanticParse→BuildSQL 受控拼装），候选指标强约束选择空间。

## 6. 语义别名（semantic_alias）

API：`POST /api/semantic-alias` · 列表：`GET /api/semantic-alias/agent/6` · 消歧：`GET /api/semantic-alias/resolve?agentId=6&text=...`

| aliasText | targetType | targetCode |
|-----------|------------|------------|
| 流水条数 / 库存台账条数 / 台账一共多少条 | METRIC | stock_flow_count |
| 入库量 / 入库数量 | METRIC | stock_in_qty |
| 出库量 / 出库数量 | METRIC | stock_out_qty |
| 出入库数量 / 按出入库类型统计 | METRIC | stock_flow_by_type |
| 按仓库汇总 / 分仓库出入库 | METRIC | stock_flow_by_warehouse |
| 现存量 / 即时库存 / 库存总量 | METRIC | stock_onhand_qty |
| 净变动 | METRIC | stock_flow_qty_sum |
| 电子成品仓 / 手机原料仓 / 手机成品仓 | DIM | warehouse:… |
| 入库 / 出库 | FILTER | FFlowType=in/out |

## 7. 使用方式

**前端**：智能体列表 → **库存台账智能体**

**SSE 冒烟**

```bash
Q=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("按仓库名称汇总出入库操作数量，并给出图表分析"))')
curl -sN "http://localhost:8065/api/stream/search?agentId=6&query=$Q"
```

**预设问题样例**

1. 库存台账一共有多少条流水？  
2. 按出入库类型统计数量合计  
3. 按仓库汇总出入库操作数量  
4. 最近 10 条库存流水记录  
5. 各仓库当前即时库存总量  

## 8. 已知限制

1. **端到端延迟**约 2–4 分钟（模型网关读超时历史问题，见 memory：model-gateway-60s-timeout）  
2. **物料维** `FMaterialID ↔ t_bd_material` 样本无法可靠 JOIN，未绑物料表  
3. 流水样本仅 **100 行**，洞察仅对当前样本成立  
4. Evidence 召回偶发空，通常不阻断 SQL  
5. 指标/别名完整接管需切换 `workflowMode=semantic`（尚未切换）  
6. 报告优化配置仅绑定 agentId=6  

## 9. 变更范围说明

| 类型 | 是否变更 |
|------|----------|
| Java / Graph 代码 | 否 |
| 前端代码 | 否 |
| 元库配置（agent/datasource/metric/alias/prompt/sql_example） | **是** |
| 业务库 asd_standard | 只读 |
| docs/dev 文档 | 本文 |

## 10. 相关路径

| 路径 | 用途 |
|------|------|
| `docs/.db.env` | 业务库连接 |
| `docs/data/db.csv` | 台账字段摘录 |
| `docs/data/asd_standard测试库数据库字典.xlsx` | 全库字典 |
| `.ccg/tasks/inventory-stock-flow-agent/` | 搭建任务过程 |
| `.ccg/tasks/inventory-agent-output-quality/` | 质量优化度量 |
| `docs/dev/V0.2_ARCHITECTURE.md` | 语义层/指标架构背景 |
| `docs/dev/API_INTEGRATION.md` | API 对接总表 |

## 11. 元库持久化与一键恢复

- Compose 元库卷：`data-agent-mysql-inner-data`（`docker-file/docker-compose.yml`，`external: true`）
- **禁止** `docker compose down -v`（会删命名卷）
- 日常 hybrid：`bash scripts/dev-hybrid.sh up` + IDEA `profiles=local`（`SQL_INIT=never`）
- 备份：`bash scripts/backup-meta-db.sh` → `backups/nl2sql_*.sql`
- 空库/丢配置后恢复：`bash scripts/restore-inventory-agent.sh`（业务库 asd_standard **只读**；需后端 `:8065` 已起）
- root@% 需 `INSERT` 权限（IDEA 经 3306 写元库）；若 `INSERT command denied to user 'root'@'<host-ip>'`：
  `docker exec data-agent-mysql-inner mysql -uroot -proot -e "GRANT ALL ON *.* TO 'root'@'%' WITH GRANT OPTION; FLUSH PRIVILEGES;"`

### 11.1 2026-07-17 恢复后基线（事实）

| 项 | 值 |
|----|-----|
| Agent | **6** · published · nl2sql · embed 已开（显式 origins，非 `*`） |
| DS **1** | 模拟业务库-product_db · `127.0.0.1:3307` · 测连 OK |
| DS **4** | ASD标准库-库存台账 · `110.41.20.224:3306` · 测连 OK |
| 选中表 | 3：`t_wms_stock_flow` / `t_wms_stock` / `t_warehouse` |
| semantic_model | 20 |
| business_knowledge | 7 |
| metric + version | 7 + 7 default |
| semantic_alias | 20 |
| sql_example | 3（脚本路径；完整 6 条可再补） |
| presets | 5 |
| 其它种子 Agent | **已删除**（仅保留台账） |

**注意**：`POST /api/metric` / `semantic-alias` 请求体若缺 `source_table` 等表字段会 4xx/5xx；完整恢复以脚本 + 按表结构 SQL / dump 为准。  
`restore-inventory-agent.sh` 已修 datasource 创建响应的 JSON 解析（勿用错误 heredoc）。

**并列数据面**：模拟 MySQL 可无持久卷，recreate 后执行：

```bash
docker exec -i data-agent-mysql-datasource mysql -uroot -proot \
  < docker-file/config/mysql/product_db.sql
```

## 12. 后续可选

- [ ] 切 `workflowMode=semantic` 并回归 5 个预设问题  
- [ ] 补物料维映射（若业务给出正确关联键）  
- [ ] 扩展选表：`t_wms_stock_in/out/change`  
- [ ] 延迟专项（网关超时 / 节点跳过策略）  
- [ ] 将 sql_example 持续从成功 query_log 反哺

## 12. 真实数据画像与二次优化（2026-07-17）

基于 `asd_standard` 实测 + `asd_standard测试库数据库字典.xlsx`。

### 12.1 流水画像（t_wms_stock_flow = 100 行）

| 维度 | 事实 |
|------|------|
| 时间 | 2024-08-13 09:55 ~ 2024-08-15 00:29 |
| in / out | 各 50 笔；qty +248 / -406 |
| FBusinessType | 空 52 笔；**入库回滚 48 笔**（全是 out） |
| 真实出库 | out 且非回滚 = **仅 2 笔**（手机原料仓听筒/喇叭各 -80） |
| 仓库 | 电子成品仓 94 笔；手机成品仓 4；手机原料仓 2 |
| 物料（JOIN t_material） | 电脑×94；主板0035×4；听筒×1；喇叭×1 |

### 12.2 字典级关联（已落地）

| 关系 | SQL | 命中 |
|------|-----|------|
| 流水→仓库 | `CAST(FStockId AS UNSIGNED)=t_warehouse.F_id` | 100% |
| 流水→物料 | `FMaterialID=t_material.F_id`（**勿用 t_bd_material**） | 100% |
| 流水→即时库存条码 | `FBarCode=t_wms_stock.FBarcodeId` | 97/100 |
| 字典废字段 | `t_wms_stock.FBarcode` 标注「条码-作废」 | 勿 JOIN |

### 12.3 不宜绑表（当前空/无关）

- `t_wms_stock_in` / `t_wms_stock_out`：**0 行**
- `t_bd_material_l`：**0 行** 无名称
- 字典中入库单/出库单/上架/拣货等模块表：有结构但本样本无分析价值

### 12.4 二次优化落地

- 选表增加 **t_material**
- 逻辑外键 +2：Material、BarCode↔FBarcodeId（合计 4）
- 指标 +3：`stock_real_out_qty` / `stock_rollback_qty` / `stock_flow_by_material`（合计 10）
- 术语：入库回滚、真实出库、物料、条码关联、样本说明
- sql_example 13；预设含回滚/物料问法
- 冒烟：按物料汇总 JOIN t_material ✓；回滚占出库 **60.59%**（246/406）✓

## 13. 向量/SSE 故障（Embedding 风暴）— 2026-07-17

### 现象
- 后端日志刷屏：`o.s.ai.vectorstore.SimpleVectorStore : Calling EmbeddingModel for document id ...`
- 前端：`Stream connection failed`

### 根因
1. Schema 初始化会对**选中表的每个列**写向量文档（含 sample 值），经 `SimpleVectorStore.add` **逐批调 Embedding API**。
2. 将 **`t_material`（~5 万行 / 83 列）** 加入选表后，初始化/召回路径 Embedding 调用爆炸，占满网关与后端线程。
3. `vectorstore.json` 几乎为空 `{}` 时，每次初始化都要重新 embed（无持久化缓存可用）。
4. SSE 长时间无有效进度或后端阻塞 → EventSource `onerror` →「Stream connection failed」。

### 处置
1. 选表**仅保留**小表：`t_wms_stock_flow`, `t_wms_stock`, `t_warehouse`
2. `t_material` 仍可在 SQL 中 JOIN（逻辑外键 + prompt + few-shot），**不要**再 Schema init 该表
3. 重新 `POST /api/agent/6/datasources/init` 成功后，短问冒烟约 20s 恢复

### 原则
- Schema 向量只收「高频小维表」；大主数据用 SQL JOIN + 业务术语/样例约束
- 改选表后务必 re-init；init 期间勿并发问数

