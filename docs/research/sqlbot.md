# SQLBot

> 路线② · 全栈问数产品 · 本地路径 `/home/ubuntu/dev/reference/SQLBot`

## 一句话定位

SQLBot 是 DataEase 团队开源的**基于 LLM + RAG 的企业级 ChatBI 问数系统**，把"自然语言 → SQL → 数据 → 可视化图表 → 智能分析"拆成一条**固定多步串行流水线**，工程化亮点在 **AST 级 SQL 安全护栏（只读校验 + 危险函数黑名单 + 表名白名单）** 与 **行级权限 LLM 改写**。

## 技术栈与版本

> 来源：`backend/pyproject.toml`（name=`sqlbot`, version=`1.9.0`）、`frontend/package.json`、`g2-ssr/package.json`。

- **后端（Python 3.11，非 Go）**：FastAPI（`fastapi[standard]>=0.115`）+ SQLModel/SQLAlchemy + Alembic + Pydantic v2 + `langchain 0.3` / `langgraph 0.3` + `pgvector` + `sentence-transformers`。
- **SQL 安全核心依赖**：`sqlglot>=28.6`（AST 解析/方言，跨库只读校验主力）、`sqlparse>=0.5`（格式化展示）。
- **多数据源驱动**：pymysql / psycopg2 / pymssql / oracledb / clickhouse-sqlalchemy / pymssql / redshift-connector / pyhive / oracledb / dmPython(达梦) / elasticsearch 7.x，外加 doris / starrocks / kingbase / hive。
- **可视化（g2-ssr）**：独立 Node.js 微服务，`@antv/g2-ssr` + `@antv/g2 5.3` + node-canvas + pm2 守护，监听 `:3000`，HTTP POST 传 `{type,axis,data,path}` 渲染 PNG 落盘。字体用内置 `Arial_Unicode.ttf`（23MB，解决中文渲染）。
- **前端（Vue 3，非 React）**：Vue 3.5 + Vite + Element Plus + `@antv/g2`、`@antv/s2`、`@antv/x6`（表关系图）+ vue-i18n + TinyMCE。
- **企业版增强**：`sqlbot-xpack`（私有源 testpypi）提供自定义提示词、行级/列级权限模型、License 校验。
- **集成**：`fastapi-mcp`（MCP 协议）、Web 嵌入 / 弹窗嵌入 / SSO / LDAP（`ldap3`）。

## 核心架构

请求由 `LLMService.run_task()`（`backend/apps/chat/task/llm.py:1210`）单方法串联，用 Python 生成器 + SSE 向前端流式吐 chunk，**无显式重试/循环边**（失败即终止记录错误）。线程池 `ThreadPoolExecutor(max_workers=200)` 跑异步任务。

```mermaid
flowchart TD
    Q[用户问题] --> DS{数据源已选定?}
    DS -- 否 --> SDS[select_datasource<br/>LLM 选库 + 向量召回 ds_embedding]
    DS -- 是 --> CONN[check_connection]
    SDS --> CONN
    CONN --> SCHEMA[choose_table_schema<br/>RAG 召回 top-K 表 + 样本数据]
    SCHEMA --> GENSQL[generate_sql<br/>LLM 生成 SQL, 流式]
    GENSQL --> PARSE[check_sql<br/>extract_nested_json 解析]
    PARSE --> TBLCHK[表名白名单校验<br/>extract_tables_from_sql<br/>sqlglot 解析真实表名 vs allowed]
    TBLCHK --> ROW{需行权限?<br/>is_normal_user}
    ROW -- 是 --> FLT[generate_filter<br/>LLM 把 WHERE 注入 SQL]
    ROW -- 否 --> SAVE[check_save_sql]
    FLT --> SAVE
    SAVE --> READONLY[exec_sql → check_sql_read<br/>AST 只读+危险函数黑名单]
    READONLY --> EXEC[(目标库执行)]
    EXEC --> DATA[结果集 1000 行截断]
    DATA --> CHART[generate_chart<br/>LLM 生成图表配置 JSON]
    CHART --> PIC[g2-ssr :3000<br/>渲染 PNG]
    DATA --> ANS[可选: 智能分析 / 预测]
    PIC --> R[前端 SSE]
    ANS --> R

    classDef safe fill:#ffe,stroke:#c80;
    class TBLCHK,READONLY safe;
```

> **注**：图中虚线"自愈重试"在 SQLBot 中**不存在**——执行失败直接 `save_error` 终止。这是它最大的鲁棒性短板（见"局限"）。

## 关键源码路径

| 后端包/文件 | 职责 |
|---|---|
| `backend/apps/chat/task/llm.py`（1952 行） | **编排核心**。`LLMService` 类：`run_task()` 主流程、`generate_sql/generate_chart/generate_filter/select_datasource/generate_analysis/generate_predict`。模块级 `extract_tables_from_sql()`（L71）做表名白名单校验。 |
| `backend/apps/chat/api/chat.py` | FastAPI 路由，SSE 入口，实例化 `LLMService` 并提交线程池。 |
| `backend/apps/chat/models/chat_model.py` | `ChatQuestion.sql_sys_question()`（L235）等提示词装配方法，引用 `templates/`。 |
| `backend/apps/db/db.py`（851 行） | **SQL 执行 + 安全护栏**。`exec_sql()`（L585）、`check_sql_read()`（L777）、`check_dangerous_functions()`（L764）、`get_sqlglot_dialect()`（L720）。多数据源连接/版本/schema 反射。 |
| `backend/apps/db/db_sql.py` | 各库 `information_schema` 查表/查字段 SQL 模板。 |
| `backend/apps/datasource/crud/permission.py` | `get_row_permission_filters()` / `get_column_permission_fields()` / `is_normal_user()`（非 id=1 即普通用户）。 |
| `backend/apps/datasource/crud/row_permission.py` | **行权限 WHERE 子句生成器**。`transFilterTree/transTreeToWhere/transTreeItem` 把权限树翻译成 SQL 片段，`_escape_sql_value()` 防注入，系统变量（当前用户名/账号）注入。 |
| `backend/apps/datasource/crud/datasource.py` | `get_table_schema()`（L508，向量召回 top-K）、`get_tables_sample_data()`（L490，样本数据注入 prompt）。 |
| `backend/apps/datasource/embedding/table_embedding.py` | 表结构向量化 + 余弦相似度排序，`TABLE_EMBEDDING_COUNT=10`。 |
| `backend/templates/template.yaml` | **提示词母版**：system / process_check / query_limit / generate_rules。 |
| `backend/templates/sql_examples/*.yaml` | 13 种数据库各自的引号/方言/示例 SQL（MySQL/PG/Oracle/SQLServer/CK/Doris/StarRocks/Hive/ES/Redshift/Kingbase/DM）。 |
| `backend/common/utils/whitelist.py` | **HTTP 路由白名单**（免鉴权路径，非 SQL 白名单）。 |
| `backend/common/utils/aes_crypto.py` / `aes_decrypt` | 数据源密码 AES 加密存储。 |
| `g2-ssr/app.js` + `g2-ssr/charts/{bar,column,line,pie}.js` | 服务端图表渲染微服务。 |
| `frontend/src/views/` | Vue 前端对话/仪表盘/数据源管理页。 |

## 亮点设计（源码级）

### 1. AST 级 SQL 只读校验 —— 三层防御（`db.py:check_sql_read` L777-842）

这是 SQLBot 最值得借鉴的设计，**执行前**强制校验，与 LLM 无关的硬护栏：

- **第一层 关键字黑白名单**（L783-801）：取 SQL 去括号后首词 `first_keyword`，先查 `denied_write_commands = {INSERT,UPDATE,DELETE,CREATE,DROP,ALTER,TRUNCATE,MERGE,COPY,REPLACE,GRANT,REVOKE,USE,SET,CALL}` 直接拒；元数据查询（SHOW/DESCRIBE/EXPLAIN）受开关 `SQLBOT_ALLOW_METADATA_QUERIES`（默认 `False`）控制，默认只允许 `{SELECT, WITH}`。
- **第二层 危险模式正则**（L747-752 `DANGEROUS_PATTERNS`）：`INTO OUTFILE` / `INTO DUMPFILE` / `EXEC(` / `COPY ... TO PROGRAM`。
- **第三层 sqlglot AST**（L808-834）：按 `get_sqlglot_dialect()`（mysql→`mysql`、sqlServer→`tsql`、hive→`hive`，其余 `None`）解析，遍历 `exp.Anonymous` 命中 `get_dangerous_functions()`（通用 `version/current_user/user/database` + 库特定：MySQL `LOAD_FILE/INTO OUTFILE`、PG `pg_read_file/lo_import`、SQLServer `EXEC/xp_cmdshell`、Oracle `UTL_FILE` 等），再 `isinstance(stmt, (exp.Insert,exp.Update,...))` 兜底写操作类型。

> 失败抛 `ValueError`，`exec_sql` 调用方（`llm.py:execute_sql` L1158）转成 `SQLBotDBError`。**校验与执行同方法、无旁路**。

### 2. 表名白名单 —— 不信 LLM 自报（`llm.py` L1305-1320）

`check_sql()` 只从 LLM JSON 里取 `tables` 字段，但 `run_task()` 随即用 `extract_tables_from_sql(sql, ds_type)`（L71-84，**sqlglot `find_all(exp.Table)` 重新解析真实 SQL**）与 `allowed_tables = set(self.table_name_list)`（RAG 召回 + 用户授权的表集合）做差集，任何 `unauthorized_tables` 非空直接 `SingleMessageError` 拒绝。这堵住了"LLM 在 JSON 里撒谎、SQL 里偷查他表"的攻击面。

### 3. 行级权限 LLM 改写（`permission.py` + `row_permission.py` + `llm.build_table_filter` L872）

区别于"在 SQL 上机械拼 WHERE"，SQLBot 用 **LLM 做语义改写**：

- `get_row_permission_filters()` 查 `DsPermission`（xpack 表）+ `DsRules`（用户-权限关联），只对 `is_normal_user`（id≠1）生效。
- `transFilterTree()` 把权限树翻译成参数化 WHERE 片段，支持系统变量（`current_user.name/account/email`，L247-287）注入，实现"张三只能看自己部门数据"。
- `_escape_sql_value()`（L12）标准 SQL 转义（`'`→`''`、`\`→`\\`）防注入；`_VALID_LOGIC_OPS = {AND,OR}`（L42）防 logic 字段注入。
- 把 `{table, filter}` 喂给 `build_table_filter()` → `filter_sys_question()/filter_user_question()` 提示词 → **LLM 二次生成完整 SQL**（`OperationEnum.GENERATE_SQL_WITH_PERMISSIONS`），再走一遍 `check_save_sql`。即权限改写产物仍受只读校验约束。

### 4. RAG 三路召回注入 prompt（`llm.init_messages` L244）

单次 SQL 生成会装配多段"确认型"对话（每段 Human+AI 配对，强约束 LLM）：
- `terminologies`（术语库，同义词映射，解决业务黑话）；
- `data_training`（人工标注的 SQL 示例，类似 vanna 训练库）；
- `custom_prompt`（xpack 自定义提示词）；
- `db_schema` + `sample_data`（M-Schema 表结构 + 样本几行数据）。

每段后跟一句 `AIPromptMessage('我已确认...我会严格遵守')`，是典型的 **prompt 内嵌确认协议**。历史对话用 `get_last_conversation_rounds()`（L1929）按"用户消息"切片取最近 N 轮（`GENERATE_SQL_QUERY_HISTORY_ROUND_COUNT=3`）。

### 5. g2-ssr 服务端渲染图表（`g2-ssr/` + `llm.request_picture` L1744）

独立 Node 进程（pm2 守护），FastAPI 后端把 `{path, type, data, axis}` POST 到 `MCP_IMAGE_HOST=http://localhost:3000`，`@antv/g2-ssr` 的 `createChart(options).exportToFile(path)` 直接渲染 PNG 落盘，返回 URL。前端拿到 `![type](url)` 即可在对话流里内联展示。`charts/{bar,column,line,pie}.js` 各自封装 options 构造，`utils.js` 复用。**LLM 只产出图表配置 JSON（type + axis 映射），渲染下沉到 SSR 微服务**——职责清晰，前端无重算负担。默认超时 `SERVER_IMAGE_TIMEOUT=15s`。

### 6. 多数据源统一抽象（`db.py` + `db/constant.py`）

`DB.get_db(ds.type)` 返回 `{prefix, suffix, connect_type}`，`connect_type` 分 `sqlalchemy`（走 `create_engine`）与原生驱动（dm/doris/starrocks/redshift/kingbase/hive/es 各自 `connect()`）。所有 `get_tables/get_fields/exec_sql` 都按这两条分支实现，`NullPool` 避免长连接泄漏。数据源密码 `aes_decrypt(configuration)` 解密，连接串里用户名密码 `urllib.parse.quote()`。`checkParams()`（L845）拦截 `illegalParams` JDBC 参数。

## 局限

1. **无 SQL 自愈重试循环**：`run_task()` 是单向串行，执行失败 `save_error` 后 `finally: self.finish()` 终止，**不回灌错误信息重写**。仅在下一次该 chat 的提问里，把 `last_execute_sql_error`（`llm.py:190`）塞进 `<error-msg>` 让 LLM"下次注意"——这是开环纠偏，不是闭环自愈。对照控制论 η₆（抗饱和），它缺少 `attempts` 有界重试边。
2. **流程串行、无并行节点**：select_datasource → schema → sql → permission → exec → chart → picture 全串行，延迟叠加；无法像 Graph 那样并行跑"SQL 生成 + 图表类型预测"。
3. **行权限依赖 LLM 改写**：虽有只读校验兜底，但权限条件本身由 LLM 重写 SQL 注入，理论上存在"LLM 误解 filter 导致漏权限"风险（待确认是否有后续断言校验 filter 是否被实际应用）。机械拼 WHERE 会更稳，但牺牲了多表/子查询场景的可读性——这是它做的取舍。
4. **`sqlglot` 方言覆盖不全**：`get_sqlglot_dialect()` 仅显式映射 mysql/tsql/hive，oracle/pg/ck 等用 `None`（默认方言），复杂方言 SQL 的 AST 解析可能误判或抛异常被 `except` 吞掉（`extract_tables_from_sql` L82 静默 `pass`），存在表名校验被绕过的边角风险。
5. **`is_normal_user = id != 1`**（`permission.py:76`）：用硬编码主键判定管理员，耦合死、多租户扩展性差。
6. **提示词极长**：`generate_rules` 含大量 XML 化 `<rule priority="critical">`，token 消耗高；强约束靠"AI 确认句"堆叠，属 prompt 工程的重量级方案。

## → Spring AI Alibaba Graph 构建启示

### 可借鉴

- **AST 级只读校验 + 危险函数黑名单**：Java 侧用 `jsqlparser`（或 ANTLR）复刻 `check_sql_read` 的三层结构——首词黑白名单 → 正则危险模式 → AST `Insert/Update/Delete/...` 类型断言 + 函数名黑名单。**这是 η₅ 先稳后优的硬门槛**，生产上线必做。SQLBot 的库特定黑名单字典（`DS_SPECIFIC_DANGEROUS_FUNCTIONS`）可直接照搬。
- **表名白名单不信 LLM**：Graph 里加一个 `TableNameGuardNode`，用 AST 解析真实表名与 RAG 召回集合做差集，拒绝越权表。SQLBot 的 `extract_tables_from_sql` 是极简范本。
- **行级权限改写**：可学其"系统变量（当前用户/租户）注入 WHERE"模型，但建议**改回机械拼接 + AST 断言 filter 已应用**，比纯 LLM 改写更稳；或用 Graph 的 `interruptBefore` 做 HITL 审核。
- **g2-ssr 图表微服务模式**：LLM 只产图表配置 JSON，渲染下沉独立服务，Spring 侧可用 ECharts/ AntV 服务端渲染或直接前端渲染，职责分离思路一致。
- **多数据源统一抽象**：`{prefix, suffix, connect_type}` + `NullPool` 的设计可迁移到 Spring 的 `DynamicDataSource` 路由。
- **RAG 三路召回 + 确认型 prompt**：术语库 / SQL 示例库 / 自定义提示词三路召回，配合"我已确认"协议，是提升精度的成熟工程方案。

### 可规避

- **无自愈循环的鲁棒性缺陷**：这是 SQLBot 最大的短板。Spring AI Alibaba Graph 应显式建循环边——`SqlValidateNode` 失败 → 错误信息回灌 `SqlGenerateNode` → `attempts++`，上限 N（η₆ 抗饱和），用 Graph 状态字段 `attempts` 可观测可调参。**不要**学它的"失败即终止"。
- **串行流水线**：Graph 天然支持并行/分支，可把"SQL 生成"与"图表类型预测"并行，或把"可行性预评估"做早退（η 信息效率）。
- **`sqlglot` 静默吞异常**：表名解析失败不能 `pass`，要当安全事件处理（拒绝执行 + 告警）。
- **硬编码 admin 判定**：用 Spring Security 的 `GrantedAuthority` 做角色判定，不要耦合主键。

## 相关

- 控制论综合分析：[NL2SQL_AGENT_CYBERNETICS.md](../dev/NL2SQL_AGENT_CYBERNETICS.md) §2.2 路线②显式多步 Graph / §4.3 决策三（安全护栏 = 先稳后优 η₅）/ 附录 A 速查表第 355 行 SQLBot 行。
