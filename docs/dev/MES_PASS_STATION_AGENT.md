# 扫码过站智能体（agentId=7）— 配置记录

> 日期：2026-07-17 · 分支：`dev` · 方式：纯配置/API  
> 主表：`asd_standard.t_mes_passstaion`（库内表名拼写为 **passstaion**）  
> 数据源：复用 DS **4**（`docs/.db.env` → `110.41.20.224:3306/asd_standard`）

## 1. 目标

与库存台账智能体同模式，为 MES **扫码过站** 建可问数 Agent：过站量、NG 率、分站点、条码轨迹、工单关联。

## 2. 资源清单

| 资源 | ID / 值 |
|------|---------|
| Agent | **7** · 扫码过站智能体 · `published` · `nl2sql` |
| Datasource | **4** ASD标准库-库存台账（同库 asd_standard） |
| 选中表（向量化） | `t_mes_passstaion`, `t_mes_station` |
| 逻辑外键 | pass.FStationID → station.F_id |
| 语义模型 | 25 字段 |
| 业务术语 | 6 |
| 指标 | 6（pass_count / pass_ng_* / pass_by_* / pass_scan_qty_sum） |
| 语义别名 | 12 |
| sql_example | 6 · reviewed |
| 报告优化 | `40543255-06f9-4d5f-a4c4-20993ba23f2d` priority=100 |
| 预设问题 | 5 |

### 故意不向量化（防 Embedding 风暴）

| 表 | 原因 | 用法 |
|----|------|------|
| `t_material` | ~5 万行大表 | SQL：`FMaterialID=t_material.F_id` |
| `t_mo` | 列多、关联稀疏 | SQL：`FMoID=t_mo.F_id` → `FBillNo` |
| `t_mes_line` | 样本 join 仅约 15 行 | 可选 JOIN |

## 3. 数据画像（实测）

| 维度 | 事实 |
|------|------|
| 总行 | 50,234；**有效** `FIsDeleted=0` → **49,765** |
| 时间 | 2024-01-11 ~ 2025-12-16 |
| NG | FIsNG=1 → 1,392 |
| 主站点 | **网分设备采集站 (85)** ≈ 47,251 笔 |
| 其他站点 | ATE老化后站、功能测试、PCBA上料、外观测试… |
| 条码 | 非空 distinct ≈ 9,618 |
| 工单 | 多数 FMoID 为空；有值时可 JOIN `t_mo` |

### 主表关键字段

| 字段 | 含义 |
|------|------|
| FBarcode | 条码 |
| FStationID | 站点 → `t_mes_station` |
| FCreateTime | 过站时间 |
| FIsNG | 1=不良 |
| FIsDeleted | 逻辑删除，分析默认 0 |
| FScanQty | 扫描数量 |
| FQualifiedNum / FUnQualifiedNum | 合格/不合格数 |
| FMoID | 工单 ID |
| FMaterialID | 物料 ID |
| FDefect | 缺陷码 |
| FUserID / FOperatorID | 操作人 |

## 4. 指标

| code | 名称 | 口径摘要 |
|------|------|----------|
| pass_count | 过站总笔数 | COUNT(*) WHERE FIsDeleted=0 |
| pass_ng_count | NG 笔数 | FIsNG=1 |
| pass_ng_rate | NG 率 | NG/总数 |
| pass_by_station | 分站点过站量 | JOIN station GROUP BY FName |
| pass_by_day | 每日过站量 | DATE(FCreateTime) |
| pass_scan_qty_sum | 扫描数量合计 | SUM(FScanQty) |

## 5. 冒烟（已验证）

| 问题 | 结果 | 耗时 |
|------|------|------|
| 扫码过站一共有多少条有效记录？ | **49765**（FIsDeleted=0） | ~18s |
| 按站点统计过站量和 NG 数 | JOIN `t_mes_station`，按 FName 汇总 | ~39s |

全链路 complete，无 error。

## 6. 使用

前端：智能体列表 → **扫码过站智能体**

```bash
Q=$(python3 -c 'import urllib.parse; print(urllib.parse.quote("按站点统计过站量和NG数，并出图"))')
curl -sN "http://localhost:8065/api/stream/search?agentId=7&query=$Q"
```

预设：

1. 扫码过站一共有多少条有效记录？  
2. 按站点统计过站量和 NG 数，并出图  
3. 整体 NG 率是多少？  
4. 过站量最多的前 10 个站点  
5. 按天统计过站量趋势  

## 7. 与库存台账智能体对照

| | 库存台账 #6 | 扫码过站 #7 |
|--|-------------|-------------|
| 主表 | t_wms_stock_flow (100) | t_mes_passstaion (~5万) |
| 维表向量 | warehouse, stock | t_mes_station |
| 库 | asd_standard DS4 | 同 DS4 |
| 模式 | nl2sql | nl2sql |

## 8. 注意

1. 表名是 **`t_mes_passstaion`**（少一个 t），SQL 必须用库内真实拼写。  
2. 主表行数大但**只向量化列元数据**（非按行 embed）；勿再把 t_material 加进选表。  
3. 「按站点」用 **COUNT(*)** 看笔数更稳；`SUM(FScanQty)` 在部分站点样本偏稀疏。  
4. 恢复参考：`scripts/restore-inventory-agent.sh` 模式；本 Agent 可仿写 restore 脚本（未单独脚本化）。
