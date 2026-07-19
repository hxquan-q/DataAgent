# 后端 API 变更台账

> 规则：前端壳层重构**默认冻结** REST / SSE / 鉴权 / embed HMAC。  
> 若必须改后端：先在本表登记 → 再改 Java → 再改前端。

| 日期 | Path | 变更摘要 | 兼容策略 | 状态 | PR/Commit |
|------|------|----------|----------|------|-----------|
| — | — | （暂无） | — | — | — |

| 2026-07-19 | GET `/api/stream/search` | 客户端 SSE 传输：EventSource → fetch+stream；仍同 path/query 参数；鉴权优先 `Authorization: Bearer`，query `access_token` 保留兼容 | 兼容 | done | rebuild-ui |
| 2026-07-19 | GET `/api/agent/{id}/sessions/stream` | 同上 fetch SSE；Bearer 鉴权 | 兼容 | done | rebuild-ui |
| 2026-07-19 | Graph SSE headers | 增补 `X-Accel-Buffering: no` 防反代缓冲 | 兼容 | done | rebuild-ui |
