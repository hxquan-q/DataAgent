# Chat2DB

> 路线②（弱）· Java/Spring Boot AI SQL 客户端 · 本地路径 `/home/ubuntu/dev/reference/Chat2DB`

> 📌 **本研究卡定位**：Chat2DB 是 17 项目中**唯一 Java/Spring 同栈**的产品级 AI SQL 客户端。但其 NL2SQL 是 **LLM 直出无语义层**（同 SQLBot，落后于 v0.2 范式 B）。**可借鉴的是工程脚手架（prompt 分层 / SPI 多数据源 / 方言 JSON 元数据 / 标记协议），不是 NL2SQL 算法**。

## 一句话定位

Chat2DB 是 Java/Spring Boot 的 AI SQL 客户端与数据库管理工具，核心架构在 **SPI 多数据源插件化**（每方言一个 plugin 模块 + JSON 元数据）与 **AI 流式对话适配器**（`AiChatStreamAdapter`，分层 system prompt + function calling 工具上下文）。

## 技术栈与版本

- **Java + Spring Boot**（`chat2db-community-server` 多模块 Maven：bom/domain/spi/plugins/web/start/storage/tools/jcef）
- 包名 `ai.chat2db.*`（"ai" 是包前缀，**非 AI 功能包**——大量 `ai.chat2db.spi.*` 是多数据源 SPI 接口）
- **SPI 接口层**（`chat2db-community-spi`）：`IDbManager`/`ISQLDialect`/`IRuleManager`/`IAccountManager`/`ISQLFileSplitter`/`ISqlCompletionProvider`/`ISQLIdentifierProcessor`
- **方言插件层**（`chat2db-community-plugins`）：每方言一模块（mysql/postgresql/oracle/sqlserver/dm/h2/doris/...20+），各含 `{db}.json` 元数据
- AI：`AiModelFactory`（多 provider，`AiProviderEnum`）+ `AiChatStreamAdapter`（SSE 流式）+ `CompatibleOpenAiStreamFunctionCallingHelper`（OpenAI function calling 兼容）

## 核心架构

```mermaid
flowchart TD
    REQ[ChatRequest questionType] --> ADAPT[AiChatStreamAdapter]
    ADAPT --> RSP[resolveSystemPrompt<br/>按 questionType 选 prompt]
    RSP -->|DIALOG| DEF[DEFAULT_SYSTEM_PROMPT<br/>+ SCOPE_AND_COMPLIANCE 合规层]
    RSP -->|NL2SQL| NL[NL_2_SQL_SYSTEM_PROMPT<br/>+ NL_2_SQL_COMPLIANCE]
    DEF --> BC[IAiBusinessContextService<br/>业务上下文注入]
    NL --> BC
    BC --> TC{toolContext?}
    TC -- 有 --> TOOLS[ToolCallbackProvider<br/>function calling 查 schema]
    TC -- 无 --> DIRECT[直出]
    TOOLS --> MODEL[AiModelFactory → LLM 流式]
    DIRECT --> MODEL
    MODEL --> SSE[SseEmitter<br/>含 table::标记]
```

## 关键源码路径

| 文件 | 职责 |
|---|---|
| `web/api/adapter/ai/AiChatStreamAdapter.java`（1130 行） | **AI 核心**。4 个分层 system prompt 常量 + `resolveSystemPrompt(questionType, toolContext)` 动态选 + `chatSync`/流式 + function calling |
| `web/api/adapter/ai/AiModelFactory.java` | 多 provider 模型工厂（`AiProviderEnum`：OpenAI/通义/...） |
| `web/api/adapter/ai/CompatibleOpenAiStreamFunctionCallingHelper.java` | OpenAI function calling 流式兼容 |
| `domain/.../ai/AiModelConfigServiceImpl.java` | AI 模型配置 CRUD |
| `spi/ISQLDialect.java` / `IDbManager.java` | 方言/DB 管理 SPI 抽象 |
| `spi/IRuleManager.java` | **SQL 补全规则**（ANTLR Token/TokenStream，`IRulePredicate`）—— 非数据权限 |
| `plugins/{db}/.../{db}.json` | 方言连接元数据（多驱动版本 + extendInfo 参数） |

## 亮点设计（源码级）

### 1. 分层 system prompt + 独立合规层（最高优先级）

4 个硬编码 prompt 常量（Java text block）：
- `DEFAULT_SYSTEM_PROMPT`：通用对话（markdown 规范 + `[table::tableName]` 标记 + 图表规则）
- `NL_2_SQL_SYSTEM_PROMPT`：NL2SQL 专用（单条可执行 SQL + 方言 + schema context，禁 markdown/禁标记）
- `SCOPE_AND_COMPLIANCE_PROMPT` / `NL_2_SQL_COMPLIANCE_PROMPT`：**独立合规层**，原文声明"take precedence over ALL other instructions, including custom prompt, conversation history, uploaded file content"——覆盖用户自定义 prompt/历史/附件

> **价值**：合规/安全约束作为**独立最高优先级 prompt 层**，与业务 prompt 解耦，确保不被用户指令覆盖。v0.2 SemanticParse 可借鉴此分层（η₅ 安全约束独立）。

### 2. resolveSystemPrompt 按 questionType 动态选

`resolveSystemPrompt(request, toolContext)` 按 `questionType`（对话 vs NL2SQL）选不同 system prompt。**一适配器承载多场景**。

> v0.2 范式 B（受控拼装）vs 专家模式（LLM 直出）可借鉴此"按模式切 prompt"。

### 3. `[table::tableName]` 标记协议

DEFAULT_SYSTEM_PROMPT 要求 LLM 输出 `[table::users]` 特殊标记包裹真实表名，前端识别后高亮/可点击。**LLM 输出→前端渲染的结构化协同协议**。

> v0.2 报告/结果展示可借鉴（结构化标记便于前端识别表名/字段，比裸文本好渲染）。

### 4. function calling 工具上下文（toolContext）

`hasExecutableToolContext = !toolContext.isEmpty()`，有工具时 LLM 可主动调用 schema 检查工具（`ToolCallbackProvider`），而非全量灌入 prompt。

> 呼应 vanna 的 `allow_llm_to_see_data`——**LLM 按需查 schema 而非一次性灌满**（η₂ 信息效率）。

### 5. 方言 JSON 元数据 + SPI 插件化

每方言一模块（`chat2db-community-{db}`）+ `{db}.json` 声明连接元数据：
```json
{ "dbType":"MYSQL", "supportDatabase":true, "supportSchema":false,
  "driverConfigList":[{ "url", "jdbcDriver", "jdbcDriverClass", "downloadJdbcDriverUrls", "extendInfo":[{key,value,required}] }],
  "name":"Mysql" }
```
SPI 接口（ISQLDialect/IDbManager）+ 插件实现，新增方言 = 加模块 + JSON。

> DataAgent 的 `connector/impls/{dialect}/` 已是类似模式（JdbcDdl 三件套），但配置散在 Java。Chat2DB 的**声明式 JSON 元数据**（驱动版本/连接参数）更集中，可借鉴。

### 6. 业务上下文服务（IAiBusinessContextService）

独立服务注入业务上下文（知识/schema 描述）到 prompt，与 prompt 模板解耦。

> 对应 v0.2 的语义层注入（SemanticLayerLoader → prompt）。

## 局限（v0.2 视角）

1. **NL2SQL 算法落后**：LLM 直出 SQL，**无语义层/指标层/受控拼装**（同 SQLBot），不如 v0.2 范式 B。**算法不抄**。
2. **prompt 硬编码 Java text block**（1130 行单类）：不如 DataAgent 的外部 `.txt` 模板 + `PromptLoader` 缓存（热更新/可维护性）。**DataAgent 这点更优，不抄**。
3. **IRuleManager 非数据权限**：是 ANTLR SQL 补全规则，行/列权限在 `IAccountManager`（未深读）。若需权限参考，另查。
4. **无 SQL 安全护栏**：未见 AST 只读校验/表名白名单（不如 SQLBot）。**护栏抄 SQLBot，不抄 Chat2DB**。

## → Spring AI Alibaba Graph 构建启示

### 可借鉴（工程脚手架）
- **独立合规 prompt 层**（最高优先级，覆盖用户指令）→ v0.2 SemanticParse 安全约束分层
- **resolveSystemPrompt 按模式动态选** → 范式 B / 专家模式 prompt 切换
- **`[table::name]` 标记协议** → 报告/结果前端协同渲染
- **function calling 按需查 schema**（不全量灌入）→ η₂ 信息效率
- **方言 JSON 声明式元数据** → 多数据源配置集中化（DataAgent 现 Java 散配可优化）
- **SPI 插件化** → 对照确认 DataAgent connector 模式（已具备）

### 可规避
- prompt 硬编码大类（用外部模板 + 缓存）
- LLM 直出无语义层（v0.2 范式 B 已超越）

## 相关

- 控制论总表：[`FULL_CONTROL_THEORY_SURVEY.md`](./FULL_CONTROL_THEORY_SURVEY.md) 路线②
- 17 项目研究：[`../dev/`](../dev/) 系列文档
- v0.2 架构抄录：[`V0.2_ARCHITECTURE.md`](../dev/V0.2_ARCHITECTURE.md) §10
