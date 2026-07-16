/**
 * embed 页 frame-ancestors CSP 中间件（R2 安全核心）。
 *
 * 对 GET /embed/:agentId 按该 Agent 的 allowedOrigins 动态下发
 * `Content-Security-Policy: frame-ancestors <origins>`，从浏览器层禁止非白名单宿主页 iframe 嵌入。
 * 通配符 * 或未配置 origins 时不设 CSP（交由部署层默认策略兜底）。
 *
 * agent 未启用 embed / config 端点 403 时不设 CSP（embed 页本身也无法正常工作）。
 */
export default defineEventHandler(async (event) => {
	const path: string = event.path || '';
	const m = /^\/embed\/([^/?#]+)/.exec(path);
	if (!m) return;
	const agentId = m[1];
	try {
		const cfg = await $fetch<{ allowedOrigins?: string[] }>(`/api/embed/public/${agentId}/config`);
		const origins = (cfg && cfg.allowedOrigins) || [];
		const clean = origins.map((o) => (o || '').trim()).filter((o) => o && o !== '*');
		if (clean.length > 0) {
			setHeader(event, 'Content-Security-Policy', `frame-ancestors ${clean.join(' ')}`);
		}
	} catch {
		/* agent 不存在或未启用 embed：不设 CSP，由部署层兜底 */
	}
});
