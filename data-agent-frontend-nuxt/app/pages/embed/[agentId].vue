<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, nextTick, watch } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { transformTableTagsInHtml } from '~/utils/tableTag';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useTypewriter } from '~/composables/useTypewriter';
import ChatResultSet from '~/components/chat/ChatResultSet.vue';
import type { ResultData } from '~/services/resultSet/index';

/**
 * embed 对话页 — UI 对齐 WeKnora EmbedChat：
 * - 主答案：markdown-it + echarts + 打字机
 * - 中间 Graph 节点：可折叠「分析过程」树（默认收起）
 * - 欢迎气泡 / 建议问题 / 主题色
 * 通信与鉴权：postMessage token + EventSource SSE（不变）
 */
definePageMeta({ layout: false });

useHead({
	meta: [{ name: 'referrer', content: 'no-referrer' }],
});

const route = useRoute();
const agentId = computed(() => String(route.params.agentId));
const { renderECharts } = useEchartsRenderer();
const { displayedText, append, reset, flush } = useTypewriter();

interface Block {
	nodeName: string;
	text: string;
	textType?: string;
}
interface Msg {
	role: 'user' | 'assistant';
	content: string;
	blocks?: Block[];
	/** 主答案 markdown 文本（完成后快照） */
	answer?: string;
	/** 本地去重时间戳 */
	_ts?: number;
}

interface EmbedUiConfig {
	welcomeMessage?: string;
	primaryColor?: string;
	title?: string;
	showSuggestedQuestions?: boolean;
}

const NODE_LABELS: Record<string, string> = {
	IntentRecognitionNode: '意图识别',
	EvidenceRecallNode: '证据召回',
	QueryEnhanceNode: '问题增强',
	SchemaRecallNode: 'Schema 召回',
	TableRelationNode: '表关系推断',
	FeasibilityAssessmentNode: '可行性评估',
	PlannerNode: '规划',
	PlanExecutorNode: '计划执行',
	SqlGenerateNode: 'SQL 生成',
	SemanticConsistencyNode: '语义校验',
	SqlExecuteNode: 'SQL 执行',
	PythonGenerateNode: 'Python 生成',
	PythonExecuteNode: 'Python 执行',
	PythonAnalyzeNode: 'Python 分析',
	ReportGeneratorNode: '报告生成',
	HumanFeedbackNode: '人工确认',
	SemanticParseNode: '语义解析',
	BuildSQLNode: '受控拼装 SQL',
};

const FALLBACK_SUGGESTIONS = [
	'各客户最近三个月订单与预测偏差最大的是谁？',
	'汇总本月采购供应执行中的短缺情况',
	'物料供需平衡表里缺口最大的物料有哪些？',
	'售后与设备维修的异常记录概览',
];

const config = ref<EmbedUiConfig | null>(null);
const token = ref('');
const hostContext = ref<Record<string, unknown>>({});
const sessionId = ref('');
const messages = ref<Msg[]>([]);
const input = ref('');
const pendingHostQuery = ref('');
const streaming = ref(false);
const liveBlocks = ref<Block[]>([]);
const ready = ref(false);
const suggested = ref<string[]>([]);
/** 历史消息「分析过程」展开索引 */
const expandedSteps = ref<Set<number>>(new Set());
const liveShowSteps = ref(false);

function toggleSteps(i: number) {
	const next = new Set(expandedSteps.value);
	if (next.has(i)) next.delete(i);
	else next.add(i);
	expandedSteps.value = next;
}
const userHasScrolledUp = ref(false);

const listRef = ref<HTMLDivElement | null>(null);
let activeES: EventSource | null = null;
let lastTypedLen = 0;
let ignoreScroll = false;

const SANITIZE_OPTIONS = {
	ADD_TAGS: ['div'],
	ADD_ATTR: ['style', 'class', 'data-echarts-config'],
};

const themeColor = computed(() => config.value?.primaryColor || '#2F84D6');
const initHint = computed(() => {
	if (!config.value) return '正在加载配置…';
	if (!token.value) return '等待宿主鉴权（会话令牌）…';
	return '初始化完成';
});
const title = computed(() => config.value?.title || 'DataAgent 助手');
const showWelcome = computed(
	() => Boolean(config.value?.welcomeMessage) && messages.value.length === 0 && !streaming.value,
);
const showSuggested = computed(
	() =>
		(config.value?.showSuggestedQuestions !== false) &&
		suggested.value.length > 0 &&
		messages.value.length === 0 &&
		!streaming.value,
);

function nodeLabel(name: string): string {
	return NODE_LABELS[name] || name.replace(/Node$/, '');
}

function isMarkdownBlock(b: Block): boolean {
	return b.textType === 'MARK_DOWN' || b.nodeName === 'ReportGeneratorNode';
}

function isCodeBlock(b: Block): boolean {
	return b.textType === 'SQL' || b.textType === 'JSON' || b.textType === 'PYTHON';
}

function isResultSetBlock(b: Block): boolean {
	return b.textType === 'RESULT_SET';
}

function parseResultSet(raw: string): ResultData | null {
	if (!raw) return null;
	try {
		return JSON.parse(raw) as ResultData;
	} catch {
		return null;
	}
}

function hasRenderableResultSet(raw: string): boolean {
	const d = parseResultSet(raw);
	if (!d?.resultSet) return false;
	const cols = d.resultSet.column || [];
	const rows = d.resultSet.data || [];
	const err = d.resultSet.errorMsg || '';
	// 有真实列/行或明确执行错误才展示；裸解析失败不占位
	return Boolean(err) || cols.length > 0 || rows.length > 0;
}

function resultSetBlocks(blocks: Block[] | undefined): Block[] {
	if (!blocks?.length) return [];
	return blocks.filter(
		(b) => isResultSetBlock(b) && Boolean(b.text?.trim()) && hasRenderableResultSet(b.text),
	);
}


function extractAnswer(blocks: Block[]): string {
	const rev = [...blocks].reverse();
	const report = rev.find((b) => isMarkdownBlock(b) && b.text.trim());
	if (report) return report.text;
	// 无报告节点时：拼非代码块文本，避免整页代码墙
	const texts = blocks.filter((b) => !isCodeBlock(b) && b.text.trim()).map((b) => b.text.trim());
	if (texts.length) return texts[texts.length - 1];
	return blocks.map((b) => b.text).filter(Boolean).join('\n\n');
}

function intermediateBlocks(blocks: Block[]): Block[] {
	const answer = extractAnswer(blocks);
	return blocks.filter((b) => {
		if (!b.text?.trim()) return false;
		if (isMarkdownBlock(b) && b.text === answer) return false;
		// 报告节点本身不当作中间步骤展示
		if (b.nodeName === 'ReportGeneratorNode') return false;
		// RESULT_SET 提升为答案产物，不进过程树
		if (isResultSetBlock(b)) return false;
		return true;
	});
}

function sanitizeMd(mdSrc: string): string {
	if (!mdSrc) return '';
	return DOMPurify.sanitize(
		transformTableTagsInHtml(renderMarkdownContent(mdSrc)),
		SANITIZE_OPTIONS,
	) as string;
}

function renderBlockDetail(b: Block): string {
	let raw = b.text || '';
	if (!raw) return '';
	// 过程区截断，避免 141KB SSE 中间态把界面拖死
	const LIMIT = 1600;
	const truncated = raw.length > LIMIT;
	if (truncated) raw = raw.slice(0, LIMIT) + '\n…(过程输出已截断)';
	if (isCodeBlock(b)) {
		const lang = (b.textType || 'text').toLowerCase();
		const body = raw.replace(/```/g, '\\`\\`\\`');
		return sanitizeMd('```' + lang + '\n' + body + '\n```');
	}
	return sanitizeMd(raw);
}

const liveAnswer = computed(() => extractAnswer(liveBlocks.value));
const liveSteps = computed(() => intermediateBlocks(liveBlocks.value));
const LIVE_STEP_WINDOW = 6;
const liveStepsDisplay = computed(() => {
	const steps = liveSteps.value;
	if (steps.length <= LIVE_STEP_WINDOW) return steps;
	return steps.slice(-LIVE_STEP_WINDOW);
});
const liveStepsHidden = computed(() =>
	Math.max(0, liveSteps.value.length - liveStepsDisplay.value.length),
);
const liveAnswerHtml = computed(() => sanitizeMd(displayedText.value));

function feedTypewriter(full: string) {
	if (!full) {
		reset();
		lastTypedLen = 0;
		return;
	}
	if (full.length < lastTypedLen) {
		reset();
		lastTypedLen = 0;
	}
	if (full.length > lastTypedLen) {
		append(full.slice(lastTypedLen));
		lastTypedLen = full.length;
	}
}

watch(liveAnswer, (v) => {
	if (streaming.value) feedTypewriter(v);
});

watch(streaming, (s) => {
	if (!s) {
		flush();
		lastTypedLen = 0;
		// 完成后收起过程（答案 + 结果表保留）
		liveShowSteps.value = false;
	} else {
		reset();
		lastTypedLen = 0;
		// 执行中展示过程（有界窗口）
		liveShowSteps.value = true;
	}
});

function scrollToBottom(force = false) {
	nextTick(() => {
		const el = listRef.value;
		if (!el) return;
		if (!force && userHasScrolledUp.value) return;
		ignoreScroll = true;
		el.scrollTop = el.scrollHeight;
		requestAnimationFrame(() => {
			ignoreScroll = false;
		});
	});
}

function onListScroll() {
	if (ignoreScroll) return;
	const el = listRef.value;
	if (!el) return;
	const dist = el.scrollHeight - el.scrollTop - el.clientHeight;
	userHasScrolledUp.value = dist > 80;
}

function scheduleCharts() {
	nextTick(() => {
		renderECharts(listRef.value);
		scrollToBottom();
	});
}

watch([messages, liveAnswerHtml, displayedText], () => scheduleCharts(), { deep: true });

async function onHostMessage(ev: MessageEvent) {
	if (ev.source !== window.parent) return;
	const d = ev.data || {};
	if (d.source !== 'dataagent-host') return;
	if (d.type === 'token') {
		await setSessionToken(d.token);
		const pending = pendingHostQuery.value;
		pendingHostQuery.value = '';
		if (pending && !streaming.value) void send(pending);
	} else if (d.type === 'context') {
		hostContext.value = d.context || {};
	} else if (d.type === 'query') {
		const q = String(d.query || '').trim();
		if (!q || streaming.value) return;
		if (!token.value) {
			pendingHostQuery.value = q;
			return;
		}
		input.value = q;
		void send(q);
	}
}

async function resolveSessionToken(candidate: string): Promise<string> {
	if (!candidate || candidate.startsWith('das_')) return candidate;
	// Generated snippets carry the publish token; exchange it before creating a session.
	try {
		const res = await fetch(`/api/embed/public/${agentId.value}/exchange`, {
			method: 'POST',
			headers: { 'X-Publish-Token': candidate },
		});
		if (res.ok) {
			const data = (await res.json()) as { sessionToken?: string };
			if (data.sessionToken) return data.sessionToken;
		}
	} catch {
		// Keep legacy opaque session tokens compatible.
	}
	return candidate;
}

async function setSessionToken(candidate: unknown) {
	ready.value = false;
	token.value = await resolveSessionToken(String(candidate || '').trim());
	ready.value = Boolean(token.value);
}

async function ensureSession() {
	if (sessionId.value) return;
	const res = await fetch(`/api/embed/public/${agentId.value}/sessions`, {
		method: 'POST',
		headers: { 'X-Session-Token': token.value },
	});
	if (!res.ok) throw new Error('创建会话失败');
	const data = await res.json();
	sessionId.value = data.sessionId;
}

function buildQuery(q: string): string {
	const ctx = hostContext.value;
	const keys = Object.keys(ctx).filter((k) => {
		const v = ctx[k];
		return v !== undefined && v !== null && v !== '';
	});
	if (!keys.length) return q;
	const lines = keys.map((k) => `${k}: ${typeof ctx[k] === 'string' ? ctx[k] : JSON.stringify(ctx[k])}`);
	return `[Host context]\n${lines.join('\n')}\n\n${q}`;
}

let sendLock = false;
let streamFinished = false;

async function send(preset?: string) {
	const q = (preset ?? input.value).trim();
	if (!q || !token.value || streaming.value || sendLock) return;
	// 防宿主连发同一 query（ready 重入 / postMessage 双投）
	const lastUser = [...messages.value].reverse().find((m) => m.role === 'user');
	if (lastUser && lastUser.content === q && streaming.value) return;

	sendLock = true;
	streamFinished = false;
	try {
		await ensureSession();
	} catch {
		messages.value.push({ role: 'assistant', content: '⚠️ 会话初始化失败，请稍后重试。' });
		sendLock = false;
		return;
	}
	// 1.5s 内相同 user 文案视为双发，不重复气泡
	const dup =
		lastUser &&
		lastUser.content === q &&
		Date.now() - (lastUser._ts || 0) < 1500;
	if (!dup) {
		messages.value.push({ role: 'user', content: q, _ts: Date.now() });
	}
	input.value = '';
	streaming.value = true;
	liveBlocks.value = [];
	liveShowSteps.value = true; // 立即给过程反馈
	userHasScrolledUp.value = false;
	reset();
	lastTypedLen = 0;
	scrollToBottom(true);

	const params = new URLSearchParams({
		query: buildQuery(q),
		threadId: sessionId.value,
		token: token.value,
	});
	const es = new EventSource(`/api/embed/public/${agentId.value}/chat?${params.toString()}`);
	activeES = es;
	es.onmessage = (event) => {
		try {
			const node = JSON.parse(event.data) as {
				nodeName: string;
				text: string;
				textType?: string;
			};
			if (!node.text) return;
			const last = liveBlocks.value[liveBlocks.value.length - 1];
			if (last && last.nodeName === node.nodeName) {
				last.text += node.text;
				if (node.textType) last.textType = node.textType;
			} else {
				liveBlocks.value.push({
					nodeName: node.nodeName,
					text: node.text,
					textType: node.textType,
				});
			}
		} catch {
			/* ignore */
		}
	};
	const onComplete = () => finishStream(es, false);
	const onError = () => finishStream(es, true);
	es.addEventListener('complete', onComplete);
	es.addEventListener('error', onError);
	sendLock = false;
}

function finishStream(es: EventSource, fromError: boolean) {
	if (streamFinished) return;
	const hadContent = liveBlocks.value.length > 0;
	streamFinished = true;
	streaming.value = false;
	liveShowSteps.value = false;
	if (hadContent) {
		const blocks = [...liveBlocks.value];
		messages.value.push({
			role: 'assistant',
			content: '',
			blocks,
			answer: extractAnswer(blocks),
		});
	}
	liveBlocks.value = [];
	try {
		es.close();
	} catch {
		/* ignore */
	}
	if (activeES === es) activeES = null;
	if (fromError && !hadContent) {
		messages.value.push({ role: 'assistant', content: '⚠️ 流式连接失败，请检查令牌、代理或后端日志后重试。' });
	}
	flush();
	scheduleCharts();
}

function stop() {
	const es = activeES;
	if (es) {
		finishStream(es, false);
	} else {
		streaming.value = false;
		liveShowSteps.value = false;
	}
}

function onKeydown(e: KeyboardEvent) {
	if (e.key === 'Enter' && !e.shiftKey) {
		e.preventDefault();
		send();
	}
}

function answerHtml(m: Msg): string {
	return sanitizeMd(m.answer || extractAnswer(m.blocks || []) || m.content || '');
}

onMounted(async () => {
	const t0 = performance.now();
	// demo/host 可把 token 放 query，避免 postMessage 往返
	const qToken = String(route.query.token || route.query.sessionToken || '');
	if (qToken) {
		await setSessionToken(qToken);
	}
	window.parent?.postMessage({ source: 'dataagent-embed', type: 'ready' }, '*');
	window.addEventListener('message', onHostMessage);

	// config + preset 并行，不串行阻塞
	const configP = fetch(`/api/embed/public/${agentId.value}/config`)
		.then(async (res) => {
			if (res.ok) config.value = await res.json();
		})
		.catch(() => {});
	const presetP = fetch(`/api/embed/public/${agentId.value}/preset-questions`)
		.then(async (r) => {
			if (!r.ok) {
				suggested.value = FALLBACK_SUGGESTIONS;
				return;
			}
			const list = (await r.json()) as Array<{ question?: string; content?: string }>;
			const qs = list
				.map((x) => x.question || x.content || '')
				.map((s) => s.trim())
				.filter(Boolean);
			suggested.value = qs.length ? qs.slice(0, 6) : FALLBACK_SUGGESTIONS;
		})
		.catch(() => {
			suggested.value = FALLBACK_SUGGESTIONS;
		});
	await Promise.all([configP, presetP]);
	if (import.meta.dev) {
		console.info(
			`[embed-init] agent=${agentId.value} ui=${Math.round(performance.now() - t0)}ms ready=${ready.value}`,
		);
	}
});

onBeforeUnmount(() => {
	activeES?.close();
	window.removeEventListener('message', onHostMessage);
});
</script>

<template>
	<div class="embed-chat" :style="{ '--embed-primary': themeColor }">
		<header class="embed-chat__header">
			<span class="embed-chat__title">{{ title }}</span>
			<span v-if="streaming" class="embed-chat__status" aria-live="polite">
				<span class="embed-chat__status-dot" />
				分析中
			</span>
		</header>

		<div ref="listRef" class="embed-chat__scroll" @scroll="onListScroll">
			<div class="embed-chat__messages">
				<!-- Welcome（WeKnora 风格非对称气泡） -->
				<div v-if="showWelcome" class="embed-welcome">
					<p class="embed-welcome__text">{{ config?.welcomeMessage }}</p>
				</div>
				<div v-else-if="!ready && messages.length === 0" class="embed-welcome">
					<p class="embed-welcome__text muted">{{ initHint }}</p>
				</div>

				<!-- Suggested questions -->
				<div v-if="showSuggested" class="embed-suggested">
					<p class="embed-suggested__title">试试这样问</p>
					<div class="embed-suggested__grid">
						<button
							v-for="q in suggested"
							:key="q"
							type="button"
							class="embed-suggested__card"
							@click="send(q)"
						>
							<span>{{ q }}</span>
						</button>
					</div>
				</div>

				<!-- History -->
				<div v-for="(m, i) in messages" :key="i" class="msg-row">
					<div v-if="m.role === 'user'" class="msg-user">
						<div class="msg-user__bubble">{{ m.content }}</div>
					</div>
					<div v-else class="msg-bot">
						<!-- 分析过程（折叠） -->
						<div
							v-if="m.blocks && intermediateBlocks(m.blocks).length"
							class="pipeline"
						>
							<button
								type="button"
								class="pipeline__root"
								@click="toggleSteps(i)"
							>
								<span class="pipeline__icon" aria-hidden="true">
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none">
										<path
											d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
											stroke="currentColor"
											stroke-width="2"
											stroke-linecap="round"
											stroke-linejoin="round"
										/>
									</svg>
								</span>
								<span class="pipeline__summary">
									分析过程 · {{ intermediateBlocks(m.blocks).length }} 步
								</span>
								<span class="pipeline__chevron" :class="{ open: expandedSteps.has(i) }">›</span>
							</button>
							<div v-if="expandedSteps.has(i)" class="pipeline__children">
								<div
									v-for="(b, j) in intermediateBlocks(m.blocks)"
									:key="j"
									class="pipeline__step"
								>
									<div class="pipeline__step-name">{{ nodeLabel(b.nodeName) }}</div>
									<div class="markdown-body pipeline__step-body" v-html="renderBlockDetail(b)" />
								</div>
							</div>
						</div>

						<!-- 主答案 -->
						<div
							v-if="m.answer || m.content || (m.blocks && extractAnswer(m.blocks))"
							class="msg-bot__answer markdown-body"
							v-html="answerHtml(m)"
						/>
						<div
							v-for="(rs, ri) in resultSetBlocks(m.blocks)"
							:key="'rs-' + ri"
							class="msg-bot__artifact"
						>
							<div class="msg-bot__artifact-label">查询结果</div>
							<ChatResultSet :data="parseResultSet(rs.text)" :page-size="8" />
						</div>
					</div>
				</div>

				<!-- Live stream -->
				<div v-if="streaming" class="msg-row">
					<div class="msg-bot">
						<div
							v-if="!liveSteps.length && !displayedText"
							class="thinking-row"
							role="status"
							aria-live="polite"
						>
							<span class="thinking-row__pulse" aria-hidden="true" />
							<span>正在理解问题并规划查询…</span>
						</div>
						<div v-if="liveSteps.length" class="pipeline">
							<button type="button" class="pipeline__root" @click="liveShowSteps = !liveShowSteps">
								<span class="pipeline__icon" aria-hidden="true">
									<svg width="14" height="14" viewBox="0 0 24 24" fill="none">
										<path
											d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"
											stroke="currentColor"
											stroke-width="2"
											stroke-linecap="round"
											stroke-linejoin="round"
										/>
									</svg>
								</span>
								<span class="pipeline__summary">
									<span class="pipeline__pulse" />
									进行中 · {{ liveSteps.length }} 步<template v-if="liveStepsHidden">（显示最近 {{ liveStepsDisplay.length }}）</template> · {{ nodeLabel(liveSteps[liveSteps.length - 1]?.nodeName || '') }}
								</span>
								<span class="pipeline__chevron" :class="{ open: liveShowSteps }">›</span>
							</button>
							<div v-if="liveShowSteps" class="pipeline__children">
								<div v-if="liveStepsHidden" class="pipeline__more">已折叠较早 {{ liveStepsHidden }} 步</div>
								<div v-for="(b, j) in liveStepsDisplay" :key="j" class="pipeline__step">
									<div class="pipeline__step-name">{{ nodeLabel(b.nodeName) }}</div>
									<div class="markdown-body pipeline__step-body" v-html="renderBlockDetail(b)" />
								</div>
							</div>
						</div>

						<div
							v-if="displayedText"
							class="msg-bot__answer markdown-body streaming"
							v-html="liveAnswerHtml"
						/>
						<div
							v-for="(rs, ri) in resultSetBlocks(liveBlocks)"
							:key="'live-rs-' + ri"
							class="msg-bot__artifact"
						>
							<div class="msg-bot__artifact-label">查询结果</div>
							<ChatResultSet :data="parseResultSet(rs.text)" :page-size="8" />
						</div>
						<div
							v-if="!displayedText && !resultSetBlocks(liveBlocks).length"
							class="loading-typing"
							aria-label="生成中"
						>
							<span /><span /><span />
						</div>
					</div>
				</div>
			</div>
		</div>

		<button
			v-show="userHasScrolledUp"
			type="button"
			class="scroll-bottom"
			aria-label="滚到最新"
			@click="userHasScrolledUp = false; scrollToBottom(true)"
		>
			<svg width="18" height="18" viewBox="0 0 24 24" fill="none" aria-hidden="true">
				<path
					d="M6 9l6 6 6-6"
					stroke="currentColor"
					stroke-width="2"
					stroke-linecap="round"
					stroke-linejoin="round"
				/>
			</svg>
		</button>

		<footer class="embed-chat__input">
			<div class="input-shell">
				<textarea
					v-model="input"
					class="input-shell__field"
					rows="1"
					placeholder="输入你的问题…"
					:disabled="!ready || streaming"
					@keydown="onKeydown"
				/>
				<button
					v-if="!streaming"
					type="button"
					class="input-shell__send"
					:disabled="!ready || !input.trim()"
					@click="send()"
				>
					发送
				</button>
				<button v-else type="button" class="input-shell__send stop" @click="stop">停止</button>
			</div>
		</footer>
	</div>
</template>

<style scoped>
/* ── Shell（WeKnora embed-chat 布局） ─────────────────────────── */
.embed-chat {
	--embed-primary: var(--da-primary);
	--embed-bg: var(--da-surface-soft);
	--embed-surface: var(--da-surface);
	--embed-text: var(--da-ink);
	--embed-muted: var(--da-muted);
	--embed-border: var(--da-line-soft);
	display: flex;
	flex-direction: column;
	height: 100vh;
	background: var(--embed-bg);
	color: var(--embed-text);
	font-family: var(--da-font-sans);
	font-size: 16px;
	line-height: 1.625;
	-webkit-font-smoothing: antialiased;
	position: relative;
}

.embed-chat__header {
	flex-shrink: 0;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	padding: 12px 16px;
	background: color-mix(in srgb, var(--embed-primary) 10%, var(--embed-surface));
	color: var(--embed-text);
	font-weight: 600;
	font-size: 15px;
	letter-spacing: -0.01em;
	border-bottom: 1px solid var(--embed-border);
	backdrop-filter: blur(8px);
}
.embed-chat__title {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.embed-chat__status {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	font-size: 12px;
	font-weight: 600;
	color: var(--embed-primary);
}

.embed-chat__scroll {
	flex: 1;
	overflow-y: auto;
	padding: 16px 16px 8px;
}

.embed-chat__messages {
	display: flex;
	flex-direction: column;
	gap: 16px;
	max-width: 880px;
	margin: 0 auto;
	width: 100%;
}

/* ── Welcome ─────────────────────────────────────────────────── */
.embed-welcome {
	animation: welcome-in 0.28s ease both;
}
.embed-welcome__text {
	margin: 0;
	max-width: min(88%, 520px);
	padding: 10px 14px;
	font-size: 14px;
	line-height: 1.55;
	color: var(--embed-text);
	white-space: pre-wrap;
	word-break: break-word;
	background: color-mix(in srgb, var(--embed-primary) 7%, var(--da-surface));
	border: 1px solid color-mix(in srgb, var(--embed-primary) 14%, var(--da-line-soft));
	border-radius: 4px 14px 14px 14px;
	box-shadow: var(--da-shadow-sm);
}
.embed-welcome__text.muted {
	color: var(--embed-muted);
}
@keyframes welcome-in {
	from {
		opacity: 0;
		transform: translateY(6px);
	}
	to {
		opacity: 1;
		transform: translateY(0);
	}
}

/* ── Suggested ───────────────────────────────────────────────── */
.embed-suggested__title {
	margin: 0 0 8px;
	font-size: 13px;
	color: var(--embed-muted);
	font-weight: 500;
}
.embed-suggested__grid {
	display: grid;
	grid-template-columns: 1fr 1fr;
	gap: 8px;
}
@media (max-width: 480px) {
	.embed-suggested__grid {
		grid-template-columns: 1fr;
	}
}
.embed-suggested__card {
	text-align: left;
	padding: 10px 12px;
	border-radius: var(--da-radius-md);
	border: 1px solid var(--embed-border);
	background: var(--embed-surface);
	font-size: 13px;
	line-height: 1.45;
	color: var(--embed-text);
	cursor: pointer;
	transition:
		border-color 0.18s ease,
		box-shadow 0.18s ease,
		transform 0.18s ease;
	min-height: 44px;
}
.embed-suggested__card:hover {
	border-color: color-mix(in srgb, var(--embed-primary) 45%, var(--embed-border));
	box-shadow: var(--da-shadow-md);
	transform: translateY(-1px);
}
.embed-suggested__card:focus-visible {
	outline: 2px solid var(--embed-primary);
	outline-offset: 2px;
}

/* ── Messages ────────────────────────────────────────────────── */
.msg-row {
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.msg-user {
	display: flex;
	justify-content: flex-end;
}
.msg-user__bubble {
	max-width: min(88%, 560px);
	padding: 10px 14px;
	background: color-mix(in srgb, var(--embed-primary) 12%, var(--da-surface));
	border: 1px solid color-mix(in srgb, var(--embed-primary) 18%, var(--da-line-soft));
	border-radius: 14px 4px 14px 14px;
	font-size: 14px;
	line-height: 1.55;
	white-space: pre-wrap;
	word-break: break-word;
}
.msg-bot {
	display: flex;
	flex-direction: column;
	gap: 10px;
	max-width: 100%;
	padding: 2px 0;
}
.msg-bot__artifact {
	margin-top: 8px;
	padding: 0 0 8px;
	background: var(--da-surface);
	border: 1px solid color-mix(in srgb, var(--embed-primary) 22%, var(--da-line-soft));
	border-radius: 10px;
	overflow: hidden;
	box-shadow: var(--da-shadow-sm);
}
.msg-bot__artifact-label {
	font-size: 11px;
	font-weight: 600;
	color: var(--embed-muted);
	padding: 8px 12px 4px;
}
.msg-bot__answer {
	/* 答案区不包重边框气泡，贴近 WeKnora content-wrapper */
	padding: 2px 0;
}

/* ── Pipeline（折叠分析过程） ─────────────────────────────────── */
.pipeline {
	display: flex;
	flex-direction: column;
	gap: 0;
}
.pipeline__root {
	display: flex;
	align-items: center;
	gap: 8px;
	width: 100%;
	padding: 8px 10px;
	border: 1px solid var(--embed-border);
	border-radius: 8px;
	background: var(--da-surface-soft);
	cursor: pointer;
	font-size: 13px;
	color: var(--embed-muted);
	transition: background 0.15s ease;
	min-height: 40px;
	text-align: left;
}
.pipeline__root:hover {
	background: var(--da-surface-soft);
}
.pipeline__icon {
	display: inline-flex;
	color: var(--embed-primary);
	flex-shrink: 0;
}
.pipeline__summary {
	flex: 1;
	display: flex;
	align-items: center;
	gap: 6px;
	font-weight: 500;
	color: var(--da-ink);
}
.pipeline__pulse {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--embed-primary);
	animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
	0%,
	100% {
		opacity: 0.35;
		transform: scale(0.85);
	}
	50% {
		opacity: 1;
		transform: scale(1);
	}
}
.pipeline__chevron {
	font-size: 18px;
	line-height: 1;
	transform: rotate(0deg);
	transition: transform 0.18s ease;
	color: var(--da-muted);
}
.pipeline__chevron.open {
	transform: rotate(90deg);
}
.pipeline__children {
	margin: 4px 0 0 10px;
	padding-left: 12px;
	border-left: 2px solid color-mix(in srgb, var(--embed-primary) 25%, var(--da-line-soft));
	display: flex;
	flex-direction: column;
	gap: 8px;
}
.pipeline__step {
	padding: 6px 0 6px 4px;
}
.pipeline__step-name {
	font-size: 12px;
	font-weight: 600;
	color: var(--embed-primary);
	margin-bottom: 4px;
}
.pipeline__step-body {
	font-size: 13px !important;
	opacity: 0.92;
	max-height: 240px;
	overflow: auto;
}

/* ── Typing ──────────────────────────────────────────────────── */
.loading-typing {
	display: flex;
	align-items: center;
	gap: 4px;
	height: 32px;
	padding-left: 2px;
}
.loading-typing span {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--embed-primary);
	animation: typingBounce 1.4s ease-in-out infinite;
}
.loading-typing span:nth-child(2) {
	animation-delay: 0.2s;
}
.loading-typing span:nth-child(3) {
	animation-delay: 0.4s;
}
@keyframes typingBounce {
	0%,
	60%,
	100% {
		transform: translateY(0);
	}
	30% {
		transform: translateY(-8px);
	}
}

/* ── Scroll to bottom ────────────────────────────────────────── */
.scroll-bottom {
	position: absolute;
	left: 50%;
	transform: translateX(-50%);
	bottom: 88px;
	z-index: 10;
	width: 36px;
	height: 36px;
	border-radius: 50%;
	background: var(--embed-surface);
	border: 1px solid var(--embed-border);
	box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
	display: flex;
	align-items: center;
	justify-content: center;
	cursor: pointer;
	color: var(--embed-muted);
	transition:
		opacity 0.2s ease,
		transform 0.2s ease;
}
.scroll-bottom:hover {
	color: var(--embed-text);
}

/* ── Input ───────────────────────────────────────────────────── */
.embed-chat__input {
	/* DEEIX floating composer base */
	flex-shrink: 0;
	padding: 8px 16px 16px;
	background: linear-gradient(to top, var(--embed-bg) 70%, transparent);
}
.input-shell {
	display: flex;
	gap: 8px;
	align-items: flex-end;
	max-width: 880px;
	margin: 0 auto;
	padding: 10px 12px;
	background: var(--embed-surface);
	border: 1px solid var(--embed-border);
	border-radius: var(--da-composer-radius, 18px);
	box-shadow: var(--da-shadow-composer, var(--da-shadow-md));
	transition: border-color var(--da-dur-fast) var(--da-ease-out),
		box-shadow var(--da-dur-fast) var(--da-ease-out);
}
.input-shell:focus-within {
	border-color: color-mix(in srgb, var(--embed-primary) 50%, var(--embed-border));
	box-shadow: 0 0 0 3px color-mix(in srgb, var(--embed-primary) 14%, transparent),
		var(--da-shadow-composer, var(--da-shadow-md));
}
.input-shell__field {
	flex: 1;
	border: none;
	outline: none;
	resize: none;
	font: inherit;
	font-size: 14px;
	line-height: 1.5;
	max-height: 120px;
	min-height: 24px;
	padding: 6px 4px;
	background: transparent;
	color: var(--embed-text);
}
.input-shell__field:disabled {
	opacity: 0.6;
}
.input-shell__send {
	flex-shrink: 0;
	border: none;
	border-radius:  999px;
	padding: 0 16px;
	height: 36px;
	min-width: 64px;
	background: var(--embed-primary);
	color: white;
	font-size: 14px;
	font-weight: 500;
	cursor: pointer;
	transition: opacity 0.15s ease;
}
.input-shell__send:disabled {
	opacity: 0.45;
	cursor: not-allowed;
}
.input-shell__send.stop {
	background: var(--da-muted);
}
.input-shell__send:not(:disabled):hover {
	filter: brightness(0.96);
}

/* ── Markdown（对齐 WeKnora chat-markdown 关键） ──────────────── */
.markdown-body {
	font-size: 16px;
	line-height: 1.625;
	color: var(--embed-text);
	word-break: break-word;
}
.markdown-body.streaming :deep(> :last-child::after) {
	content: '';
	display: inline-block;
	width: 6px;
	height: 6px;
	margin-left: 4px;
	border-radius: 50%;
	background: var(--embed-primary);
	vertical-align: middle;
	animation: typingBounce 1s ease-in-out infinite;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3),
.markdown-body :deep(h4) {
	margin: 1.1em 0 0.45em;
	line-height: 1.35;
	font-weight: 600;
	color: var(--da-ink);
}
.markdown-body :deep(h1) {
	font-size: 1.35em;
}
.markdown-body :deep(h2) {
	font-size: 1.2em;
}
.markdown-body :deep(h3) {
	font-size: 1.05em;
}
.markdown-body :deep(p) {
	margin: 0 0 0.25em;
	line-height: 1.625;
}
.markdown-body :deep(p + p) {
	margin-top: 0.75em;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
	margin: 0.5em 0 0.75em;
	padding-left: 1.4em;
}
.markdown-body :deep(li) {
	margin: 0.2em 0;
}
.markdown-body :deep(strong) {
	font-weight: 600;
}
.markdown-body :deep(a) {
	color: var(--embed-primary);
	text-decoration: underline;
	text-decoration-style: dotted;
	text-underline-offset: 2px;
}
.markdown-body :deep(code:not(pre code)) {
	background: var(--da-surface-soft);
	padding: 0.12em 0.4em;
	border-radius: 999px;
	font-size: 0.88em;
	font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
.markdown-body :deep(pre) {
	background: var(--da-ink);
	color: var(--da-line-soft);
	padding: 12px 14px;
	border-radius: 10px;
	overflow-x: auto;
	font-size: 13px;
	line-height: 1.5;
	margin: 0.65em 0;
}
.markdown-body :deep(pre code) {
	font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
	background: transparent;
	padding: 0;
}
.markdown-body :deep(blockquote) {
	margin: 0.75em 0;
	padding: 0.35em 0 0.35em 12px;
	border-left: 3px solid color-mix(in srgb, var(--embed-primary) 55%, var(--da-line));
	color: var(--da-muted);
}
.markdown-body :deep(table) {
	border-collapse: collapse;
	width: 100%;
	margin: 0.75em 0;
	font-size: 14px;
	display: block;
	overflow-x: auto;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
	border: 1px solid var(--da-line-soft);
	padding: 8px 10px;
	text-align: left;
}
.markdown-body :deep(th) {
	background: var(--da-surface-soft);
	font-weight: 600;
}
.markdown-body :deep(tr:nth-child(even) td) {
	background: var(--da-surface-soft);
}
.markdown-body :deep(.md-echarts) {
	width: 100%;
	min-height: 300px;
	margin: 12px 0;
	border-radius: 10px;
	background: var(--da-surface-soft);
}
.markdown-body :deep(.md-echarts-skeleton) {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 8px;
	min-height: 140px;
	background: linear-gradient(90deg, var(--da-surface-soft) 25%, var(--da-line-soft) 50%, var(--da-surface-soft) 75%);
	background-size: 200% 100%;
	animation: shimmer 1.2s ease-in-out infinite;
	border-radius: 10px;
	color: var(--embed-muted);
	font-size: 13px;
	margin: 12px 0;
}
@keyframes shimmer {
	0% {
		background-position: 200% 0;
	}
	100% {
		background-position: -200% 0;
	}
}

@media (prefers-reduced-motion: reduce) {
	.embed-welcome,
	.loading-typing span,
	.pipeline__pulse,
	.markdown-body.streaming :deep(> :last-child::after),
	.markdown-body :deep(.md-echarts-skeleton) {
		animation: none !important;
	}
}

/* R3 polish: answer surface + process micro-interactions */
.msg-bot__answer {
	letter-spacing: -0.01em !important;
	box-shadow: var(--da-shadow-sm);
	animation: da-soft-in 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
}
.pipeline__root:hover {
	background: color-mix(in srgb, var(--embed-primary) 6%, var(--da-surface));
}
.pipeline__chevron {
	transition: transform 0.22s cubic-bezier(0.22, 1, 0.36, 1);
}
.msg-bot__artifact {
	animation: da-soft-in 0.38s cubic-bezier(0.22, 1, 0.36, 1) both;
}

.embed-chat__header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
}
.embed-chat__status {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	font-size: 12px;
	font-weight: 500;
	opacity: 0.95;
}
.embed-chat__status-dot {
	width: 7px;
	height: 7px;
	border-radius: 50%;
	background: var(--da-surface);
	animation: pendingPulse 1s ease-in-out infinite;
}
.thinking-row {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 8px 2px 10px;
	font-size: 13px;
	color: var(--embed-muted);
}
.thinking-row__pulse {
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: var(--embed-primary);
	animation: pendingPulse 1s ease-in-out infinite;
}
@keyframes pendingPulse {
	0%, 100% { opacity: 0.4; transform: scale(0.9); }
	50% { opacity: 1; transform: scale(1.15); }
}
</style>
