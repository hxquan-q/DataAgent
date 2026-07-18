/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

<template>
	<div ref="listRef" class="message-list custom-scrollbar">
		<!-- R6: compact context strip when chatting -->
		<div
			v-if="store.currentAgentName && !showWelcome"
			class="chat-status-strip"
			role="status"
			:aria-busy="store.isStreaming ? 'true' : 'false'"
		>
			<span class="chat-status-strip__agent">{{ store.currentAgentName }}</span>
			<span v-if="store.activeChatModel || store.activeModelConfig?.modelName" class="chat-status-strip__model">
				{{ store.activeModelConfig?.modelName || store.activeChatModel }}
			</span>
			<button
				type="button"
				class="chat-status-strip__ready"
				:class="{ ok: stripHasModel }"
				:title="stripHasModel ? 'CHAT 模型就绪' : '点击配置 CHAT 模型'"
				@click="goModels"
			>
				模型
			</button>
			<button
				type="button"
				class="chat-status-strip__ready"
				:class="{ ok: stripHasDs }"
				:title="stripHasDs ? '数据源就绪' : '点击绑定数据源'"
				@click="goDatasource"
			>
				数据源
			</button>
			<span v-if="store.isStreaming" class="chat-status-strip__live">
				分析中<span v-if="streamElapsed > 0"> · {{ streamElapsed }}s</span>
			</span>
			<button
				type="button"
				class="chat-status-strip__switch"
				:disabled="store.isStreaming"
				@click="goSwitchAgent"
			>
				切换智能体
			</button>
		</div>
		<!-- Empty: no session, or session with no messages yet -->
		<ChatWelcome v-if="showWelcome" />

		<!-- Messages -->
		<template v-else>
			<div class="messages-inner">
				<template v-for="message in filteredMessages" :key="message.id">
					<div class="message-wrapper da-msg-enter">
						<!-- ── User message ─────────────────────────────────── -->
						<div v-if="message.role === 'user'" class="row user-row">
							<v-card class="user-card" elevation="0">
								<span
									v-html="escapeHtml(message.content).replace(/\n/g, '<br>')"
								/>
							</v-card>
							<v-avatar
								color="primary"
								variant="tonal"
								size="32"
								rounded="lg"
								class="avatar"
							>
								<v-icon size="18" color="primary">mdi-account</v-icon>
							</v-avatar>
						</div>

						<!-- ── AI messages ──────────────────────────────────── -->
						<div v-else class="row ai-row">
							<v-avatar
								color="primary"
								size="32"
								rounded="lg"
								class="avatar"
							>
								<v-icon size="18" color="white">mdi-robot</v-icon>
							</v-avatar>

							<!-- HTML node message -->
							<v-card
								v-if="message.messageType === 'html'"
								class="ai-card"
								elevation="0"
							>
								<div class="md-body" v-html="sanitizeHtml(message.content)" />
							</v-card>

							<!-- Result Set -->
							<v-card
								v-else-if="message.messageType === 'result-set'"
								class="ai-card"
								elevation="0"
							>
								<ChatResultSet
									:data="safeParseJson(message.content)"
									:page-size="store.requestOptions.pageSize"
								/>
							</v-card>

							<!-- Markdown Report -->
							<v-card
								v-else-if="message.messageType === 'markdown-report'"
								class="ai-card report-card"
								elevation="0"
							>
								<ChatMarkdownReport :content="message.content" />
							</v-card>

							<!-- Timeline + answer-first report (R210) -->
							<template v-else-if="message.messageType === 'timeline'">
								<v-card
									v-if="extractReportContent(message.content)"
									class="ai-card report-card mb-2"
									elevation="0"
								>
									<ChatMarkdownReport
										:content="extractReportContent(message.content)!"
									/>
								</v-card>
								<v-card class="ai-card timeline-card" elevation="0">
									<ChatWorkflowTimeline
										:node-blocks="safeParseBlocks(message.content)"
										:completed="true"
									/>
								</v-card>
							</template>

							<!-- Warning (user stopped) -->
							<div
								v-else-if="message.messageType === 'warning'"
								class="status-banner status-banner--warning"
							>
								<v-icon size="16" class="mr-2">mdi-alert</v-icon>
								{{ message.content }}
							</div>

							<!-- Error -->
							<div
								v-else-if="message.messageType === 'error'"
								class="status-banner status-banner--error"
							>
								<v-icon size="16" class="mr-2">mdi-alert-circle</v-icon>
								{{ message.content }}
							</div>

							<!-- Plain AI text (render as markdown) -->
							<v-card v-else class="ai-card" elevation="0">
								<div class="md-body" v-html="renderMarkdown(message.content)" />
							</v-card>
						</div>
					</div>

				</template>

				<!-- ── Streaming: Report first (R210 answer-first) ── -->
				<div
					v-if="store.isReportStreaming && store.streamingReportContent"
					class="row ai-row"
				>
					<v-avatar color="primary" size="32" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card report-card" elevation="0">
						<ChatStreamingReport :content="store.streamingReportContent" />
					</v-card>
				</div>

				<!-- ── Streaming: Workflow Timeline (secondary) ── -->
				<div
					v-if="store.isStreaming && store.nodeBlocks.length > 0"
					class="row ai-row"
				>
					<v-avatar
						color="primary"
						size="32"
						rounded="lg"
						class="avatar"
						:style="
							store.isReportStreaming && store.streamingReportContent
								? 'visibility: hidden'
								: undefined
						"
					>
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card timeline-card" elevation="0">
						<ChatWorkflowTimeline :node-blocks="store.nodeBlocks" />
					</v-card>
				</div>

				<!-- ── Streaming spinner (before first node arrives) ── -->
				<div
					v-else-if="store.isStreaming && store.nodeBlocks.length === 0"
					class="row ai-row"
				>
					<v-avatar color="primary" size="32" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card thinking-card" elevation="0">
						<div class="thinking-row" role="status" aria-live="polite">
							<div class="thinking-dots" aria-hidden="true">
								<span class="dot" />
								<span class="dot dot--2" />
								<span class="dot dot--3" />
							</div>
							<span class="thinking-label">正在分析…</span>
						</div>
					</v-card>
				</div>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick, onUnmounted } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useChatStore } from '~/stores/chat';
import type { ResultData } from '~/services/resultSet/index';
import type { ChatMessage } from '~/services/chat/index';
import ChatWelcome from './ChatWelcome.vue';
import ChatResultSet from './ChatResultSet.vue';
import ChatMarkdownReport from './ChatMarkdownReport.vue';
import ChatWorkflowTimeline from './ChatWorkflowTimeline.vue';
import ChatStreamingReport from './ChatStreamingReport.vue';

const TIMELINE_ABSORBED_TYPES = new Set([
	'result-set',
	'markdown-report',
	'html',
]);

const store = useChatStore();

function goModels() {
	navigateTo('/system/model-config');
}
function goDatasource() {
	const id = store.currentAgentId;
	if (id) {
		navigateTo({ path: '/system/data-sources', query: { agentId: String(id) } });
	} else {
		navigateTo('/system/data-sources');
	}
}
function goSwitchAgent() {
	navigateTo('/system/agents');
}

const streamElapsed = ref(0);
let streamTimer: ReturnType<typeof setInterval> | null = null;
watch(
	() => store.isStreaming,
	(streaming) => {
		if (streaming) {
			streamElapsed.value = 0;
			if (streamTimer) clearInterval(streamTimer);
			streamTimer = setInterval(() => {
				streamElapsed.value += 1;
			}, 1000);
		} else if (streamTimer) {
			clearInterval(streamTimer);
			streamTimer = null;
		}
	},
);
onUnmounted(() => {
	if (streamTimer) {
		clearInterval(streamTimer);
		streamTimer = null;
	}
});

const stripHasModel = computed(
	() => store.chatModels.length > 0 && !!store.activeModelConfig,
);
const stripHasDs = computed(
	() => store.allDatasources.length > 0 && !!store.activeDatasource,
);


const listRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();

// Welcome when idle empty (session may already exist after loadSessions)
const showWelcome = computed(
	() =>
		!store.isStreaming &&
		!store.isReportStreaming &&
		(!store.currentSession || store.currentMessages.length === 0),
);

const filteredMessages = computed<ChatMessage[]>(() => {
	const msgs = store.currentMessages;
	if (!msgs.length) return msgs;

	const result: ChatMessage[] = [];
	for (let i = 0; i < msgs.length; i++) {
		const msg = msgs[i];
		if (!msg) continue;
		if (
			msg.role === 'assistant' &&
			TIMELINE_ABSORBED_TYPES.has(msg.messageType)
		) {
			const surroundHasTimeline = msgs.some(
				(m, j) =>
					j !== i &&
					m.role === 'assistant' &&
					m.messageType === 'timeline' &&
					m.sessionId === msg.sessionId,
			);
			if (surroundHasTimeline) continue;
		}
		result.push(msg);
	}
	return result;
});

const SANITIZE_OPTIONS = {
	ADD_TAGS: ['div'],
	ADD_ATTR: ['style', 'class'],
	RETURN_TRUSTED_TYPE: false as const,
};

function renderMarkdown(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(
		renderMarkdownContent(content),
		SANITIZE_OPTIONS,
	) as string;
}

function sanitizeHtml(content: string): string {
	if (!content) return '';
	return DOMPurify.sanitize(content, SANITIZE_OPTIONS) as string;
}

function safeParseJson(content: string): ResultData | null {
	try {
		return JSON.parse(content);
	} catch {
		return null;
	}
}

function safeParseBlocks(content: string) {
	try {
		return JSON.parse(
			content,
		) as import('~/services/graph/index').GraphNodeResponse[][];
	} catch {
		return [];
	}
}

function extractReportContent(timelineJson: string): string | null {
	try {
		const blocks = JSON.parse(
			timelineJson,
		) as import('~/services/graph/index').GraphNodeResponse[][];
		// R215: 兼容 textType 大小写/别名，并扫描 block 内任意节点
		for (const block of blocks) {
			for (const node of block || []) {
				if (node?.nodeName !== 'ReportGeneratorNode' || !node?.text) continue;
				const tt = String(node.textType || '').toUpperCase();
				if (tt === 'MARK_DOWN' || tt === 'MARKDOWN' || tt === 'MD' || tt === 'HTML' || !tt) {
					return node.text;
				}
			}
		}
	} catch {
		/* ignore */
	}
	return null;
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

let scrollRafId: number | null = null;
function scrollToBottom() {
	if (scrollRafId) cancelAnimationFrame(scrollRafId);
	scrollRafId = requestAnimationFrame(() => {
		if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight;
		scrollRafId = null;
	});
}

watch(
	() => store.currentMessages.length,
	() => {
		scrollToBottom();
		nextTick(() => renderECharts(listRef.value));
	},
);
watch(
	() => store.nodeBlocks,
	() => scrollToBottom(),
	{ deep: true },
);
watch(
	() => store.streamingReportContent,
	() => scrollToBottom(),
);
watch(
	() => store.isStreaming,
	(v) => {
		if (v) scrollToBottom();
	},
);
</script>

<style scoped>
/* ── Layout ──────────────────────────────────────────────────────────────────── */
.message-list {
	flex: 1;
	overflow-y: auto;
	display: flex;
	flex-direction: column;
	background:
		radial-gradient(1000px 380px at 50% -100px, color-mix(in srgb, var(--da-primary, #2f84d6) 8%, transparent), transparent 62%),
		var(--da-surface-soft);
}

.messages-inner {
	padding: 18px 20px 28px;
	display: flex;
	flex-direction: column;
	gap: 16px;
	width: 100%;
	max-width: var(--da-chat-max, 960px);
	margin: 0 auto;
}

.message-wrapper {
	/* enter animation via .da-msg-enter */
}

/* ── Row (shared by user + AI) ───────────────────────────────────────────────── */
.row {
	display: flex;
	align-items: flex-start;
	gap: 6px;
}

.user-row {
	justify-content: flex-end;
}

.ai-row {
	justify-content: flex-start;
}

/* ── Avatar ──────────────────────────────────────────────────────────────────── */
.avatar {
	flex-shrink: 0;
	margin-top: 2px;
}

/* ── User card ───────────────────────────────────────────────────────────────── */
.user-card {
	background: linear-gradient(
		135deg,
		var(--da-primary, #2f84d6) 0%,
		var(--da-accent, #3b9eea) 100%
	) !important;
	color: white !important;
	padding: 10px 14px;
	border-radius: 18px 18px 6px 18px !important;
	font-size: 13.5px;
	line-height: 1.55;
	max-width: min(62%, 560px);
	word-break: break-word;
	box-shadow: 0 8px 20px color-mix(in srgb, var(--da-primary, #2f84d6) 24%, transparent) !important;
	letter-spacing: -0.01em;
}

/* ── AI card ─────────────────────────────────────────────────────────────────── */
.ai-card {
	padding: 12px 14px;
	border-radius: 6px 18px 18px 18px !important;
	font-size: 13.5px;
	line-height: 1.65;
	max-width: min(82%, 760px);
	word-break: break-word;
	color: var(--da-ink, #1a2332);
	background: var(--da-surface, #fff) !important;
	border: 1px solid var(--da-line-soft, #e4edf5) !important;
	box-shadow: var(--da-shadow-sm) !important;
	letter-spacing: -0.01em;
	transition: box-shadow var(--da-dur-base, 0.2s) var(--da-ease-out),
		border-color var(--da-dur-base, 0.2s) var(--da-ease-out);
}

.ai-card:hover {
	border-color: color-mix(in srgb, var(--da-primary, #2f84d6) 22%, var(--da-line-soft, #e4edf5)) !important;
	box-shadow: var(--da-shadow-md) !important;
}

/* Report card: answer primary — stronger frame */
.report-card {
	max-width: 100% !important;
	padding: 0 !important;
	flex: 1;
	min-width: 0;
	border: 1px solid color-mix(in srgb, var(--da-primary, #2f84d6) 28%, transparent) !important;
	box-shadow: var(--da-shadow-lg) !important;
	overflow: hidden;
	border-radius: var(--da-radius-md, 14px) !important;
}

/* Timeline card: process secondary (DEEIX hierarchy) */
.timeline-card {
	padding: 8px 10px;
	max-width: 100% !important;
	flex: 1;
	min-width: 0;
	background: color-mix(in srgb, var(--da-surface-soft) 88%, var(--da-surface)) !important;
	border: 1px dashed color-mix(in srgb, var(--da-line) 55%, transparent) !important;
	box-shadow: none !important;
	border-radius: var(--da-radius-md) !important;
	opacity: 0.96;
}
.timeline-card:hover {
	opacity: 1;
	border-color: color-mix(in srgb, var(--da-primary) 22%, var(--da-line-soft)) !important;
}

/* ── Thinking feedback (WeKnora-like status) ─────────────────────────────────── */
.thinking-card {
	padding: 10px 14px !important;
	background: color-mix(in srgb, var(--da-primary-soft) 55%, var(--da-surface)) !important;
	border: 1px solid color-mix(in srgb, var(--da-primary) 18%, var(--da-line-soft)) !important;
	box-shadow: none !important;
}
.thinking-row {
	display: flex;
	align-items: center;
	gap: 8px;
}
.thinking-label {
	font-size: 12.5px;
	font-weight: 500;
	color: var(--da-muted);
	letter-spacing: -0.01em;
}
.thinking-dots {
	display: flex;
	align-items: center;
	gap: 5px;
	padding: 2px 0;
}
.dot {
	width: 6px;
	height: 6px;
	background: var(--da-primary);
	border-radius: 50%;
	animation: dotBounce 1.2s infinite;
	opacity: 0.55;
}
.dot--2 {
	animation-delay: 0.2s;
}
.dot--3 {
	animation-delay: 0.4s;
}
@keyframes dotBounce {
	0%,
	60%,
	100% {
		transform: translateY(0);
	}
	30% {
		transform: translateY(-5px);
	}
}
@media (prefers-reduced-motion: reduce) {
	.dot {
		animation: none;
		opacity: 0.7;
	}
}

/* ── Markdown body inside AI card ────────────────────────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
	font-weight: 700;
	margin: 10px 0 4px;
	line-height: 1.4;
}
.md-body :deep(p) {
	margin-bottom: 6px;
}
.md-body :deep(ul),
.md-body :deep(ol) {
	padding-left: 20px;
	margin-bottom: 5px;
}
.md-body :deep(li) {
	margin-bottom: 2px;
}
.md-body :deep(code:not(pre code)) {
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	padding: 1px 4px;
	border-radius: var(--da-radius-sm);
	font-size: 12.5px;
	font-family: var(--da-font-mono);
	color: color-mix(in srgb, var(--da-primary) 55%, #be185d);
}
.md-body :deep(blockquote) {
	border-left: 3px solid var(--da-accent);
	padding-left: 12px;
	color: var(--da-muted);
	margin: 4px 0;
}
.md-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 8px 0;
	display: block;
	overflow-x: auto;
}
.md-body :deep(thead) {
	display: table-header-group;
}
.md-body :deep(tbody) {
	display: table-row-group;
}
.md-body :deep(tr) {
	display: table-row;
	border-top: 1px solid var(--da-line);
}
.md-body :deep(th) {
	display: table-cell;
	background: var(--da-surface-soft);
	padding: 6px 10px;
	border: 1px solid var(--da-line-soft);
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.md-body :deep(td) {
	display: table-cell;
	padding: 6px 10px;
	border: 1px solid var(--da-line-soft);
	font-size: 13px;
}
.md-body :deep(tr:nth-child(even)) {
	background: var(--da-surface-soft);
}
.md-body :deep(a) {
	color: var(--da-primary);
	text-decoration: underline;
}
.md-body :deep(hr) {
	border: none;
	border-top: 1px solid var(--da-line-soft);
	margin: 8px 0;
}
.md-body :deep(strong) {
	font-weight: 700;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) {
	margin: 8px 0;
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-sm);
	overflow: auto;
	background: var(--da-surface-soft);
}
.md-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: var(--da-surface-soft);
	padding: 3px 6px;
	border-bottom: 1px solid var(--da-line-soft);
	font-size: 11px;
}
.md-body :deep(.code-language) {
	color: var(--da-muted);
	font-weight: 600;
	font-family: var(--da-font-mono);
	font-size: 9.5px;
	text-transform: uppercase;
}
.md-body :deep(.code-copy-button) {
	background: transparent;
	border: 1px solid var(--da-line);
	padding: 2px 8px;
	border-radius: var(--da-radius-sm);
	font-size: 10px;
	cursor: pointer;
	transition: all 0.2s;
	color: var(--da-ink);
}
.md-body :deep(.code-copy-button:hover) {
	background: var(--da-surface-soft);
	border-color: var(--da-line);
}
.md-body :deep(.code-copy-button.copied) {
	background: var(--da-success);
	border-color: var(--da-success);
	color: white;
}
.md-body :deep(pre.hljs) {
	margin: 0;
	padding: 8px;
	overflow-x: auto;
	overflow-y: hidden;
	background: var(--da-surface-soft);
	font-size: 11.5px;
	line-height: 1.4;
	white-space: pre;
}
.md-body :deep(pre.hljs code) {
	display: block;
	padding: 0;
	margin: 0;
	background: transparent;
	border: none;
	font-family: var(--da-font-mono);
	color: inherit;
	white-space: pre;
	min-width: max-content;
}

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar {
	width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
	background: var(--da-line);
	border-radius: var(--da-radius-sm);
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: var(--da-muted);
}

/* ── Status banners (warning / error) ────────────────────────────────────────── */
 .status-banner {
	display: flex;
	align-items: center;
	padding: 8px 12px;
	border-radius: var(--da-radius-sm);
	font-size: 13px;
	font-weight: 500;
	line-height: 1.5;
	max-width: 75%;
}
.status-banner--warning {
	background: color-mix(in srgb, var(--da-warning) 10%, white);
	border: 1px solid color-mix(in srgb, var(--da-warning) 40%, white);
	color: var(--da-warning);
}
.status-banner--error {
	background: color-mix(in srgb, var(--da-danger) 8%, white);
	border: 1px solid color-mix(in srgb, var(--da-danger) 35%, white);
	color: var(--da-danger);
}

.chat-status-strip {
	display: flex;
	align-items: center;
	gap: 6px;
	flex-wrap: wrap;
	padding: 8px 12px;
	max-width: var(--da-chat-max, 960px);
	width: calc(100% - 40px);
	margin: 10px auto 0;
	box-sizing: border-box;
	font-size: 11px;
	color: var(--da-muted);
	background: color-mix(in srgb, var(--da-surface) 88%, var(--da-primary-soft));
	border: 1px solid var(--da-line-soft);
	border-radius: 999px;
	box-shadow: var(--da-shadow-sm);
}
.chat-status-strip__agent {
	font-weight: 600;
	color: var(--da-ink);
}
.chat-status-strip__model {
	padding: 2px 8px;
	border-radius: 999px;
	background: var(--da-primary-soft);
	color: var(--da-primary);
	font-weight: 500;
}
.chat-status-strip__live {
	padding: 2px 8px;
	border-radius: 999px;
	background: color-mix(in srgb, var(--da-success) 10%, white);
	color: var(--da-success);
	font-weight: 600;
}

.chat-status-strip__ready {
	appearance: none;
	cursor: pointer;
	font: inherit;
	padding: 2px 8px;
	border-radius: 999px;
	font-size: 11px;
	font-weight: 600;
	background: color-mix(in srgb, var(--da-warning) 12%, white);
	color: var(--da-warning);
	border: 1px solid color-mix(in srgb, var(--da-warning) 35%, white);
}
.chat-status-strip__ready.ok {
	background: color-mix(in srgb, var(--da-success) 10%, white);
	color: var(--da-success);
	border-color: color-mix(in srgb, var(--da-success) 30%, white);
}

.chat-status-strip__switch {
	appearance: none;
	margin-left: auto;
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface);
	color: var(--da-primary);
	border-radius: 999px;
	padding: 2px 10px;
	font: inherit;
	font-size: 11px;
	font-weight: 600;
	cursor: pointer;
	transition: background var(--da-dur-fast) var(--da-ease-out);
}
.chat-status-strip__switch:hover:not(:disabled) {
	background: var(--da-primary-soft);
}
.chat-status-strip__switch:disabled {
	opacity: var(--da-disabled-opacity);
	cursor: not-allowed;
}
.chat-status-strip__switch:focus-visible,
.chat-status-strip__ready:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}
</style>
