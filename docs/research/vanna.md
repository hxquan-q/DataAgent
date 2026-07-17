# vanna

> 路线③ · RAG 训练式 NL2SQL · 本地路径 `/home/ubuntu/dev/reference/vanna`

## 一句话定位

Python 生态最流行的「RAG 训练式 NL2SQL」框架：把 DDL / 文档 / 历史 Question-SQL 三类知识向量化存入向量库，检索后拼成 prompt 让 LLM 生成 SQL；2.0 进一步叠加企业级 RLS 行权限、审计与流式 UI。核心通过**多继承 Mixin** 让"向量库"与"LLM"两个正交维度各自可插拔。

## 技术栈与版本（从 pyproject/setup 读）

- 包名 `vanna`，`pyproject.toml` 声明 `version = "2.0.2"`（注意：`src/vanna/__init__.py` 内 `__version__ = "0.1.0"` —— 2.0 重构后该常量未同步，**待确认**是否为重构遗留）。
- 构建后端 `flit_core`；`requires-python = ">=3.9"`，`ruff target-version = "py311"`。
- 核心依赖（`[project.dependencies]`）：`pydantic>=2.0.0`、`pandas`、`sqlparse`、`sqlalchemy`、`plotly`、`tabulate`、`httpx`、`requests`、`PyYAML`、`click`。
- 通过 `[project.optional-dependencies]` 做**可选附加（extras）矩阵**，按需安装：
  - LLM 维度：`openai` / `anthropic` / `gemini` / `mistralai` / `ollama` / `bedrock` / `qianfan` / `zhipuai` / `azureopenai` / `vllm` / `xinference-client` / `hf` / `marqo` / `cohere`。
  - 向量库维度：`chromadb>=1.1.0` / `pgvector`(=`langchain-postgres>=0.0.12`) / `qdrant` / `pinecone` / `milvus`(=`pymilvus[model]`) / `faiss-cpu|gpu` / `weaviate` / `opensearch` / `azuresearch` / `marqo`。
  - 数据库方言：`postgres`(psycopg2) / `mysql`(PyMySQL) / `clickhouse` / `bigquery` / `snowflake` / `duckdb` / `oracle`(oracledb) / `mssql`(pyodbc) / `hive` / `presto`。
  - 服务端：`flask` / `fastapi`。
- 入口脚本：`vanna = "vanna.servers.cli.server_runner:main"`。

## 核心架构

```mermaid
flowchart TD
    subgraph Train["训练阶段 train() / add_*（写入知识）"]
        T1[DDL CREATE 语句]:::store -->|add_ddl| V[(向量库 ddl collection)]
        T2[业务文档/列说明]:::store -->|add_documentation| V2[(向量库 documentation collection)]
        T3[历史 Question-SQL 对]:::store -->|add_question_sql| V3[(向量库 sql collection)]
        TP[TrainingPlan<br/>扫描 INFORMATION_SCHEMA 自动生成] --> T1
        TP --> T2
    end

    subgraph Gen["生成阶段 generate_sql()（检索+拼prompt）"]
        Q[自然语言问题] --> R1[get_related_ddl]
        Q --> R2[get_related_documentation]
        Q --> R3[get_similar_question_sql]
        R1 -->|向量近邻 n_results_ddl| V
        R2 -->|向量近邻 n_results_documentation| V2
        R3 -->|向量近邻 n_results_sql| V3
        R1 & R2 & R3 --> P[get_sql_prompt<br/>system+few-shot+用户问题]
        P -->|max_tokens 硬截断| LLM[(LLM submit_prompt)]
        LLM -->|extract_sql 正则| SQL[最终 SQL]
        SQL -.允许内省.->|allow_llm_to_see_data=True<br/>跑 intermediate_sql 再补一轮| LLM
    end

    SQL --> Run[run_sql 执行] --> DF[pandas DataFrame]
    DF -->|auto_train 默认开| T3
    DF --> Plot[generate_plotly_code 可视化]

    classDef store fill:#fef3c7,stroke:#d97706;
```

注意：图中的「三库」`sql / ddl / documentation` 是 ChromaDB 的三个独立 collection（见 `ChromaDB_VectorStore.__init__`），分别对应三类知识源；这是 vanna 区别于朴素 RAG 的关键特征。

## 关键源码路径

| 模块 / 文件 | 职责 |
| --- | --- |
| `src/vanna/legacy/base/base.py`（2125 行） | **RAG 训练式核心**。`VannaBase` 抽象基类，定义 `train` / `generate_sql` / `ask` / `get_sql_prompt` 等具体方法，以及向量库/LLM 的 `@abstractmethod` 契约。文件顶部 docstring 自带架构 mermaid 图。 |
| `src/vanna/legacy/chromadb/chromadb_vector.py` | `ChromaDB_VectorStore(VannaBase)`：三 collection 向量库的标杆实现，含 `add_*` / `get_related_*` / `remove_collection`。 |
| `src/vanna/legacy/{openai,anthropic,mistral,ollama,bedrock,ZhipuAI,qianfan,qianwen,google,hf,vllm,xinference,cohere,mock}/` | LLM Chat 侧实现（约 15 个），各自实现 `system_message/user_message/submit_prompt/generate_embedding`。 |
| `src/vanna/legacy/{pgvector,qdrant,pinecone,milvus,faiss,weaviate,opensearch,azuresearch,marqo,oracle,vannadb,google/bigquery_vector}/` | 向量库侧实现（13+ 个），均继承 `VannaBase`，可任选。 |
| `src/vanna/legacy/local.py` | `LocalContext_OpenAI(ChromaDB_VectorStore, OpenAI_Chat)` —— **多继承 Mixin 组合的范式样例**，证明两维度正交可插拔。 |
| `src/vanna/legacy/types.py` | `TrainingPlan` / `TrainingPlanItem`（`ITEM_TYPE_SQL/DDL/IS`）。 |
| `src/vanna/legacy/base/base.py` 中 `connect_to_{snowflake,postgres,mysql,clickhouse,oracle,bigquery,duckdb,mssql,presto,hive,sqlite}` | 数据库连接器，内部闭包绑定 `run_sql_*` 到 `self.run_sql`。 |
| `src/vanna/core/`、`src/vanna/agents/`、`src/vanna/integrations/` | **2.0 新增的通用 Agent 框架**（`Agent`/`Tool`/`LlmService`/`ConversationStore`/`workflow`/`evaluation`），与 NL2SQL RAG 解耦；`core/registry.py` 出现 RLS（行级安全）注释、`core/tool/models.py` 出现 `transform_args`（工具参数校验/拒绝钩子）。 |
| `src/vanna/servers/` | Flask / FastAPI 服务端，`<vanna-chat>` Web Component。 |

## 亮点设计（源码级）

1. **三库训练反哺（knowledge split）** —— `VannaBase.train()`（`base.py:1807`）按参数分流到 `add_question_sql` / `add_ddl` / `add_documentation` 三个抽象方法；`ChromaDB_VectorStore`（`chromadb_vector.py:45-59`）为它们分别建 `sql` / `ddl` / `documentation` 三个独立 collection，各自维护 `n_results_sql / n_results_ddl / n_results_documentation` 检索 top-k。三类知识用不同召回数，避免 DDL 噪声淹没 few-shot。

2. **向量库 / LLM 正交可插拔（多继承 Mixin）** —— `VannaBase` 用 `@abstractmethod` 把"存取知识"（`get_related_ddl` 等 6 个）和"调 LLM"（`submit_prompt` / `system_message` / `generate_embedding` 等）拆成两组契约；`local.py` 的 `class LocalContext_OpenAI(ChromaDB_VectorStore, OpenAI_Chat)` 证明两维度可任意组合（13+ 向量库 × 15+ LLM）。这是 Spring 侧"接口隔离 + Bean 注入"的 Python 原型。

3. **`get_sql_prompt` 模板化 few-shot（`base.py:586`）** —— 把 system prompt + DDL（`===Tables`）+ 文档（`===Additional Context`）+ few-shot（历史 question→assistant 对）+ 当前用户问题按 OpenAI chat message 格式拼装；默认 system prompt 内嵌 6 条 Response Guidelines（含「context 不足请解释」「需特定字符串值时先发 intermediate_sql」），是工程化 prompt 的范本。

4. **`allow_llm_to_see_data` 数据安全闸（`base.py:139-166`）** —— 当 LLM 回复含 `intermediate_sql`（要查列里的实际值才能生成最终 SQL）时，**默认拒绝**并提示用户显式开 `allow_llm_to_see_data=True`；开启后才执行 intermediate SQL、把结果 `to_markdown()` 回灌 doc_list 再跑第二轮。把"LLM 是否可见真实数据"做成显式开关，是 NL2SQL 隐私边界的关键设计。

5. **`max_tokens` 硬截断防炸 prompt（`base.py:532-584`）** —— `add_ddl_to_prompt` / `add_documentation_to_prompt` / `add_sql_to_prompt` 用 `str_to_approx_token_count`（`len/4` 粗估）逐条累加，超过 `self.max_tokens`（默认 14000）就丢弃后续 DDL/文档。简单但有界。

6. **`get_training_plan_generic` 自动冷启动（`base.py:1882`）** —— 扫描 `INFORMATION_SCHEMA.COLUMNS`，按 `database.schema.table` 分组，把每张表的列说明拼成 markdown 文档塞进 `TrainingPlan`（`ITEM_TYPE_IS`）；Snowflake 版（`base.py:1942`）还会捞 `query_history` 历史查询当训练 SQL。让"接上库就能训"。

7. **`ask()` 在线自适应闭环（`base.py:1692`）** —— 生成 SQL → `run_sql` → 若 `len(df)>0 and auto_train` 则 `add_question_sql` 自动回灌。这正是"在线辨识/自适应"的闭环实现：每成功一问就强化知识库。

8. **2.0 叠加企业级治理（`core/`）** —— `core/registry.py:123` 注释提到「Applying row-level security (RLS) to SQL queries」，`core/tool/models.py` 的 `transform_args`（`registry.py:113`）在工具执行前做参数校验/拒绝；配合 `core/audit` / `core/observability` / `core/recovery` / `core/user` 形成 user-aware 的生产级 NL2SQL。（2.0 这层为通用 Agent 框架，与 legacy RAG 解耦——**待确认** RLS 是否已实际作用于生成的 SQL。）

## 局限

- **大库 DDL 超长**：靠 `max_tokens`（默认 14000）硬截断，且 token 估计是 `len/4` 粗略值，不区分模型 tokenizer；大宽表/多表场景下 DDL 会被整条丢弃而非智能压缩或按相关列裁剪。
- **冷启动需人工标注**：`get_training_plan_generic` 只能从 `INFORMATION_SCHEMA` 自动生列说明，高质量 Question-SQL 对仍需人工喂；纯 schema 召回效果有限。
- **召回相关性无重排**：`get_related_*` 直接用向量库的 `n_results` 近邻，无 cross-encoder rerank、无 BM25 混合检索；question 与 DDL 语义距离本身偏弱。
- **`extract_sql` 依赖正则**（`base.py:170`）：对非标准模型输出（多余解释、非 markdown 包裹）鲁棒性有限。
- **2.0 重构带来版本混乱**：`pyproject` 标 2.0.2 而 `__init__` 标 0.1.0；经典 NL2SQL 核心被整体移入 `legacy/`，文档/示例可能滞后——使用时需明确走 `legacy` 路径还是新 `core/` Agent 路径。
- **无 SQL 执行错误自动修复闭环**：`generate_sql` 单轮/双轮（intermediate），不包含「执行报错→反馈 LLM 重写」的自我纠错循环（该能力属于路线④ Agent 式，vanna 路线③刻意保持轻量）。

## → Spring AI Alibaba Graph 构建启示

- **可借鉴**
  - 三库训练反哺：在 Spring 侧用三张向量表 / 三个 VectorStore bean（`ddlStore` / `docStore` / `sqlExampleStore`）分别召回，配独立 top-k，比单一混合召回更可控。
  - 向量库可插拔抽象：照 `VannaBase` 的 `@abstractmethod` 契约，用 Java interface（`KnowledgeStore` / `ChatModelAdapter`）+ Spring `@Conditional`/Starter 自动装配，实现 PgVector / Milvus / Qdrant / Redis 任选。
  - `allow_llm_to_see_data` 显式数据闸：移植为 Graph 节点上的策略点（`allowDataIntrospection`），在执行 intermediate SQL 前做权限/审计拦截。
  - `get_training_plan_generic` 自动建表说明：用 `INFORMATION_SCHEMA` 扫描 + Markdown 拼装做冷启动种子，降低首次接入成本。
  - RLS 行权限 + `transform_args` 参数闸：2.0 的 `core/registry.py` / `core/tool/models.py` 思路直接映射到 Spring AI 的 `ToolCallback` 前置校验与数据权限切面。
  - Mixin 正交组合思路 → Spring 里即"两个独立 AutoConfiguration"（向量库 Starter、模型 Starter），用户按需拼装。
- **可规避**
  - 不要靠 `len/4` 粗估 + `max_tokens` 硬截断 DDL：改用真实 tokenizer 计数 + 按列相关度裁剪 / DDL 摘要化 / 分表策略。
  - 召回后应加 cross-encoder rerank 与 BM25 混合检索，弥补纯向量近邻的弱相关性。
  - 训练数据治理：`ask()` 默认 `auto_train=True` 会把每条成功 SQL 自动入库，存在"脏 SQL 污染知识库"风险——需加人工审核/置信度门槛/可回滚（`remove_training_data`）。
  - 不要把 NL2SQL 核心塞进 `legacy` 包然后另起通用 Agent 框架导致版本/文档割裂；保持 NL2SQL 作为一等公民的稳定 API。

## 相关

- 控制论综合分析：[NL2SQL_AGENT_CYBERNETICS.md](../dev/NL2SQL_AGENT_CYBERNETICS.md) §2.3
