# 对比矩阵：问数 Agent 特性 × 项目

> 范围：当前 DataAgent（基准，Java/Spring AI Alibaba Graph）+ 8 个参考项目。
> 数据来源：各项目本地源码（见同目录单项目卡片）。**技术栈以源码实际为准**（已纠正既有认知，见 [README 勘误表](./README.md#-事实勘误源码级核对纠正既有认知)）。
> 控制论维度的解释见 [NL2SQL_AGENT_CYBERNETICS.md](../dev/NL2SQL_AGENT_CYBERNETICS.md)。

## 一、总览矩阵

| 项目 | 路线 | 技术栈（源码实际） | 编排形态 | 反馈/自愈 | Schema 策略 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **DataAgent**（当前） | ② | Java 17 / Spring AI Alibaba Graph | 声明式 `StateGraph` + 条件边 + `interruptBefore` | ✅ 多重显式闭环（抗饱和上限+早退） | 召回 top-K 表 + 知识库 |
| **QueryWeaver** | ② | Python/FastAPI + LiteLLM + React/TS | async generator + 手写 `if/yield` 分支 | ✅ **HealerAgent ×3 自愈**（attempts 一等字段） | **FalkorDB 图检索**（表/列双向量+FK 邻域+桥接表） |
| **SQLBot** | ② | Python/FastAPI + Vue 3 + g2-ssr(Node) | 多步串行流程 | ❌ 无自愈（开环纠偏） | RAG 三路召回 |
| **vanna** | ③ | Python（2.0 经典 RAG 在 `legacy/`） | 单步生成 + 训练反哺 | ❌ 无运行时自愈 | 三库（sql/ddl/docs）分库检索 |
| **pgai** | ④ | PG 扩展（pgvector+plpython3u）⚠️2026.02 停维 | DB 内 vectorizer worker 调度 | ✅ `explain` 校验回灌 | semantic catalog（事件触发器自维护） |
| text-to-sql-agent | ① | Python + LangChain | 单步开环 Agent | ❌ 隐式 | 全量 schema 塞上下文 |
| LangChain-SQL-可视化 | ① | Python + LangChain + Plotly | 单步开环 Agent | ❌ 隐式 | 全量 schema |
| langchain-sql-guide | ① | Python + LangChain | 单步开环 Agent | ❌ 隐式 | 全量 schema |
| postgresai | — | Node CLI + Python（**非 NL2SQL**） | — | — | — |

## 二、安全与权限（生产门槛）

| 项目 | SQL 安全护栏 | 表名信任 | 行级权限 | 破坏性操作 |
| :--- | :--- | :--- | :--- | :--- |
| **DataAgent** | 部分（行数限制等有界约束） | — | 待补 | HITL 二次确认 |
| **SQLBot** 🔴 | **AST 只读三层防御**（首词黑白名单+危险模式正则+sqlglot AST+库特定危险函数字典） | **白名单**（sqlglot 重解析真实表名，不信 LLM 自报） | ✅ **LLM 改写 row filter**（系统变量注入+防注入） | 只读校验拦截 |
| **QueryWeaver** | DML/DDL 七动词黑名单 + **注释穿透防绕过** | — | — | ✅ demo 图拒绝 + 二次确认 + DDL 后重建 schema |
| **vanna** | prompt 文本约束 | — | RLS（2.0 core 层，是否作用于生成 SQL 待确认） | — |
| **pgai** | `explain (verbose, format json)` 校验 | — | — | — |
| 路线①三例 | **仅 prompt 文本禁 DML**（可绕过） | 信 LLM | ❌ | ❌ |

> **关键差距**：当前 DataAgent 相对 SQLBot 缺 **AST 级只读护栏 + 表名白名单**，相对 vanna/SQLBot 缺 **行级权限改写**。这三项是生产多租户硬门槛（控制论 η₅，优先级高于精度）。

## 三、自适应与可观测

| 项目 | RAG 训练库（越用越准） | HITL 人在回路 | 可观测/Trace | 可视化 |
| :--- | :--- | :--- | :--- | :--- |
| **DataAgent** | ❌（最大可补强点） | ✅ `interruptBefore` 监督控制 | ✅ Langfuse 节点 trace | 前端流式报告 |
| **QueryWeaver** | ❌ | 二次确认 | 结构化 attempts | React 前端 |
| **SQLBot** | RAG 三路召回（静态） | ❌ | — | **g2-ssr 服务端渲染**（LLM 只产配置 JSON） |
| **vanna** 🔴 | ✅ **三库训练反哺** | — | — | 前端/notebook |
| **pgai** | — | — | — | — |
| text-to-sql-agent | ❌ | ❌ | ✅ **LangSmith trace**（唯一亮点） | — |
| LangChain-SQL-可视化 | ❌ | ❌ | 弱 | ✅ **Plotly 工具**（可视化即 agent tool） |
| langchain-sql-guide | ❌ | ❌ | ✅ **自定义 Callback** | — |

## 四、对 Spring AI Alibaba Graph 构建的取舍提炼

| 想要的能力 | 抄谁 | 抄什么（源码定位见各卡片） | 控制论依据 |
| :--- | :--- | :--- | :--- |
| SQL 只读护栏 | **SQLBot** | `check_sql_read()` AST 三层防御 + 表名白名单 | η₅ 先稳后优 |
| 自愈重试闭环 | **QueryWeaver** | `heal_and_execute` ×3，`attempts` 状态字段，循环内累积错误 | 域Ⅲ 反馈校正 + η₆ 抗饱和 |
| 外键关系召回 | **QueryWeaver** | FalkorDB 表/列双向量 + FK 邻域 + `allShortestPaths` 找桥接表 | η₄ 主导极点 |
| SQL 样例训练库 | **vanna** | 三库训练（question/sql/ddl/docs）反哺 | 域Ⅴ§16 在线辨识 |
| 行级权限 | **SQLBot** / **vanna** | LLM 改写 row filter + 系统变量注入 | η₅ |
| 图表渲染下沉 | **SQLBot** | g2-ssr：LLM 产配置 JSON，渲染独立服务 | 职责分离 |
| 全链路 trace | text-to-sql-agent / DataAgent | LangSmith / Langfuse 节点级 trace | η₇ 可验证 |

| 要规避的坑 | 谁踩过 | 教训 |
| :--- | :--- | :--- |
| 开环无自愈，执行失败即终止 | SQLBot / 路线① | 必须有显式重试闭环 |
| 全量 schema 塞上下文（信道过载） | 路线① | 召回 top-K，别硬塞 |
| 靠 prompt 文本禁 DML（可绕过） | 路线① / 部分 vanna | 必须 AST 级校验 |
| `max_tokens` 硬截断 DDL（丢随机片段） | vanna `legacy/` | 截断丢的是信号不是冗余 |
| 依赖已停维项目 | pgai（2026.02 停维） | 不纳入生产选型 |
| 手写 `if/yield` 大函数编排 | QueryWeaver | 用声明式 `StateGraph` 取代 |

## 五、置信度

| 结论 | 置信度 | 依据 |
| :--- | :--- | :--- |
| 各项目技术栈与编排形态 | **High** | 源码级核对（pyproject/扩展控制文件/入口文件） |
| SQLBot / QueryWeaver 安全与自愈机制 | **High** | 引用具体函数与行号 |
| vanna RLS 是否作用于生成 SQL | **Low** | 2.0 架构分裂，待确认（见 [vanna.md](./vanna.md)） |
| 「DataAgent 最该补三件：训练库/AST护栏/行权限」 | **Medium** | 控制论 η₅ 排序，具体优先级依业务场景 |
