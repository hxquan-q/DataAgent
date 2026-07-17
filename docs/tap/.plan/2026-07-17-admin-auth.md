# 方案：平台管理员登录鉴权（Admin Auth）

> 日期：2026-07-17  
> 状态：**已实现（2026-07-18）**  
> 目标：管理端不可裸奔访问，必须管理员账号密码登录后才能操作  

---

## 0. 会审结论（四专家）

| 角色 | 结论 | 一句话 |
|------|------|--------|
| software-product-manager | **APPROVE_WITH_CHANGES** | 关大门目标/范围正确；须写死登出语义、改密 P0、prod fail-fast、忘密 Runbook |
| planner | **APPROVE_WITH_CHANGES** | WBS 可落地；钉白名单、SSE-only query token、T4←T5 依赖、前移 test Security |
| software-architect | **APPROVE_WITH_CHANGES** | Security WebFlux+JWT 正确；MCP/SSE/embed-preset/prod 密钥须收紧 |
| claude（落地） | **APPROVE_WITH_CHANGES** | 可落地；axios/$fetch/fileUpload 全链路、preset 断裂、401 JSON、测试不红 |

**合并后主线不变**：Spring Security WebFlux + JWT HS256 + BCrypt + `admin_user` + 空表 bootstrap + 前端 login/middleware。  
**编码前已吸收的附条件见 §8**。

---

## 1. 问题与目标

### 1.1 现状（证据）

| 项 | 事实 |
|----|------|
| 管理 API | `/api/**` 无登录校验，任意网络可达方均可 CRUD Agent/数据源/模型密钥 |
| 管理前端 | Nuxt SPA 无 login 页、无 route middleware、axios 无 401 处理 |
| 已有安全能力 | ① 凭据 AES-256-GCM 静态加密 ② embed HMAC 会话令牌；**均非管理端登录** |
| Spring Security | **未引入**；栈为 **WebFlux** |
| 用户表 | 无 `admin_user`；`agent.admin_id` / `chat_session.user_id` 预留未闭环 |
| 元库策略 | `sql.init=never` → 已有库手写 CREATE；禁止 `SQL_INIT=always` / `down -v` |
| 额外断裂点 | embed 页调用 `GET /api/agent/{id}/preset-questions`（管理 API）— 全局鉴权后会 401 |

### 1.2 产品目标（User Stories）

1. **As a** 平台管理员，**I want** 用账号密码登录管理端，**so that** 只有我能改 Agent/数据源/密钥。  
2. **As a** 未登录访客，**I want** 访问 `/system/*` 被带到登录页，**so that** 无法浏览管理 UI。  
3. **As a** 调用方，**I want** 无 Bearer 的管理 `/api/**` 一律 401，**so that** 脚本无法裸调。  
4. **As a** 管理员，**I want** 退出后本机管理会话结束，**so that** 共用设备风险可控。  
   - **v1 语义写死**：退出 = **客户端丢弃 token**；服务端 **不做** JWT blocklist；旧 token 在 TTL 内仍可能被 API 接受（已知限制，文档说明）。  
5. **As a** 管理员，**I want** 登录后修改密码（**P0**），**so that** 可不长期使用 bootstrap 弱密。  
6. **As a** 部署者，**I want** 空库 bootstrap / 已有库跑迁移脚本即可，**so that** hybrid 元库零破坏。  
7. **As a** embed 终端用户，**I want** 公开对话与预设问题不要求管理员登录，**so that** 已发布对话不受影响。

### 1.3 非目标（YAGNI）

- RBAC / 多租户 / 注册 / 邮箱找回 / MFA / OAuth/SSO  
- 服务端 logout 黑名单 / refresh token  
- 把 embed 并入管理员 JWT  
- 改 Graph/NL2SQL 业务逻辑  
- 完整用户中心  

---

## 2. 架构决策（已拍板）

| # | 议题 | 决定 |
|---|------|------|
| 1 | JWT vs Session | **JWT HS256**（无状态） |
| 2 | 栈 | **Spring Security WebFlux** + 薄 `JwtService`（jjwt 或 nimbus；不引入完整 OAuth2 RS） |
| 3 | SSE | 允许 query `access_token`，**仅** `/api/stream/**` 与 `/api/agent/*/sessions/stream`；Header 优先 |
| 4 | local 默认密码 | `admin` / `admin123` + **WARN** |
| 5 | prod | 空表无 `DATA_AGENT_ADMIN_PASSWORD` 或 JWT secret 缺失/过短 → **fail-fast** |
| 6 | Swagger | **local 可放行**；非 local **authenticated 或关闭** |
| 7 | 改密 | **本版必做（P0）** |
| 8 | 暴力破解 | **本版接受风险 + 记 P1 债务**（内网优先）；文档建议网关限流 |
| 9 | JWT TTL | 默认 **28800s（8h）**，env 可调 |
| 10 | MCP | 本版 **permitAll**（保持现状能力），路径写死；日后另做 API Key |

### 2.1 信任边界

```
Browser (Nuxt)
  │  POST /api/auth/login          (public)
  │  其它管理 /api/**              (Bearer JWT)
  │  SSE EventSource              (?access_token= 仅 stream 路径)
  ▼
SecurityWebFilterChain (WebFlux :8065)
  permitAll  → 见 §2.2
  authenticated → 其余
  ▼
AdminUser (BCrypt) / JwtService
```

### 2.2 permitAll 白名单（写死）

| 路径 | 原因 |
|------|------|
| `POST /api/auth/login` | 登录 |
| `GET /api/auth/bootstrap-status` | 是否已初始化 |
| `/api/embed/public/**` | 嵌入公开面（自有 token） |
| `/echo/**` | 健康/回显 |
| `/sse`、`/sse/**` | MCP SSE（Spring AI 默认） |
| `/mcp`、`/mcp/**` | MCP message/streamable |
| `/uploads/**` | 静态资源读（`WebConfig` url-prefix；`<v-img>` 不带 Bearer） |
| `GET /api/upload/**` | 文件/头像只读（若走此路径）；**POST 上传仍需认证** |
| OPTIONS `/**` | CORS preflight |
| local only：`/swagger-ui/**`、`/v3/api-docs/**` | 开发文档 |
| `/actuator/health`（若启用） | 仅 health；其它 actuator 默认拒 |

**不在白名单 = 需认证**（含 `/api/agent/**` 管理 CRUD、模型配置、数据源等）。

### 2.3 embed 预设问题（必须修）

现状：`pages/embed/[agentId].vue` → `GET /api/agent/{id}/preset-questions`。  
**采用**：在 `EmbedPublicController` 增加  
`GET /api/embed/public/{agentId}/preset-questions`（仅已发布/embed 启用 Agent 的只读列表），前端 embed 改调此路径。  
**不**把管理 preset API 整段 permitAll。

### 2.4 数据模型

```sql
CREATE TABLE IF NOT EXISTS admin_user (
  id            BIGINT NOT NULL AUTO_INCREMENT,
  username      VARCHAR(64)  NOT NULL,
  password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt',
  display_name  VARCHAR(64)  NULL,
  status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1=启用 0=禁用',
  last_login_at TIMESTAMP    NULL,
  create_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
  update_time   TIMESTAMP    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='平台管理员';
```

- 迁移：`scripts/sql/admin_user_v1.sql`（已有库）  
- `schema.sql` / `schema-h2.sql` 同步  
- **禁止** `data.sql` 写死生产密码  
- JWT secret 与 AES `SYSTEM_AES_KEY` **分离**

### 2.5 API

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/api/auth/login` | 公开 | `{username,password}` → `{token,expiresIn,username,adminId}` |
| POST | `/api/auth/logout` | 需登录 | 服务端 no-op；客户端丢弃 |
| GET  | `/api/auth/me` | 需登录 | 当前管理员 |
| POST | `/api/auth/change-password` | 需登录 | `{oldPassword,newPassword}`；成功后前端强制重登 |
| GET  | `/api/auth/bootstrap-status` | 公开 | `{initialized:boolean}` |
| GET  | `/api/embed/public/{agentId}/preset-questions` | 公开* | *embed 启用校验，只读 |

失败：登录失败 401 统一「用户名或密码错误」；无/无效 token → 401 JSON（`ApiResponse` 形）；禁用 → 403。

JWT 载荷：`sub=adminId`，claim `username`，`iat`/`exp`。

### 2.6 配置

```yaml
spring.ai.alibaba.data-agent.admin:
  username: ${DATA_AGENT_ADMIN_USERNAME:admin}
  password: ${DATA_AGENT_ADMIN_PASSWORD:}   # bootstrap only
  jwt-secret: ${DATA_AGENT_JWT_SECRET:}
  jwt-ttl-seconds: ${DATA_AGENT_JWT_TTL:28800}
```

- **local**：password 默认 `admin123`（或 yml 默认）+ WARN；jwt-secret 可缺省则启动时随机内存 secret + WARN  
- **prod**（`spring.profiles` 非 local/h2/test）：password 空且表空 → fail-fast；jwt-secret 空或 &lt;32 字符 → fail-fast  
- **h2/test**：固定 jwt-secret + 可预测 bootstrap 密码；或 `bootstrap-enabled=false`；**禁止**走 prod fail-fast  
- JWT secret **不得**复用 `embed.token-hmac-key` / `SYSTEM_AES_KEY`

### 2.7 前端

| 项 | 做法 |
|----|------|
| `pages/login.vue` | layout false |
| `middleware/auth.global.ts` | 除 `/login`、`/embed/**` 外需 token；`?redirect=` |
| Token | localStorage；**统一**请求注入 Bearer |
| 客户端覆盖 | axios 各 service、`$fetch`（如 agentDatasource）、`fileUpload` 的 fetch、EventSource（graph + session stream） |
| 401 | 清 token → `/login`；避免 login 页请求 me 死循环 |
| 壳层 | 用户名 + 退出；改密入口（P0 最小对话框） |
| 改密后 | 强制 logout 再 login |

### 2.8 测试策略

- 现有 `*ControllerTest` 纯 mock：**尽量不动**  
- 新增路径矩阵：`WebTestClient` — 无 token 401、login+token 200、embed public 200、echo 200、MCP path permit  
- test profile：固定 JWT secret + 可 bootstrap 测试管理员  
- `@SpringBootTest` 不红：test Security 配置与 T2 同批  

---

## 3. 风险与缓解

| 风险 | 缓解 |
|------|------|
| Security × MCP/SSE | 白名单写死；SSE query 路径谓词 |
| EventSource 无 Header | 仅 stream 路径 `access_token` |
| query token 进 access log | 短 TTL；文档脱敏；禁止业务日志打印 token |
| embed preset 401 | public 只读端点 |
| 测试全红 | T2.9 test Security |
| 登出≠吊销 | 产品明示 + 8h TTL + 改密后前端清 token |
| 忘密锁死 | Runbook：备份后 SQL 更新 BCrypt（T7） |
| 暴力破解 | P1 债务；网关限流建议 |
| 元库 | 只追加表；checklist 强制迁移步骤 |

---

## 4. 实施阶段

见 `docs/tap/.task/2026-07-17-admin-auth-tasks.md`。

顺序摘要：

```text
T0 → T1 → T2(+test Security) → T3
  → 并行 T5 ∥ T4.1 ∥ Jwt 单测
  → T5.2+T5.3 后 T4.2（SSE 挂 token）
  → T6 → T7
```

---

## 5. 验收标准（发布门）

1. 未登录打开 `/system/agents` 或 `/` → `/login?redirect=`  
2. 正确密码登录 → 管理 API 带 Bearer 成功  
3. 错误密码 → 401，统一文案  
4. 退出后前端不可用；**文档注明**旧 JWT 在 TTL 内服务端仍可能接受  
5. `curl` 无 token `/api/agent/list` → 401  
6. 改密成功 → 旧密码登录失败；前端需重新登录  
7. embed：config/exchange/chat + **preset-questions public** 无管理员 JWT 可用  
8. MCP `/sse`、`/mcp/**` 不被误 401  
9. 非 SSE 路径仅带 query `access_token` → **401**（路径收窄）  
10. hybrid：执行 `admin_user_v1.sql` 后可 bootstrap/登录  
11. 仓库无明文生产密码；未改 `SQL_INIT` / 未 `down -v`  
12. 相关测试绿  

---

## 6. 运维恢复（Runbook 要点 · T7 展开）

忘密（有元库访问权限时）：

1. `bash scripts/backup-meta-db.sh`（推荐）  
2. 本地生成 BCrypt 哈希（小工具或临时 Java main）  
3. `UPDATE admin_user SET password_hash=?, update_time=NOW() WHERE username='admin';`  
4. 用新密码登录；建议再在 UI 改一次密  

---

## 7. 原则对齐

- **η₁**：开发平面 + 数据平面仅追加表  
- **η₂ YAGNI**：单管理员关大门  
- **η₅**：hybrid 红线  
- **Ponytail**：标准 Security + 最小表/API；不做 IAM 平台  

---

## 8. 会审附条件清单（已并入本文与 task）

- [x] User Stories + 登出语义写死  
- [x] 改密 P0  
- [x] MCP/SSE/swagger/uploads 白名单写死  
- [x] query token 仅 SSE 路径  
- [x] embed preset public  
- [x] prod fail-fast  
- [x] JWT TTL 8h  
- [x] 测试 Security 前移  
- [x] T4 依赖 T5.2  
- [x] 忘密 Runbook  
- [x] 暴力破解 = P1 债务（本版不实现限流）  

---

## 9. 待你确认

确认后进入编码（按 task WBS）。若要调整：

- 暴力破解改为本版做登录限流  
- JWT 改回 24h  
- MCP 本版也要鉴权  

回复 **「确认，开始实现」** 或指出修改点即可。  
