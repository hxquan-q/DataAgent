# DataAgent 前后端 API 对接文档

本文档描述 DataAgent 前端（Nuxt）与后端（Spring WebFlux / Spring MVC Controller）的接口契约、代理配置与对接约定。

> 源码依据：
>
> - 后端：`data-agent-management/src/main/java/com/alibaba/cloud/ai/dataagent/controller/*`
> - 前端：`data-agent-frontend-nuxt/app/services/**`
> - 代理：`data-agent-frontend-nuxt/nuxt.config.ts`
> - 端口：`data-agent-management/src/main/resources/application.yml`
>
> 运行时完整 Schema 以 Swagger 为准：`http://localhost:8065/swagger-ui.html`

---

## 1. 对接总览

### 1.1 请求链路

```text
浏览器
  → Nuxt 前端（开发默认 :3000，ssr: false）
  → 相对路径 /api/** 或 /nl2sql/**
  → Nuxt routeRules 反向代理
  → Java 后端 http://localhost:8065
  → Controller @RequestMapping
```

### 1.2 环境与端口

| 项 | 值 | 配置位置 |
|---|---|---|
| 后端 HTTP 端口 | `8065` | `application.yml` → `server.port` |
| 后端 context-path | 无（根路径） | — |
| Swagger UI | `http://localhost:8065/swagger-ui.html` | `springdoc.swagger-ui.path` |
| OpenAPI JSON | `http://localhost:8065/v3/api-docs` | `springdoc.api-docs.path` |
| 健康检查 | `GET http://localhost:8065/echo/ok` | `EchoController`（不在 `/api` 下） |

### 1.3 前端代理（开发）

文件：`data-agent-frontend-nuxt/nuxt.config.ts`

```ts
ssr: false,
routeRules: {
  '/': { redirect: '/agent/new' },
  '/api/**': { proxy: 'http://localhost:8065/api/**' },
  '/nl2sql/**': { proxy: 'http://localhost:8065/nl2sql/**' },
},
```

约定：

1. 前端 Service **统一使用相对路径**（如 `/api/agent/list`），不写死后端 host。
2. 开发环境由 Nuxt 代理到 `localhost:8065`。
3. 生产环境建议由 Nginx / 网关做同源反代；若前后端分离部署，需同步改代理目标或改为环境变量注入。
4. 多个 Controller 标注了 `@CrossOrigin(origins = "*")`，开发可用；生产优先同源代理，少依赖 CORS。

### 1.4 前端调用层

| 位置 | 说明 |
|---|---|
| `app/services/**/index.ts` | 按业务域封装 API（主入口） |
| `app/services/common/index.ts` | 通用响应类型 `ApiResponse` / `PageResponse` |
| HTTP 客户端 | 主要为 `axios`；部分模块用 `$fetch` |
| 流式 | `EventSource` 或 GET + `text/event-stream` |

### 1.5 响应体约定（重要）

后端**没有完全统一**响应包装，对接时需按接口区分：

| 形态 | 示例接口 | 前端处理 |
|---|---|---|
| 裸实体 / 列表 | `GET /api/agent/list` → `List<Agent>` | 直接使用 `response.data` |
| `ApiResponse<T>` | 多数知识/配置类接口 | 读 `success` / `message` / `data` |
| `PageResponse<T>` | Agent 知识分页 | 额外含 `total` / `pageNum` / `pageSize` / `totalPages` |
| `ResponseEntity<...>` | 会话、Prompt 部分接口 | 以 HTTP 状态码 + body 为准 |
| SSE | Graph / Session stream | `text/event-stream`，非 JSON 一次性响应 |
| 二进制 | HTML 报告下载、文件读取 | `byte[]` / blob |

前端通用类型（`app/services/common/index.ts`）：

```ts
interface ApiResponse<T = unknown> {
  success: boolean;
  message: string;
  data?: T;
}

interface PageResponse<T = unknown> {
  success: boolean;
  message: string;
  data: T;
  total: number;
  pageNum: number;
  pageSize: number;
  totalPages: number;
}
```

---

## 2. 模块映射一览

| 业务域 | 前端 Service | 后端 Controller | 基路径 |
|---|---|---|---|
| 智能体 | `services/agent` | `AgentController` | `/api/agent` |
| 预设问题 | `services/presetQuestion` | `AgentPresetQuestionController` | `/api/agent` |
| Agent 数据源 | `services/agentDatasource` | `AgentDatasourceController` | `/api/agent/{agentId}/datasources` |
| 会话 / 消息 | `services/chat` | `ChatController` | `/api` |
| 会话 SSE | stores/chat 等 | `SessionEventController` | `/api` |
| 图搜索 / NL 流式 | `services/graph` | `GraphController` | `/api` |
| 数据源 | `services/datasource` | `DatasourceController` | `/api/datasource` |
| 逻辑关系 | `services/logicalRelation` | `DatasourceController` | `/api/datasource` |
| 语义模型 | `services/semanticModel` | `SemanticModelController` | `/api/semantic-model` |
| 业务知识 | `services/businessKnowledge` | `BusinessKnowledgeController` | `/api/business-knowledge` |
| Agent 知识 | `services/agentKnowledge` | `AgentKnowledgeController` | `/api/agent-knowledge` |
| Prompt 配置 | `services/prompt` | `PromptConfigController` | `/api/prompt-config` |
| 模型配置 | `services/modelConfig` | `ModelConfigController` | `/api/model-config` |
| 文件上传 | `services/fileUpload` | `FileUploadController` | `/api/upload` |
| 健康检查 | — | `EchoController` | `/echo` |

---

## 3. 接口明细

下列路径均为**后端真实路径**；前端开发时使用相同相对路径（经 Nuxt 代理）。

### 3.1 智能体 `AgentController` — `/api/agent`

前端：`app/services/agent/index.ts`，`API_BASE_URL = '/api/agent'`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/agent/list` | 列表；`keyword` 优先，否则 `status`，都空返回全部 | Query: `status?`, `keyword?` | `Agent[]`（裸列表） |
| GET | `/api/agent/{id}` | 详情 | Path: `id` | `Agent`；不存在可能 404 |
| POST | `/api/agent` | 创建 | Body: `Agent` | `Agent` |
| PUT | `/api/agent/{id}` | 更新 | Path + Body: `Agent` | `Agent` |
| DELETE | `/api/agent/{id}` | 删除 | Path: `id` | `void` |
| POST | `/api/agent/{id}/publish` | 发布 | Path: `id` | `Agent` |
| POST | `/api/agent/{id}/offline` | 下线 | Path: `id` | `Agent` |
| GET | `/api/agent/{id}/api-key` | 查询 API Key | Path: `id` | `ApiResponse<ApiKeyResponse>` |
| POST | `/api/agent/{id}/api-key/generate` | 生成 | Path: `id` | `ApiResponse<ApiKeyResponse>` |
| POST | `/api/agent/{id}/api-key/reset` | 重置 | Path: `id` | `ApiResponse<ApiKeyResponse>` |
| DELETE | `/api/agent/{id}/api-key` | 删除 Key | Path: `id` | `ApiResponse<ApiKeyResponse>` |
| POST | `/api/agent/{id}/api-key/enable` | 启用/禁用 | Path: `id`；Query: `enabled` (boolean) | `ApiResponse<ApiKeyResponse>` |

`Agent` 主要字段（前后端对齐）：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | number | ID |
| `name` | string | 名称 |
| `description` | string | 描述 |
| `avatar` | string | 头像 URL |
| `status` | string | `draft` / `published` / `offline` |
| `apiKey` | string \| null | API Key |
| `apiKeyEnabled` | number \| boolean | 是否启用 Key |
| `prompt` | string | 提示词 |
| `category` | string | 分类 |
| `tags` | string | 标签 |
| `humanReviewEnabled` | number \| boolean | 人工审核开关 |
| `createTime` / `updateTime` | datetime | 时间戳 |

---

### 3.2 预设问题 `AgentPresetQuestionController` — `/api/agent`

前端：`app/services/presetQuestion/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/agent/{agentId}/preset-questions` | 列表 | Path: `agentId` | `AgentPresetQuestion[]` |
| POST | `/api/agent/{agentId}/preset-questions` | 批量保存 | Body: `List<Map>` 问题列表 | `{ message }` 类 Map |
| DELETE | `/api/agent/{agentId}/preset-questions/{questionId}` | 删除单条 | Path | Map 消息 |

---

### 3.3 Agent 数据源 `AgentDatasourceController` — `/api/agent/{agentId}/datasources`

前端：`app/services/agentDatasource/index.ts`  
（另有根级 `app/services/agentDatasource.ts`，逻辑重复，对接时优先 `index.ts`）

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| POST | `.../datasources/init` | 初始化 Schema | Path: `agentId` | `ApiResponse<?>` |
| GET | `.../datasources` | 已绑定数据源列表 | Path: `agentId` | `ApiResponse<AgentDatasource[]>` |
| GET | `.../datasources/active` | 当前激活数据源 | Path: `agentId` | `ApiResponse<AgentDatasource>` |
| POST | `.../datasources/{datasourceId}` | 绑定数据源 | Path | `ApiResponse<AgentDatasource>` |
| DELETE | `.../datasources/{datasourceId}` | 解绑 | Path | `ApiResponse<?>` |
| PUT | `.../datasources/toggle` | 切换启用状态 | Body: `ToggleDatasourceDTO` | `ApiResponse<AgentDatasource>` |
| POST | `.../datasources/tables` | 更新选中表 | Body: `UpdateDatasourceTablesDTO` | `ApiResponse<?>` |

---

### 3.4 会话与消息 `ChatController` — `/api`

前端：`app/services/chat/index.ts`，`API_BASE_URL = '/api'`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/agent/{id}/sessions` | Agent 下会话列表 | Path: `id` | `ChatSession[]` |
| POST | `/api/agent/{id}/sessions` | 创建会话 | Path；Body 可选 Map | `ChatSession` |
| DELETE | `/api/agent/{id}/sessions` | 清空该 Agent 全部会话 | Path | `ApiResponse` |
| GET | `/api/sessions/{sessionId}/messages` | 消息列表 | Path | `ChatMessage[]` |
| POST | `/api/sessions/{sessionId}/messages` | 保存消息 | Body: `ChatMessageDTO` | `ChatMessage` |
| PUT | `/api/sessions/{sessionId}/pin` | 置顶 | Query: `isPinned` | `ApiResponse` |
| PUT | `/api/sessions/{sessionId}/rename` | 重命名 | Query: `title` | `ApiResponse` |
| DELETE | `/api/sessions/{sessionId}` | 删除会话 | Path | `ApiResponse` |
| POST | `/api/sessions/{sessionId}/reports/html` | 导出 HTML 报告 | Body: 文本 content | `byte[]`（文件下载） |

注意：会话相关路径参数里 Agent 使用 `id`（Integer），与部分模块 `agentId` 命名不同，调用时保持与后端一致。

---

### 3.5 会话事件流 `SessionEventController` — `/api`

| Method | Path | 说明 | 响应 |
|---|---|---|---|
| GET | `/api/agent/{agentId}/sessions/stream` | 会话状态 SSE | `text/event-stream` |

前端示例：

```ts
const source = new EventSource(`/api/agent/${agentId}/sessions/stream`);
```

---

### 3.6 图搜索 / NL 流式 `GraphController` — `/api`

前端：`app/services/graph/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/stream/search` | 流式图/NL 查询 | Query: `agentId`（必填）, `query`（必填）, `threadId?` 等 | SSE：`Flux<ServerSentEvent<GraphNodeResponse>>` |

对接要点：

- `Content-Type` / `Accept` 按 SSE 处理，不要当普通 JSON REST。
- 查询参数拼在 URL 上；代理需支持长连接（开发用 Nuxt proxy 一般可用）。

---

### 3.7 数据源 `DatasourceController` — `/api/datasource`

前端：`app/services/datasource/index.ts`、`logicalRelation/index.ts`

#### 3.7.1 数据源本体

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/datasource/types` | 支持的数据源类型 | — | `ApiResponse<DatasourceTypeDTO[]>` |
| GET | `/api/datasource` | 列表 | Query: `status?`, `type?` | `Datasource[]`（裸列表） |
| GET | `/api/datasource/{id}` | 详情 | Path | `Datasource` |
| POST | `/api/datasource` | 创建 | Body: `Datasource` | `Datasource` |
| PUT | `/api/datasource/{id}` | 更新 | Path + Body | `Datasource` |
| DELETE | `/api/datasource/{id}` | 删除 | Path | `ApiResponse` |
| POST | `/api/datasource/{id}/test` | 测试连接 | Path | `ApiResponse` |
| GET | `/api/datasource/{id}/tables` | 表名列表 | Path | `string[]` |
| GET | `/api/datasource/{id}/tables/{tableName}/columns` | 列名列表 | Path | `ApiResponse<string[]>` |

#### 3.7.2 逻辑关系（同一 Controller）

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/datasource/{id}/logical-relations` | 列表 | Path | `ApiResponse<LogicalRelation[]>` |
| POST | `/api/datasource/{id}/logical-relations` | 新增 | Body: `CreateLogicalRelationDTO` | `ApiResponse<LogicalRelation>` |
| PUT | `/api/datasource/{id}/logical-relations/{relationId}` | 更新 | Body: `UpdateLogicalRelationDTO` | `ApiResponse<LogicalRelation>` |
| DELETE | `/api/datasource/{id}/logical-relations/{relationId}` | 删除 | Path | `ApiResponse<Void>` |
| PUT | `/api/datasource/{id}/logical-relations` | 批量保存 | Body: `LogicalRelation[]` | `ApiResponse<LogicalRelation[]>` |

---

### 3.8 语义模型 `SemanticModelController` — `/api/semantic-model`

前端：`app/services/semanticModel/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/semantic-model` | 列表 | Query: `keyword?`, `agentId?` | `ApiResponse<SemanticModel[]>` |
| GET | `/api/semantic-model/{id}` | 详情 | Path | `ApiResponse<SemanticModel>` |
| POST | `/api/semantic-model` | 创建 | Body: `SemanticModelAddDTO` | `ApiResponse<Boolean>` |
| PUT | `/api/semantic-model/{id}` | 更新 | Body: `SemanticModel` | `ApiResponse<SemanticModel>` |
| DELETE | `/api/semantic-model/{id}` | 删除 | Path | `ApiResponse<Boolean>` |
| DELETE | `/api/semantic-model/batch` | 批量删除 | Body: id 列表 | `ApiResponse` |
| PUT | `/api/semantic-model/enable` | 批量启用 | Body: ids | `ApiResponse` |
| PUT | `/api/semantic-model/disable` | 批量禁用 | Body: ids | `ApiResponse` |
| POST | `/api/semantic-model/batch-import` | 批量导入 | Body | `ApiResponse` |
| POST | `/api/semantic-model/import/excel` | Excel 导入 | multipart/body | `ApiResponse` |
| GET | `/api/semantic-model/template/download` | 下载模板 | — | 文件流 |

---

### 3.9 业务知识 `BusinessKnowledgeController` — `/api/business-knowledge`

前端：`app/services/businessKnowledge/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/business-knowledge` | 列表 | Query: `agentId`（必填）, `keyword?` | `ApiResponse<BusinessKnowledgeVO[]>` |
| GET | `/api/business-knowledge/{id}` | 详情 | Path | `ApiResponse<BusinessKnowledgeVO>` |
| POST | `/api/business-knowledge` | 创建 | Body: `CreateBusinessKnowledgeDTO` | `ApiResponse<BusinessKnowledgeVO>` |
| PUT | `/api/business-knowledge/{id}` | 更新 | Body: `UpdateBusinessKnowledgeDTO` | `ApiResponse<BusinessKnowledgeVO>` |
| DELETE | `/api/business-knowledge/{id}` | 删除 | Path | `ApiResponse<Boolean>` |
| POST | `/api/business-knowledge/recall/{id}` | 设置是否召回 | Query: `isRecall` | `ApiResponse<Boolean>` |
| POST | `/api/business-knowledge/retry-embedding/{id}` | 重试向量化 | Path | `ApiResponse<Boolean>` |
| POST | `/api/business-knowledge/refresh-vector-store` | 刷新向量库 | Query: `agentId` | `ApiResponse<Boolean>` |

---

### 3.10 Agent 知识 `AgentKnowledgeController` — `/api/agent-knowledge`

前端：`app/services/agentKnowledge/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/agent-knowledge/{id}` | 详情 | Path | `ApiResponse<AgentKnowledgeVO>` |
| POST | `/api/agent-knowledge/create` | 创建（含文件） | **multipart**：`agentId`, `title`, `type`, `question?`, 文件等 | `ApiResponse<AgentKnowledgeVO>` |
| PUT | `/api/agent-knowledge/{id}` | 更新 | Body: `UpdateKnowledgeDTO` | `ApiResponse<AgentKnowledgeVO>` |
| PUT | `/api/agent-knowledge/recall/{id}` | 召回开关 | Query: `isRecall` | `ApiResponse<AgentKnowledgeVO>` |
| DELETE | `/api/agent-knowledge/{id}` | 删除 | Path | `ApiResponse<Boolean>` |
| POST | `/api/agent-knowledge/query/page` | 分页查询 | Body: `AgentKnowledgeQueryDTO` | `PageResponse<List<AgentKnowledgeVO>>` |
| POST | `/api/agent-knowledge/retry-embedding/{id}` | 重试 embedding | Path | `ApiResponse<AgentKnowledgeVO>` |

前端另有 `/agent/{agentId}`、`/statistics/{agentId}` 等调用，若 404 请以 Swagger 与当前 Controller 为准核对是否仍在实现中。

---

### 3.11 Prompt 配置 `PromptConfigController` — `/api/prompt-config`

前端：`app/services/prompt/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| POST | `/api/prompt-config/save` | 保存 | Body: `PromptConfigDTO` | `Map` 包装 |
| GET | `/api/prompt-config/{id}` | 详情 | Path | Map |
| GET | `/api/prompt-config/list` | 全部 | — | Map |
| GET | `/api/prompt-config/list-by-type/{promptType}` | 按类型 | Query: `agentId?` | Map |
| GET | `/api/prompt-config/active/{promptType}` | 当前激活一条 | Query: `agentId?` | Map |
| GET | `/api/prompt-config/active-all/{promptType}` | 激活列表 | Query: `agentId?` | Map |
| DELETE | `/api/prompt-config/{id}` | 删除 | Path | Map |
| POST | `/api/prompt-config/{id}/enable` | 启用 | Path | Map |
| POST | `/api/prompt-config/{id}/disable` | 禁用 | Path | Map |
| GET | `/api/prompt-config/types` | 支持的类型 | — | Map |
| POST | `/api/prompt-config/batch-enable` | 批量启用 | Body: `string[]` ids | Map |
| POST | `/api/prompt-config/batch-disable` | 批量禁用 | Body: `string[]` ids | Map |
| POST | `/api/prompt-config/{id}/priority` | 更新优先级 | Body: Map | Map |
| POST | `/api/prompt-config/{id}/display-order` | 更新展示顺序 | Body: Map | Map |

说明：该模块大量返回 `ResponseEntity<Map<String, Object>>`，字段名以实际 JSON 为准（通常含 success/data/message 一类键）。

---

### 3.12 模型配置 `ModelConfigController` — `/api/model-config`

前端：`app/services/modelConfig/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| GET | `/api/model-config/list` | 列表 | — | `ApiResponse<ModelConfigDTO[]>` |
| POST | `/api/model-config/add` | 新增 | Body: `ModelConfigDTO` | `ApiResponse<String>` |
| PUT | `/api/model-config/update` | 更新 | Body: `ModelConfigDTO` | `ApiResponse<String>` |
| DELETE | `/api/model-config/{id}` | 删除 | Path | `ApiResponse<String>` |
| POST | `/api/model-config/activate/{id}` | 激活 | Path | `ApiResponse<String>` |
| POST | `/api/model-config/test` | 测试连通性 | Body: `ModelConfigDTO` | `ApiResponse<String>` |
| GET | `/api/model-config/check-ready` | 是否就绪 | — | `ApiResponse<ModelCheckVo>` |

---

### 3.13 文件上传 `FileUploadController` — `/api/upload`

前端：`app/services/fileUpload/index.ts`

| Method | Path | 说明 | 请求 | 响应 |
|---|---|---|---|---|
| POST | `/api/upload/avatar` | 上传头像 | multipart: `file` | `UploadResponse`（包在 ResponseEntity 中） |
| GET | `/api/upload/**` | 读取已上传文件 | 路径后缀为存储相对路径 | `byte[]` |

后端文件相关配置（`application.yml` → `spring.ai.alibaba.data-agent.file`）：

| 配置项 | 默认 | 说明 |
|---|---|---|
| `type` | `local` | `local` / `oss` |
| `path` | `uploads` | 本地目录 |
| `url-prefix` | `/uploads` | 对外 URL 前缀（注意与 `/api/upload` 读取路径的关系） |
| `imageSize` | `2097152` | 图片大小上限（2MB） |
| WebFlux multipart | `max-file-size: 10MB` | 单文件上限 |

---

### 3.14 健康检查 `EchoController` — `/echo`

| Method | Path | 说明 | 响应 |
|---|---|---|---|
| GET | `/echo/ok` | 存活探测 | 文本/字符串 |

注意：该路径**不在** Nuxt `/api/**` 代理范围内，前端或探活脚本需直连后端 `8065`，或额外增加代理规则。

---

## 4. 流式接口对接说明

### 4.1 会话 SSE

```text
GET /api/agent/{agentId}/sessions/stream
Accept: text/event-stream
```

- 使用 `EventSource`（浏览器）即可。
- 连接应随页面卸载关闭，避免泄漏。

### 4.2 Graph 流式搜索

```text
GET /api/stream/search?agentId=...&query=...&threadId=...
Accept: text/event-stream
```

- 事件体为 `GraphNodeResponse` 序列化结果。
- 适合展示分步推理 / SQL / 结果节点。
- 反向代理超时建议调大（生产 Nginx 注意 `proxy_read_timeout`）。

---

## 5. 错误与状态码

| 场景 | 常见表现 | 前端建议 |
|---|---|---|
| 资源不存在 | HTTP 404（如 Agent get） | 返回 null / 友好提示 |
| 业务失败 | `ApiResponse.success = false` + `message` | 展示 `message` |
| 参数校验失败 | 4xx + 全局异常处理 body | 读 GlobalExceptionHandler 统一结构 |
| 服务未启动 / 代理失败 | 网络错误、502 | 检查 8065 与 `nuxt.config.ts` 代理 |

全局异常：`GlobalExceptionHandler`（具体字段以运行时响应为准）。

---

## 6. 联调检查清单

1. 启动后端，确认 `http://localhost:8065/echo/ok` 可用。
2. 打开 `http://localhost:8065/swagger-ui.html`，核对参数与响应模型。
3. 启动前端，浏览器 Network 中请求路径应为 `/api/...`（同源），不应直接写 `8065`（开发代理场景）。
4. 验证一类普通 REST（如 `GET /api/agent/list`）与一类 SSE（`/api/stream/search` 或 sessions stream）。
5. 上传类接口使用 `multipart/form-data`，字段名与后端 `@RequestPart` 一致。
6. 注意裸实体 vs `ApiResponse` 混用，避免统一 `response.data.data` 导致取错层。

### 6.1 curl 示例

```bash
# 健康检查（直连后端）
curl -s http://localhost:8065/echo/ok

# Agent 列表（直连）
curl -s 'http://localhost:8065/api/agent/list'

# 经前端代理（前端已启动时）
curl -s 'http://localhost:3000/api/agent/list'

# 模型是否就绪
curl -s 'http://localhost:8065/api/model-config/check-ready'
```

---

## 7. 维护说明

| 变更类型 | 需要同步的位置 |
|---|---|
| 新增/修改后端接口 | Controller + 本文档 + 前端 `app/services/**` |
| 改后端端口 | `application.yml` + `nuxt.config.ts` 代理 |
| 改响应包装结构 | 前端类型与解包逻辑 + 本文档第 1.5 节 |
| 生产部署 | 网关反代规则；可去掉开发用 routeRules 硬编码 host |

建议以 **Swagger / OpenAPI** 为运行时权威契约；本文档侧重「前后端路径映射与对接约定」，便于研发快速联调。

---

## 8. 相关文件索引

```text
data-agent-management/
  src/main/resources/application.yml
  src/main/java/.../controller/
    AgentController.java
    AgentDatasourceController.java
    AgentKnowledgeController.java
    AgentPresetQuestionController.java
    BusinessKnowledgeController.java
    ChatController.java
    DatasourceController.java
    EchoController.java
    FileUploadController.java
    GraphController.java
    ModelConfigController.java
    PromptConfigController.java
    SemanticModelController.java
    SessionEventController.java
    GlobalExceptionHandler.java

data-agent-frontend-nuxt/
  nuxt.config.ts
  app/services/
    agent/
    agentDatasource/
    agentKnowledge/
    businessKnowledge/
    chat/
    common/
    datasource/
    fileUpload/
    graph/
    logicalRelation/
    modelConfig/
    presetQuestion/
    prompt/
    semanticModel/
```

---

*文档版本：基于仓库当前 Controller 与前端 services 梳理。若与 Swagger 不一致，以代码与 Swagger 为准并回写本文档。*
