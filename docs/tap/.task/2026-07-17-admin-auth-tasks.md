# 任务分解：平台管理员登录鉴权

> 对应方案：`docs/tap/.plan/2026-07-17-admin-auth.md`  
> 状态：**已实现（2026-07-18）— 见代码与单测**  
> 依赖：无  

---

## 会审记录

| 角色 | 结论 | 关键意见 | 日期 |
|------|------|----------|------|
| software-product-manager | APPROVE_WITH_CHANGES | US/登出语义/改密P0/prod fail-fast/Swagger/忘密 Runbook；暴力破解本版记 P1 | 2026-07-17 |
| planner | APPROVE_WITH_CHANGES | 钉白名单；query token 仅 SSE；T4←T5.2；T2 同期 test Security；checklist 改 curl | 2026-07-17 |
| software-architect | APPROVE_WITH_CHANGES | Security+JWT 正确；MCP/SSE 路径收窄；embed preset 迁 public；401 JSON；TTL 8h | 2026-07-17 |
| claude | APPROVE_WITH_CHANGES | axios+$fetch+fileUpload+双 EventSource；preset 断裂；EntryPoint；测试矩阵 | 2026-07-17 |

---

## 执行顺序

```text
T0 → T1 → T2（含 T2.9 test Security）→ T3
  → 并行：T5 ∥ T4.1 ∥ T6.1
  → T5.2 + T5.3 完成后：T4.2
  → T6 全量 → T7

禁止宣称「T4 与 T5 完全并行」。
```

---

## WBS

### T0 · 准备 [P0]

- [ ] T0.1 风险前可执行 `bash scripts/backup-meta-db.sh`（不自动跑）  
- [ ] T0.2 父 BOM 下引入 `spring-boot-starter-security`（WebFlux）版本对齐  
- [ ] T0.3 **白名单表写死**（路径 | permit/auth | 原因）— 与方案 §2.2 一致：  
  - login / bootstrap-status  
  - embed public  
  - echo  
  - MCP `/sse/**`、`/mcp/**`  
  - uploads 静态  
  - OPTIONS  
  - swagger：local permit / 其它 auth  
- [ ] T0.4 冻结 login 请求/响应 JSON 契约（供 T3/T5）  

**DoD**：`SecurityConfig` 注释可粘贴同一张表。

---

### T1 · 数据层 [P0]

- [ ] T1.1 `schema.sql` + `schema-h2.sql` → `admin_user`  
- [ ] T1.2 `scripts/sql/admin_user_v1.sql`  
- [ ] T1.3 `AdminUser` entity + `AdminUserMapper`  
- [ ] T1.4 禁止 `data.sql` 写生产密码  

**DoD**：H2/MySQL 脚本可建表。

---

### T2 · 后端鉴权核心 [P0]（关键路径）

- [ ] T2.1 pom + security  
- [ ] T2.2 `AdminAuthProperties`  
- [ ] T2.3 BCrypt `PasswordEncoder`  
- [ ] T2.4 `JwtService`（jjwt **或** nimbus-jose-jwt；HS256；sub=adminId + username claim；TTL 默认 28800）  
- [ ] T2.5 `AdminUserService`：查用户、校验、bootstrap、改密、last_login  
- [ ] T2.6 `AdminBootstrapRunner`：  
  - 配置开关 `bootstrap-enabled`（h2/test 可关或固定密码）  
  - local：表空 + 密码 → 创建 + WARN  
  - prod：表空且无密码 **或** jwt-secret 无效 → **fail-fast**  
  - h2/test：**禁止** prod fail-fast 分支
- [ ] T2.7 `SecurityWebFilterChain`：  
  - CSRF off、stateless  
  - JWT 过滤器：`Authorization: Bearer` 优先；query `access_token` **仅**  
    - `/api/stream/**`  
    - `/api/agent/*/sessions/stream`  
  - 白名单 = T0.3  
  - 其余 authenticated  
  - OPTIONS permit  
- [ ] T2.8 `ServerAuthenticationEntryPoint` + `AccessDeniedHandler` → `ApiResponse` JSON 401/403  
- [ ] T2.9 **test Security**：固定 test JWT secret；bootstrap 可控；避免 `@SpringBootTest` 全红  

**DoD**：无 token `/api/agent/list`→401；有效 token→200；非 SSE 仅 query token→401。

---

### T3 · Auth API [P0]

- [ ] T3.1 `AuthController`：login / logout / me / change-password / bootstrap-status  
- [ ] T3.2 校验：密码非空；新密码 ≥8  
- [ ] T3.3 登录失败统一文案  
- [ ] T3.4 `GET /api/embed/public/{agentId}/preset-questions`（只读，embed 启用校验）  

**DoD**：curl 登录→me；embed preset public 无 JWT 可访问。

---

### T4 · SSE 兼容 [P0]

- [ ] T4.1 与 T2.7 对齐路径受限 query token（核对）  
- [ ] T4.2 **depends: T5.2**  
  - `app/services/graph/index.ts` EventSource + `access_token`  
  - `app/stores/chat.ts`（或 session stream 调用点）+ `access_token`  
  - SSE 鉴权失败：停止盲目 3s 重连，提示/登出（最小）  
- [ ] T4.3 文档：勿长期日志打印 token（可并 T7）  

**DoD**：登录后聊天 SSE + session stream 正常。

---

### T5 · 前端 [P0]

- [ ] T5.1 `services/auth/index.ts`  
- [ ] T5.2 auth store/composable：token + user  
- [ ] T5.3 **统一**注入 Bearer（axios 默认实例可盖住主体 REST；另补漏网）：  
  - axios 全局拦截器（17 个 `import axios` service）  
  - `$fetch`：`skill` / `prompt` / `agentDatasource.ts`  
  - `fileUpload` 等原生 `fetch`  
  - 抽 `getAccessToken()` / `withAccessToken(url)` 给 SSE  
  - 401 → 清会话跳登录；login 页不循环  
  - **不**给 embed 请求挂管理员 JWT
- [ ] T5.4 `pages/login.vue`  
- [ ] T5.5 `middleware/auth.global.ts`：放行 `/login`、`/embed/**`；`/`、`/agent/**`、`/system/**` 未登录跳转 + `redirect`  
- [ ] T5.6 `layouts/default.vue`：用户名 + 退出  
- [ ] T5.7 **改密 UI（P0）**；成功后强制重登  
- [ ] T5.8 embed 页改调 public preset API  

**DoD**：冷启动必登录；退出后无法调管理 API；embed 预设问题仍显示。

---

### T6 · 测试 [P0]

- [ ] T6.1 JwtService 单测  
- [ ] T6.2 AdminUserService 登录成功/失败/禁用  
- [ ] T6.3 路径矩阵 WebTestClient：  
  - 无 token 管理 API 401  
  - login + Bearer 200  
  - embed public 200  
  - echo 200  
  - SSE 路径 query token 成功  
  - 非 SSE 仅 query 401  
- [ ] T6.4 既有 Web 层测试绿（test secret / helper）  
- [ ] T6.5 embed 回归：config / exchange / chat / **preset-questions public**  

**DoD**：相关测试绿。

---

### T7 · 文档与运维 [P1/门禁]

- [ ] T7.1 `docs/dev/LOCAL_RUN_CHECKLIST.md`：  
  1. 跑 `scripts/sql/admin_user_v1.sql`  
  2. env：`DATA_AGENT_ADMIN_PASSWORD` / `DATA_AGENT_JWT_SECRET`  
  3. `curl` login → Bearer `/api/agent/list`（替换裸 list→200）  
- [ ] T7.2 模块 CLAUDE.md 一行摘要  
- [ ] T7.3 docker-compose **注释** env 示例；**禁止**改 `SQL_INIT`  
- [ ] T7.4 忘密 Runbook（方案 §6）  
- [ ] T7.5 P1 债务登记：登录限流 / MCP API Key / HttpOnly cookie  

**DoD**：新人按 checklist 可登录；红线未破。

---

## 手动冒烟顺序（发布门）

1. （建议）备份元库  
2. 执行 `scripts/sql/admin_user_v1.sql`  
3. 配置/依赖 local bootstrap  
4. 启动 → 一条 admin  
5. curl login → me → agent/list  
6. 浏览器冷启动管理页 → 登录  
7. 一次问答 SSE + session 标题流  
8. 上传（若常用）带 token  
9. 退出 → list 401  
10. embed public + preset  
11. 改密 → 重登  

---

## 文件影响预估

### 后端

| 路径 | 动作 |
|------|------|
| `data-agent-management/pom.xml` | +security、JWT lib |
| `.../config/SecurityConfig.java` | 新建 |
| `.../config/AdminAuthProperties.java` | 新建 |
| `.../security/JwtService.java` | 新建 |
| `.../security/JwtAuthenticationWebFilter.java` | 新建 |
| `.../security/*EntryPoint* / *Denied*` | 新建 |
| `.../entity/AdminUser.java` | 新建 |
| `.../mapper/AdminUserMapper.java` | 新建 |
| `.../service/admin/AdminUserService.java` | 新建 |
| `.../controller/AuthController.java` | 新建 |
| `.../controller/EmbedPublicController.java` | +preset |
| `.../resources/sql/schema.sql` | 改 |
| `.../resources/sql/h2/schema-h2.sql` | 改 |
| `scripts/sql/admin_user_v1.sql` | 新建 |
| `application.yml` / `application-local.yml` / test yml | 配置 |
| test Security 配置 | 新建 |

### 前端

| 路径 | 动作 |
|------|------|
| `app/pages/login.vue` | 新建 |
| `app/middleware/auth.global.ts` | 新建 |
| `app/services/auth/index.ts` | 新建 |
| `app/stores/auth.ts`（或 composable） | 新建 |
| `app/plugins/*` axios 拦截 | 新建/改 |
| `app/layouts/default.vue` | 改 |
| `app/services/graph/index.ts` | SSE token |
| `app/stores/chat.ts` | session stream token |
| `app/pages/embed/[agentId].vue` | preset public |
| `$fetch` / fileUpload 调用点 | Bearer |

---

## 发布门检查

- [ ] 方案 §5 验收全过  
- [ ] 未引入 `SQL_INIT=always`  
- [ ] 未 `docker compose down -v`  
- [ ] 仓库无明文生产密码  
- [ ] embed + MCP 回归  
- [ ] query token 非 SSE 不可用  

---

确认执行指令：用户回复 **「确认，开始实现」** 后按 T0→… 编码。  
