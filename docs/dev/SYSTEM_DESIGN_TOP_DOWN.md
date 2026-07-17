中文 | English (TODO)

# DataAgent 系统设计：从顶层目标到代码落地（图解版）

> 本文与 [`ENGINEERING_SYSTEM_THINKING.md`](./ENGINEERING_SYSTEM_THINKING.md) 配套：
>
> - 那篇讲**怎么用工程化思维学项目**（心智、路线图、改代码清单）
> - 本文讲**系统怎么被设计出来、又怎么落到代码**（顶层 → 分层 → 工作流 → 节点 → 反馈 → 前端）
>
> 讲解主线采用工程控制论：**系统建模 → 系统分析 → 系统设计 → 系统综合（实现）**。
> 全文用 **Mermaid 图**把抽象概念画出来，方便新手“先看图、再对代码”。

---

## 目录

1. [顶层：系统要解决什么问题](#1-顶层系统要解决什么问题)
2. [分层：物理模块与逻辑分层](#2-分层物理模块与逻辑分层)
3. [控制模型：把 StateGraph 看成控制系统](#3-控制模型把-stategraph-看成控制系统)
4. [主链路：一次请求从 UI 到节点](#4-主链路一次请求从-ui-到节点)
5. [状态空间：共享状态如何设计](#5-状态空间共享状态如何设计)
6. [节点与调度器：环节 + 比较器](#6-节点与调度器环节--比较器)
7. [反馈与稳定性：重试、校验、人工回路](#7-反馈与稳定性重试校验人工回路)
8. [边界系统：模型、库、向量、Python、可观测](#8-边界系统模型库向量python可观测)
9. [前端落地：SSE 与流式体验](#9-前端落地sse-与流式体验)
10. [从设计决策到代码位置速查](#10-从设计决策到代码位置速查)
11. [二次开发决策树](#11-二次开发决策树)
12. [一张总图 + 阅读顺序](#12-一张总图--阅读顺序)

---

## 1. 顶层：系统要解决什么问题

### 1.1 一句话定义

> **DataAgent = 自然语言 → 可执行计划 → 查询/分析 → 可读报告** 的闭环系统。

它不是单纯的 Text-to-SQL 工具，而是一个带反馈的“数据分析控制系统”。

### 1.2 为什么需要这么复杂

```mermaid
flowchart LR
  subgraph Pain[业务痛点]
    A[业务术语 ≠ 表字段]
    B[问题往往多步]
    C[SQL 结果还不够]
  end

  subgraph Need[工程对策]
    A --> A1[RAG / 语义模型 / 业务知识]
    B --> B1[Planner + 多步执行]
    C --> C1[Python 分析 + 报告]
  end

  classDef pain fill:#FFF4E6,stroke:#D97706,color:#1F2937;
  classDef need fill:#ECFDF3,stroke:#16A34A,color:#1F2937;
  class A,B,C pain;
  class A1,B1,C1 need;
```

### 1.3 黑箱模型（输入 / 输出 / 扰动）

先把系统当成黑箱，只关心边界上的信号：

```mermaid
flowchart TB
  U[输入 u<br/>query / agentId / threadId<br/>humanFeedback / nl2sqlOnly]
  D[外部扰动 d<br/>LLM 不稳定 / DB 方言差异<br/>向量召回噪声]
  SYS[DataAgent 系统<br/>StateGraph + 管理后台 + 前端]
  Y[输出 y<br/>SSE 流式节点输出<br/>SQL / Python 结果 / 报告]
  M[可观测量<br/>Langfuse span<br/>状态 key / 重试计数 / 校验原因]

  U --> SYS
  D -.-> SYS
  SYS --> Y
  SYS --> M

  classDef in fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  classDef out fill:#ECFDF3,stroke:#16A34A,color:#1F2937;
  classDef dist fill:#FEE2E2,stroke:#EF4444,color:#1F2937;
  classDef sys fill:#F3E8FF,stroke:#9333EA,color:#1F2937;
  classDef meas fill:#FEF3C7,stroke:#F59E0B,color:#1F2937;
  class U in; class Y out; class D dist; class SYS sys; class M meas;
```

工程控制论对照：

| 控制论角色 | 在本项目中 |
| :--- | :--- |
| 被控对象 | StateGraph 工作流 |
| 控制器 | Dispatcher + 校验节点 + 人工反馈 |
| 传感器 | SQL 结果、语义一致性、可行性、Langfuse |
| 执行器 | LLM 调用、SQL 执行器、Python 执行器 |

### 1.4 顶层质量指标（先稳后优）

```mermaid
mindmap
  root((可收敛的正确率))
    稳定性
      重试有上限
      失败可 END
    可解释性
      每步 SSE 可见
    可干预性
      Human-in-the-loop
    可替换性
      模型/库/向量/Python 可换
    可观测性
      Langfuse 全链路
```

---

## 2. 分层：物理模块与逻辑分层

### 2.1 仓库物理分解

```mermaid
flowchart TB
  ROOT[DataAgent 仓库]
  ROOT --> FE[data-agent-frontend-nuxt<br/>操作台 / 仪表盘]
  ROOT --> BE[data-agent-management<br/>控制计算机]
  ROOT --> DOCK[docker-file<br/>执行机构与环境]
  ROOT --> DOCS[docs<br/>说明书]
  ROOT --> CI[CI<br/>出厂检验]

  classDef root fill:#F3E8FF,stroke:#9333EA,color:#1F2937;
  classDef m fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  class ROOT root; class FE,BE,DOCK,DOCS,CI m;
```

| 目录 | 角色 | 控制论类比 |
| :--- | :--- | :--- |
| `data-agent-frontend-nuxt/` | 人机接口、流式展示 | 操作台 |
| `data-agent-management/` | 工作流、API、服务 | 控制计算机 |
| `docker-file/` | 容器与依赖编排 | 机房 / 执行机构 |
| `docs/` | 知识沉淀 | 说明书 |
| `CI/` | checkstyle 等门禁 | 出厂检验 |

### 2.2 后端逻辑分层（由外到内）

```mermaid
flowchart TB
  L1[Access 接入层<br/>GraphController / REST + SSE]
  L2[Orchestration 编排层<br/>GraphServiceImpl / MultiTurnContext / StreamContext]
  L3[Workflow 控制律层<br/>StateGraph + Node + Dispatcher]
  L4[Domain 领域服务层<br/>Llm / Vector / Datasource / CodePool]
  L5[Infrastructure 基础设施层<br/>MyBatis / JDBC / VectorStore / Docker]

  L1 --> L2 --> L3 --> L4 --> L5

  classDef a fill:#DBEAFE,stroke:#2563EB,color:#1F2937;
  classDef b fill:#D1FAE5,stroke:#059669,color:#1F2937;
  classDef c fill:#FDE68A,stroke:#D97706,color:#1F2937;
  classDef d fill:#FCE7F3,stroke:#DB2777,color:#1F2937;
  classDef e fill:#E5E7EB,stroke:#6B7280,color:#1F2937;
  class L1 a; class L2 b; class L3 c; class L4 d; class L5 e;
```

**原则：上层只依赖下层接口。**  
换 Qwen/Deepseek、换 PGVector/ES，工作流尽量不动 —— 这就是对“模型失配”的鲁棒性。

### 2.3 包结构映射

后端主包：`com.alibaba.cloud.ai.dataagent`

```mermaid
flowchart LR
  subgraph Access
    controller
  end
  subgraph Orchestration
    service_graph[service/graph]
  end
  subgraph Workflow
    node[workflow/node]
    dispatcher[workflow/dispatcher]
    config
  end
  subgraph Domain
    service_other[service/*]
    prompt
    properties
  end
  subgraph Infra
    connector[connector/impls]
    mapper
    entity
  end

  controller --> service_graph
  service_graph --> config
  config --> node
  config --> dispatcher
  node --> service_other
  node --> prompt
  service_other --> connector
  service_other --> mapper
```

| 包 | 职责 |
| :--- | :--- |
| `controller` | HTTP/SSE 入口 |
| `service/graph` | 图驱动、流上下文、多轮上下文 |
| `workflow/node` | 处理环节（NodeAction） |
| `workflow/dispatcher` | 条件路由（EdgeAction） |
| `config` | Bean 装配、StateGraph 接线 |
| `service/*` | LLM、向量、数据源、Python、MCP |
| `connector/impls/*` | 多数据库方言 |
| `properties` | 可调参数（重试、阈值） |
| `prompt` | Prompt 模板 |

---

## 3. 控制模型：把 StateGraph 看成控制系统

### 3.1 为什么用图，而不是长函数

```mermaid
flowchart LR
  subgraph Bad[长函数 if-else]
    B1[数据流和控制流缠在一起]
    B2[重试/中断难测]
    B3[改一步牵全局]
  end

  subgraph Good[StateGraph]
    G1[Node = 做什么]
    G2[Dispatcher = 何时做]
    G3[State = 传什么]
  end

  Bad -->|解耦| Good

  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#1F2937;
  classDef good fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  class B1,B2,B3 bad; class G1,G2,G3 good;
```

| 概念 | 代码角色 | 控制论角色 |
| :--- | :--- | :--- |
| Node | `*Node implements NodeAction` | 环节 / 执行机构 |
| Dispatcher | `*Dispatcher implements EdgeAction` | 比较器 / 切换逻辑 |
| OverAllState | 共享状态字典 | 状态空间 x |
| KeyStrategy | 状态合并策略 | 状态更新律 |
| CompiledGraph | 可运行图 | 闭环系统 |

### 3.2 图在哪里定义与编译

```mermaid
flowchart LR
  CFG[DataAgentConfiguration<br/>nl2sqlGraph]
  CFG --> K[注册 40+ 状态 key]
  CFG --> N[addNode 16 个节点]
  CFG --> E[addEdge / addConditionalEdges]
  CFG --> SG[StateGraph Bean]
  SG --> GS[GraphServiceImpl 构造]
  GS --> CG[CompiledGraph<br/>interruptBefore HumanFeedback]

  classDef cfg fill:#DBEAFE,stroke:#2563EB,color:#1F2937;
  classDef run fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  class CFG,K,N,E,SG cfg; class GS,CG run;
```

关键代码：

- 装配：`config/DataAgentConfiguration.java`
- 编译：`service/graph/GraphServiceImpl.java`

```java
this.compiledGraph = stateGraph.compile(
    CompileConfig.builder().interruptBefore(HUMAN_FEEDBACK_NODE).build()
);
```

### 3.3 16 节点功能分区（总览图）

```mermaid
flowchart TD
  START([START]) --> Intent[IntentRecognition]
  Intent -->|闲聊/无关| END1([END])
  Intent -->|需分析| Evidence[EvidenceRecall]
  Evidence --> Enhance[QueryEnhance]
  Enhance --> Schema[SchemaRecall]
  Schema --> Relation[TableRelation]
  Relation -->|retry| Relation
  Relation --> Feasible[FeasibilityAssessment]
  Feasible -->|不可行| END2([END])
  Feasible -->|可行| Planner[Planner]
  Planner --> PlanExec[PlanExecutor]
  PlanExec -->|校验失败| Planner
  PlanExec -->|人工审核| Human[HumanFeedback]
  Human -->|reject| Planner
  Human -->|approve| PlanExec
  PlanExec -->|SQL 步| SQLGen[SqlGenerate]
  SQLGen --> Sem[SemanticConsistency]
  Sem -->|不一致| SQLGen
  Sem --> SQLExec[SqlExecute]
  SQLExec -->|失败| SQLGen
  SQLExec -->|成功| PlanExec
  PlanExec -->|Python 步| PyGen[PythonGenerate]
  PyGen --> PyExec[PythonExecute]
  PyExec -->|失败| PyGen
  PyExec --> PyAn[PythonAnalyze]
  PyAn --> PlanExec
  PlanExec -->|报告步| Report[ReportGenerator]
  Report --> END3([END])

  classDef sense fill:#E0F7FA,stroke:#06B6D4,color:#1F2937;
  classDef plan fill:#ECFDF3,stroke:#16A34A,color:#1F2937;
  classDef exec fill:#FFF7ED,stroke:#F59E0B,color:#1F2937;
  classDef out fill:#DBEAFE,stroke:#2563EB,color:#1F2937;
  classDef term fill:#E5E7EB,stroke:#9CA3AF,color:#1F2937;
  class Intent,Evidence,Enhance,Schema,Relation,Feasible sense;
  class Planner,PlanExec,Human plan;
  class SQLGen,Sem,SQLExec,PyGen,PyExec,PyAn exec;
  class Report out;
  class START,END1,END2,END3 term;
```

四段结构 = **先稳后优**：

1. 感知：能不能做、需要什么证据  
2. 规划：怎么做（可人工把关）  
3. 执行：局部闭环校正  
4. 输出：报告收口  

---

## 4. 主链路：一次请求从 UI 到节点

### 4.1 端到端时序图

```mermaid
sequenceDiagram
  autonumber
  actor U as 用户
  participant FE as 前端 EventSource<br/>services/graph
  participant CTL as GraphController
  participant GS as GraphServiceImpl
  participant CTX as MultiTurnContext
  participant G as CompiledGraph
  participant N as Node / Dispatcher

  U->>FE: 输入问题
  FE->>CTL: GET /api/stream/search
  CTL->>CTL: 创建 SSE Sink + GraphRequest
  CTL->>GS: graphStreamProcess(sink, request)
  alt 无 humanFeedbackContent
    GS->>CTX: buildContext / beginTurn
    GS->>G: stream(初始状态, threadId)
  else 有人工反馈
    GS->>G: updateState + stream(resume)
  end
  loop 每个节点
    G->>N: apply(state) / route
    N-->>G: 写回状态 / 下一节点
    G-->>CTL: NodeOutput
    CTL-->>FE: SSE GraphNodeResponse
    FE-->>U: 实时展示
  end
  G-->>CTL: complete
  CTL-->>FE: complete 事件
```

### 4.2 调用链（文件级）

```mermaid
flowchart LR
  A[app/services/graph/index.ts<br/>EventSource] --> B[GraphController.java<br/>/stream/search]
  B --> C[GraphServiceImpl.java<br/>graphStreamProcess]
  C --> D{有反馈内容?}
  D -->|否| E[handleNewProcess]
  D -->|是| F[handleHumanFeedback]
  E --> G[compiledGraph.stream]
  F --> H[updateState + stream resume]
  G --> I[Nodes + Dispatchers]
  H --> I
  I --> B

  classDef fe fill:#FFF4E6,stroke:#D97706,color:#1F2937;
  classDef be fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  classDef wf fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  class A fe; class B,C,E,F be; class G,H,I wf;
```

### 4.3 初始状态注入

`handleNewProcess` 写入图的初始 Map：

```mermaid
flowchart TB
  REQ[GraphRequest] --> S0[初始 OverAllState]
  S0 --> K1[INPUT_KEY = query]
  S0 --> K2[AGENT_ID]
  S0 --> K3[IS_ONLY_NL2SQL]
  S0 --> K4[HUMAN_REVIEW_ENABLED]
  S0 --> K5[MULTI_TURN_CONTEXT]
  S0 --> K6[TRACE_THREAD_ID]

  classDef s fill:#F3E8FF,stroke:#9333EA,color:#1F2937;
  classDef k fill:#FEF3C7,stroke:#F59E0B,color:#1F2937;
  class REQ,S0 s; class K1,K2,K3,K4,K5,K6 k;
```

之后每个节点只读自己需要的 key，再写回结果 key。

---

## 5. 状态空间：共享状态如何设计

### 5.1 节点不直接调用，只读写状态

```mermaid
flowchart LR
  N1[Node A] -->|write keyX| ST[(OverAllState)]
  N2[Node B] -->|read keyX| ST
  N2 -->|write keyY| ST
  D[Dispatcher] -->|read 控制 key| ST
  D -->|选择下一节点| N3[Node C]

  classDef n fill:#DBEAFE,stroke:#2563EB,color:#1F2937;
  classDef s fill:#FEF3C7,stroke:#F59E0B,color:#1F2937;
  classDef d fill:#FCE7F3,stroke:#DB2777,color:#1F2937;
  class N1,N2,N3 n; class ST s; class D d;
```

好处：可单测、可重排、可恢复（HITL 中断后从快照继续）。

### 5.2 Key 分类图

```mermaid
mindmap
  root((OverAllState keys))
    输入类
      INPUT_KEY
      AGENT_ID
      MULTI_TURN_CONTEXT
    召回类
      EVIDENCE
      TABLE_DOCUMENTS...
    规划类
      PLANNER_NODE_OUTPUT
      PLAN_CURRENT_STEP
      PLAN_NEXT_NODE
    执行类
      SQL_GENERATE_OUTPUT
      SQL_EXECUTE_NODE_OUTPUT
    Python类
      PYTHON_GENERATE_NODE_OUTPUT
      PYTHON_IS_SUCCESS
    控制类 抗饱和
      SQL_GENERATE_COUNT
      PLAN_REPAIR_COUNT
      PYTHON_TRIES_COUNT
    人机类
      HUMAN_REVIEW_ENABLED
      HUMAN_FEEDBACK_DATA
    输出类
      RESULT
```

合并策略几乎全是 `KeyStrategy.REPLACE`（后写覆盖前写）。

> **二次开发铁律**：新 key 必须在 `DataAgentConfiguration` 注册，并全局搜索读写点。

---

## 6. 节点与调度器：环节 + 比较器

### 6.1 节点标准模板（IntentRecognition）

```mermaid
flowchart TD
  A[读 state: INPUT / MULTI_TURN] --> B[PromptHelper 拼 prompt]
  B --> C[LlmService 流式调用]
  C --> D[解析 JSON → DTO]
  D --> E[写回 INTENT_RECOGNITION_NODE_OUTPUT]

  classDef step fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  class A,B,C,D,E step;
```

文件：`workflow/node/IntentRecognitionNode.java`

### 6.2 调度器标准模板（SqlGenerateDispatcher）

```mermaid
flowchart TD
  S[读 SQL_GENERATE_OUTPUT] --> Q{输出有效?}
  Q -->|空| C{count < maxRetry?}
  C -->|是| R1[回 SQL_GENERATE_NODE]
  C -->|否| R2[END 体面失败]
  Q -->|END 标志| R2
  Q -->|成功| R3[SEMANTIC_CONSISTENCY_NODE]

  classDef d fill:#F3F4F6,stroke:#6B7280,color:#1F2937;
  classDef ok fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#1F2937;
  class S,Q,C d; class R1,R3 ok; class R2 bad;
```

控制论关键词：误差检测 → 反馈 → **抗饱和（上限）**。

### 6.3 PlanExecutor = 主控制器

```mermaid
flowchart TD
  P[解析 Plan JSON] --> V{结构/步骤校验}
  V -->|失败| F[PLAN_VALIDATION_STATUS=false<br/>回 Planner 修复]
  V -->|通过| H{人工审核开启?}
  H -->|是| HF[下一节点 = HUMAN_FEEDBACK]
  H -->|否| Done{步骤已完成?}
  Done -->|是| Rep[Report 或 END]
  Done -->|否| Tool{toolToUse}
  Tool --> SQL[SQL_GENERATE]
  Tool --> PY[PYTHON_GENERATE]
  Tool --> RPT[REPORT_GENERATOR]

  classDef main fill:#ECFDF3,stroke:#16A34A,color:#1F2937;
  class P,V,H,Done,Tool main;
```

配套：`PlanExecutorDispatcher` 中 `MAX_REPAIR_ATTEMPTS = 2`。

### 6.4 控制律全集（条件边一览）

```mermaid
flowchart LR
  subgraph Gates[Dispatcher 决策点]
    I[Intent]
    T[TableRelation]
    F[Feasibility]
    PE[PlanExecutor]
    HF[HumanFeedback]
    SG[SqlGenerate]
    SC[Semantic]
    SE[SqlExecute]
    PY[PythonExecute]
  end

  I -->|yes/no| E1[Evidence / END]
  T -->|ok/retry/fail| E2[Feasibility / self / END]
  F -->|yes/no| E3[Planner / END]
  PE -->|route| E4[Planner/SQL/Python/Report/Human/END]
  HF -->|approve/reject| E5[PlanExecutor / Planner]
  SG -->|ok/retry| E6[Semantic / self / END]
  SC -->|ok/fail| E7[SqlExecute / SqlGenerate]
  SE -->|ok/fail| E8[PlanExecutor / SqlGenerate]
  PY -->|ok/retry| E9[Analyze / Generate / END]
```

| 源节点 | Dispatcher | 可能去向 |
| :--- | :--- | :--- |
| IntentRecognition | IntentRecognitionDispatcher | Evidence / END |
| TableRelation | TableRelationDispatcher | Feasibility / END / 自环 |
| Feasibility | FeasibilityAssessmentDispatcher | Planner / END |
| PlanExecutor | PlanExecutorDispatcher | Planner / SQL / Python / Report / Human / END |
| HumanFeedback | HumanFeedbackDispatcher | Planner / PlanExecutor / Human / END |
| SqlGenerate | SqlGenerateDispatcher | SqlGenerate / Semantic / END |
| SemanticConsistency | SemanticConsistenceDispatcher | SqlGenerate / SqlExecute |
| SqlExecute | SQLExecutorDispatcher | SqlGenerate / PlanExecutor |
| PythonExecute | PythonExecutorDispatcher | Analyze / Generate / END |

---

## 7. 反馈与稳定性：重试、校验、人工回路

### 7.1 开环 vs 闭环

```mermaid
flowchart LR
  subgraph Open[开环：不可靠]
    O1[生成 SQL] --> O2[直接相信]
  end

  subgraph Closed[闭环：可收敛]
    C1[生成 SQL] --> C2[语义校验]
    C2 --> C3[执行 SQL]
    C2 -->|失败| C1
    C3 -->|失败| C1
    C1 -.->|count 封顶| END([END])
  end

  Open -->|工程化改造| Closed

  classDef bad fill:#FEE2E2,stroke:#EF4444,color:#1F2937;
  classDef good fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  class O1,O2 bad; class C1,C2,C3,END good;
```

### 7.2 多回路总览

```mermaid
flowchart TB
  subgraph L1[局部回路]
    TR[TableRelation 自环重试]
    SQL[SQL 生成-校验-执行回修]
    PY[Python 生成-执行回修]
  end

  subgraph L2[规划回路]
    PL[Plan 校验失败 → Planner]
    PL --- CAP1[MAX_REPAIR_ATTEMPTS=2]
  end

  subgraph L3[监督回路 HITL]
    H[interruptBefore HumanFeedback]
    H --> A[approve → 继续执行]
    H --> R[reject → 回 Planner]
  end

  classDef a fill:#E0F7FA,stroke:#06B6D4,color:#1F2937;
  classDef b fill:#ECFDF3,stroke:#16A34A,color:#1F2937;
  classDef c fill:#FFF7ED,stroke:#F59E0B,color:#1F2937;
  class TR,SQL,PY a; class PL,CAP1 b; class H,A,R c;
```

| 回路 | 触发 | 回到 | 上限 |
| :--- | :--- | :--- | :--- |
| 表关系修复 | 关系失败 | TableRelation | 重试计数 |
| 计划修复 | 校验失败 | Planner | `MAX_REPAIR_ATTEMPTS=2` |
| SQL 修复 | 生成/语义/执行失败 | SqlGenerate | `max-sql-retry-count` |
| Python 修复 | 执行失败 | PythonGenerate | code-executor 配置 |
| 人工反馈 | 需审核 | 暂停 → approve/reject | reject 回 Planner |

### 7.3 HITL 暂停 / 恢复时序

```mermaid
sequenceDiagram
  participant GS as GraphServiceImpl
  participant G as CompiledGraph
  participant HF as HumanFeedbackNode
  participant FE as 前端
  participant U as 用户

  Note over G: compile 时 interruptBefore(HUMAN_FEEDBACK)
  GS->>G: stream(新请求)
  G->>HF: 计划通过且开启审核
  HF-->>FE: 等待反馈（流暂停）
  U->>FE: approve / reject + 意见
  FE->>GS: 再次请求带 humanFeedbackContent
  GS->>G: updateState(HUMAN_FEEDBACK_DATA)
  GS->>G: stream(null, resumeConfig)
  G-->>FE: 继续执行或回 Planner
```

### 7.4 体面失败

重试耗尽 → 走 END → SSE 告知前端。  
**可预期的失败** 比 “假成功 / 死循环” 更安全。

---

## 8. 边界系统：模型、库、向量、Python、可观测

边界外不可完全控，策略是 **接口隔离 + 可替换实现**：

```mermaid
flowchart TB
  WF[Workflow 层<br/>Node / Dispatcher]
  WF --> LLM[LlmService / AiModelRegistry]
  WF --> VS[AgentVectorStoreService]
  WF --> DS[Datasource / connector]
  WF --> PY[CodePoolExecutorService]
  WF --> OBS[LangfuseService]

  LLM --> L1[Qwen / Deepseek / OpenAI 兼容]
  VS --> V1[Simple / PGVector / ES / Milvus]
  DS --> D1[MySQL / PG / Oracle / ...]
  PY --> P1[Local / Docker]
  OBS --> O1[Langfuse Cloud / 自部署]

  classDef core fill:#F3E8FF,stroke:#9333EA,color:#1F2937;
  classDef bound fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  classDef impl fill:#F3F4F6,stroke:#6B7280,color:#1F2937;
  class WF core; class LLM,VS,DS,PY,OBS bound; class L1,V1,D1,P1,O1 impl;
```

| 边界 | 抽象 | 默认可替换 |
| :--- | :--- | :--- |
| 大模型 | `LlmService` + `AiModelRegistry` | 任意 OpenAI 兼容 |
| 数据源 | `connector/impls/*` | MySQL/PG/Oracle/... |
| 向量库 | `VectorStore` | Simple → PGVector/ES/Milvus |
| Python | `CodePoolExecutorService` | Local / Docker |
| 可观测 | `LangfuseService` | Cloud / 自部署 |

---

## 9. 前端落地：SSE 与流式体验

### 9.1 为什么必须流式

```mermaid
flowchart LR
  subgraph Block[阻塞 HTTP]
    B1[等 30 秒空白页] --> B2[体验差]
  end
  subgraph Stream[SSE 流式]
    S1[边生成边推] --> S2[边收边渲染]
  end
  Block -->|工程补偿| Stream
```

### 9.2 前端分层

```mermaid
flowchart TB
  P[pages 路由页面] --> C[components/chat UI]
  C --> S[stores/chat 会话状态]
  C --> SV[services/graph EventSource]
  C --> CP[composables/useTypewriter 渲染平滑]
  SV --> API[后端 /api/stream/search]

  classDef ui fill:#FFF4E6,stroke:#D97706,color:#1F2937;
  classDef data fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  class P,C ui; class S,SV,CP,API data;
```

| 层 | 路径 | 职责 |
| :--- | :--- | :--- |
| pages | `app/pages/**` | 路由 |
| components | `app/components/chat/**` | 聊天/报告 UI |
| services | `app/services/graph/index.ts` | EventSource |
| stores | `app/stores/chat.ts` | 会话状态、节流 |
| composables | `app/composables/useTypewriter.ts` | 解耦到达率与渲染率 |

---

## 10. 从设计决策到代码位置速查

```mermaid
flowchart LR
  D1[用 StateGraph] --> C1[DataAgentConfiguration]
  D2[节点只写状态] --> C2[workflow/node/*]
  D3[Dispatcher 路由] --> C3[workflow/dispatcher/*]
  D4[重试有上限] --> C4[SqlGenerateDispatcher<br/>PlanExecutorDispatcher]
  D5[HITL 中断] --> C5[GraphServiceImpl compile]
  D6[SSE 可观察] --> C6[GraphController + EventSource]
  D7[边界可替换] --> C7[connector / vectorstore / code]
```

| 设计决策 | 为什么 | 落到哪里 |
| :--- | :--- | :--- |
| StateGraph | 解耦数据流/控制流 | `DataAgentConfiguration.nl2sqlGraph` |
| 节点只写状态 | 可测可恢复 | `workflow/node/*` |
| Dispatcher 路由 | 控制律可审计 | `workflow/dispatcher/*` |
| 重试上限 | 抗饱和 | Sql/Plan/Python Dispatcher + properties |
| 先可行性后规划 | 少烧 token | `FeasibilityAssessmentNode` |
| 语义一致性 | 抗幻觉 | `SemanticConsistencyNode` |
| interruptBefore | 监督控制 | `GraphServiceImpl` |
| SSE | 长任务可观察 | `GraphController` + 前端 |
| 接口化依赖 | 边界鲁棒 | connector / vectorstore / aimodelconfig |

---

## 11. 二次开发决策树

```mermaid
flowchart TD
  Q1{改动影响什么?}
  Q1 -->|只 UI 展示| FE[frontend components/services]
  Q1 -->|只路由条件| DISP[某个 Dispatcher]
  Q1 -->|只某步算法| NODE[对应 Node + Prompt]
  Q1 -->|换外部系统| BOUND[connector / vector / model]
  Q1 -->|改全局流程| CFG[DataAgentConfiguration<br/>中高风险]

  CFG --> CHK{会动状态 key?}
  CHK -->|是| HIGH[高风险：全局搜索读写点]
  CHK -->|否| MID[中风险：补边 + 单测 + 端到端]

  FE --> TEST[验证]
  DISP --> TEST
  NODE --> TEST
  BOUND --> TEST
  HIGH --> TEST
  MID --> TEST
  TEST --> T1[单测]
  TEST --> T2[黄金问题端到端]
  TEST --> T3[失败路径]
  TEST --> T4[Langfuse trace]
```

五步清单：

1. **顶层**：改输入、输出，还是控制律？是否影响稳定性？  
2. **定层**：UI / Dispatcher / Node / 边界 / 图装配？  
3. **定状态**：新 key？谁写谁读？要不要计数上限？  
4. **验证**：单测 → 端到端 → 故意失败 → Langfuse  
5. **回写文档**：流程变了就更新架构图与本文  

---

## 12. 一张总图 + 阅读顺序

### 12.1 总览

```mermaid
flowchart TB
  U[用户] --> FE[前端 EventSource]
  FE <-->|SSE| CTL[GraphController]
  CTL --> GS[GraphServiceImpl]
  GS --> MT[MultiTurnContext]
  GS --> LF[Langfuse]
  GS --> CG[CompiledGraph<br/>interruptBefore HF]

  CG --> SENSE[感知召回<br/>Intent/Evidence/Schema/Feasibility]
  CG --> PLAN[规划+HITL<br/>Planner/PlanExecutor/Human]
  CG --> EXEC[执行闭环<br/>SQL / Python 回修]
  EXEC --> PLAN
  PLAN --> REP[Report] --> END([END])
  SENSE --> PLAN

  EXEC --> EXT[边界: LLM / DB / Vector / Python]

  classDef user fill:#FFF4E6,stroke:#D97706,color:#1F2937;
  classDef core fill:#E0F2FE,stroke:#0284C7,color:#1F2937;
  classDef wf fill:#DCFCE7,stroke:#16A34A,color:#1F2937;
  classDef ext fill:#FCE7F3,stroke:#DB2777,color:#1F2937;
  class U,FE user; class CTL,GS,MT,LF,CG core; class SENSE,PLAN,EXEC,REP,END wf; class EXT ext;
```

### 12.2 推荐阅读顺序

1. 本文第 1–3 章（先建立坐标系）  
2. [`ARCHITECTURE.md`](./ARCHITECTURE.md)（官方架构图）  
3. `config/DataAgentConfiguration.java`（图装配）  
4. `controller/GraphController.java` + `service/graph/GraphServiceImpl.java`  
5. `workflow/node/IntentRecognitionNode.java`（节点模板）  
6. `PlanExecutorNode` + `PlanExecutorDispatcher`（主控制）  
7. `SqlGenerateDispatcher`（重试控制律）  
8. `app/services/graph/index.ts`（前端 SSE）  
9. [`ENGINEERING_SYSTEM_THINKING.md`](./ENGINEERING_SYSTEM_THINKING.md)（学习与贡献）  
10. [`DEVELOPER_GUIDE.md`](./DEVELOPER_GUIDE.md)（配置旋钮）  

### 12.3 工程控制论映射

| 工程控制论 | 本项目实现 |
| :--- | :--- |
| 分类定方法 | 意图识别 / 可行性评估 |
| 状态空间 | `OverAllState` + KeyStrategy |
| 反馈控制 | SQL/Python/Plan 重试边 |
| 抗饱和 | max retry / MAX_REPAIR_ATTEMPTS |
| 稳定性优先 | 先校验再执行，失败可 END |
| 复合控制 | 自动重试 + 人工反馈 |
| 对扰动鲁棒 | LLM/DB/向量接口化 |
| 可观测 | SSE + Langfuse |
| 最少自由度 | 一节点一事 |
| 工程实践检验 | 端到端 + CI |

---

## 读懂实现的四个验收问题

你能回答下面四个问题，就算真正读懂了：

```mermaid
flowchart TB
  Q1[1. 一次请求的输入状态是什么?]
  Q2[2. Dispatcher 在哪些点切换?]
  Q3[3. 出错反馈回到哪里? 如何不发散?]
  Q4[4. 换边界系统时哪些代码可不动?]
  Q1 --> Q2 --> Q3 --> Q4 --> OK[可以开始安全地二次开发]
```

---

> **收束**：先看图建立系统图像，再下钻到 Node/Dispatcher/State。  
> 工程化不是玄学，就是 **先全局后局部、先稳定后优化、每步可验证**。
