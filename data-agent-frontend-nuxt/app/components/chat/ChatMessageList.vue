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
		>
			<span class="chat-status-strip__agent">{{ store.currentAgentName }}</span>
			<span v-if="store.activeChatModel || store.activeModelConfig?.modelName" class="chat-status-strip__model">
				{{ store.activeModelConfig?.modelName || store.activeChatModel }}
			</span>
			<span v-if="store.isStreaming" class="chat-status-strip__live">分析中</span>
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
							<v-card class="user-card" elevation="1">
								<span
									v-html="escapeHtml(message.content).replace(/\n/g, '<br>')"
								/>
							</v-card>
							<v-avatar
								color="grey-darken-2"
								size="32"
								rounded="lg"
								class="avatar"
							>
								<v-icon size="18" color="white">mdi-account</v-icon>
							</v-avatar>
						</div>

						<!-- ── AI messages ──────────────────────────────────── -->
						<div v-else class="row ai-row">
							<v-avatar
								color="blue-darken-3"
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
								elevation="1"
							>
								<div class="md-body" v-html="sanitizeHtml(message.content)" />
							</v-card>

							<!-- Result Set -->
							<v-card
								v-else-if="message.messageType === 'result-set'"
								class="ai-card"
								elevation="1"
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
								elevation="1"
							>
								<ChatMarkdownReport :content="message.content" />
							</v-card>

							<!-- Timeline -->
							<v-card
								v-else-if="message.messageType === 'timeline'"
								class="ai-card timeline-card"
								elevation="1"
							>
								<ChatWorkflowTimeline
									:node-blocks="safeParseBlocks(message.content)"
									:completed="true"
								/>
							</v-card>

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
							<v-card v-else class="ai-card" elevation="1">
								<div class="md-body" v-html="renderMarkdown(message.content)" />
							</v-card>
						</div>
					</div>

					<!-- ── Report card below completed timeline ────────── -->
					<div
						v-if="
							message.messageType === 'timeline' &&
							extractReportContent(message.content)
						"
						class="message-wrapper da-msg-enter"
					>
						<div class="row ai-row">
							<v-avatar
								color="blue-darken-3"
								size="32"
								rounded="lg"
								class="avatar"
								style="visibility: hidden"
							/>
							<v-card class="ai-card report-card" elevation="1">
								<ChatMarkdownReport
									:content="extractReportContent(message.content)!"
								/>
							</v-card>
						</div>
					</div>
				</template>

				<!-- ── Streaming: Workflow Timeline ──────────────────── -->
				<div
					v-if="store.isStreaming && store.nodeBlocks.length > 0"
					class="row ai-row"
				>
					<v-avatar color="blue-darken-3" size="32" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card timeline-card" elevation="1">
						<ChatWorkflowTimeline :node-blocks="store.nodeBlocks" />
					</v-card>
				</div>

				<!-- ── Streaming: Report card below timeline ─────────── -->
				<div
					v-if="store.isReportStreaming && store.streamingReportContent"
					class="row ai-row"
				>
					<v-avatar
						color="blue-darken-3"
						size="32"
						rounded="lg"
						class="avatar"
						style="visibility: hidden"
					/>
					<v-card class="ai-card report-card" elevation="1">
						<ChatStreamingReport :content="store.streamingReportContent" />
					</v-card>
				</div>

				<!-- ── Streaming spinner (before first node arrives) ── -->
				<div
					v-else-if="store.isStreaming && store.nodeBlocks.length === 0"
					class="row ai-row"
				>
					<v-avatar color="blue-darken-3" size="32" rounded="lg" class="avatar">
						<v-icon size="18" color="white">mdi-robot</v-icon>
					</v-avatar>
					<v-card class="ai-card thinking-card" elevation="1">
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
import { ref, computed, watch, nextTick } from 'vue';
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
		for (const block of blocks) {
			if (
				block[0]?.nodeName === 'ReportGeneratorNode' &&
				block[0]?.textType === 'MARK_DOWN' &&
				block[0]?.text
			) {
				return block[0].text;
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
		radial-gradient(1200px 400px at 50% -80px, rgba(59, 130, 246, 0.06), transparent 60%),
		#fff;
}

.messages-inner {
	padding: 16px 24px 24px;
	display: flex;
	flex-direction: column;
	gap: 16px;
	width: 100%;
	max-width: 960px;
	margin: 0 auto;
}

.message-wrapper {
	/* enter animation via .da-msg-enter */
}

/* ── Row (shared by user + AI) ───────────────────────────────────────────────── */
.row {
	display: flex;
	align-items: flex-start;
	gap: 8px;
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
	background: linear-gradient(135deg, #2563eb 0%, #3b82f6 100%) !important;
	color: white !important;
	padding: 10px 14px;
	border-radius: 18px 18px 6px 18px !important;
	font-size: 13.5px;
	line-height: 1.55;
	max-width: min(62%, 560px);
	word-break: break-word;
	box-shadow: 0 6px 16px rgba(37, 99, 235, 0.18) !important;
	letter-spacing: -0.01em;
}

/* ── AI card ─────────────────────────────────────────────────────────────────── */
.ai-card {
	padding: 12px 14px;
	border-radius: 6px 18px 18px 18px !important;
	font-size: 13.5px;
	line-height: 1.6;
	max-width: min(78%, 720px);
	word-break: break-word;
	color: var(--da-ink, #0f172a);
	background: #fff !important;
	border: 1px solid var(--da-line-soft, #e8edf2) !important;
	box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04) !important;
	letter-spacing: -0.01em;
	transition: box-shadow 0.2s var(--da-ease-out, ease), border-color 0.2s ease;
}

.ai-card:hover {
	border-color: #dbe4ee !important;
	box-shadow: 0 4px 14px rgba(15, 23, 42, 0.05) !important;
}

/* Report card: answer primary — stronger frame */
.report-card {
	max-width: 100% !important;
	padding: 0 !important;
	flex: 1;
	min-width: 0;
	border: 1px solid #bfdbfe !important;
	box-shadow:
		0 1px 2px rgba(30, 64, 175, 0.05),
		0 10px 28px rgba(30, 64, 175, 0.08) !important;
	overflow: hidden;
	border-radius: 14px !important;
}

/* Timeline card: process secondary (WeKnora hierarchy) */
.timeline-card {
	padding: 10px 12px;
	max-width: 100% !important;
	flex: 1;
	min-width: 0;
	background: var(--da-surface-soft, #f8fafc) !important;
	border: 1px dashed #dbe3ec !important;
	box-shadow: none !important;
	border-radius: 12px !important;
}

/* ── Thinking feedback (WeKnora-like status) ─────────────────────────────────── */
.thinking-card {
	padding: 10px 14px !important;
}
.thinking-row {
	display: flex;
	align-items: center;
	gap: 10px;
}
.thinking-label {
	font-size: 12.5px;
	font-weight: 500;
	color: var(--da-muted, #64748b);
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
	background: #94a3b8;
	border-radius: 50%;
	animation: dotBounce 1.2s infinite;
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
	background: #f6f8fa;
	border: 1px solid #e1e4e8;
	padding: 2px 6px;
	border-radius: 3px;
	font-size: 12.5px;
	font-family: 'Monaco', 'Menlo', 'Fira Code', monospace;
	color: #e83e8c;
}
.md-body :deep(blockquote) {
	border-left: 3px solid #3b82f6;
	padding-left: 12px;
	color: #64748b;
	margin: 6px 0;
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
	border-top: 1px solid #c6cbd1;
}
.md-body :deep(th) {
	display: table-cell;
	background: #f1f5f9;
	padding: 6px 10px;
	border: 1px solid #e2e8f0;
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.md-body :deep(td) {
	display: table-cell;
	padding: 6px 10px;
	border: 1px solid #e2e8f0;
	font-size: 13px;
}
.md-body :deep(tr:nth-child(even)) {
	background: #f8fafc;
}
.md-body :deep(a) {
	color: #2563eb;
	text-decoration: underline;
}
.md-body :deep(hr) {
	border: none;
	border-top: 1px solid #e2e8f0;
	margin: 12px 0;
}
.md-body :deep(strong) {
	font-weight: 700;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) {
	margin: 8px 0;
	border: 1px solid #e1e4e8;
	border-radius: 6px;
	overflow: auto;
	background: #f6f8fa;
}
.md-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: #f6f8fa;
	padding: 3px 6px;
	border-bottom: 1px solid #e1e4e8;
	font-size: 11px;
}
.md-body :deep(.code-language) {
	color: #6a737d;
	font-weight: 600;
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 9.5px;
	text-transform: uppercase;
}
.md-body :deep(.code-copy-button) {
	background: transparent;
	border: 1px solid #d1d5da;
	padding: 2px 8px;
	border-radius: 4px;
	font-size: 10px;
	cursor: pointer;
	transition: all 0.2s;
	color: #24292e;
}
.md-body :deep(.code-copy-button:hover) {
	background: #f3f4f6;
	border-color: #c6cbd1;
}
.md-body :deep(.code-copy-button.copied) {
	background: #28a745;
	border-color: #28a745;
	color: white;
}
.md-body :deep(pre.hljs) {
	margin: 0;
	padding: 8px;
	overflow-x: auto;
	overflow-y: hidden;
	background: #f6f8fa;
	font-size: 12px;
	line-height: 1.4;
	white-space: pre;
}
.md-body :deep(pre.hljs code) {
	display: block;
	padding: 0;
	margin: 0;
	background: transparent;
	border: none;
	font-family: 'Monaco', 'Menlo', monospace;
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
	background: #cbd5e1;
	border-radius: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: #94a3b8;
}

/* ── Status banners (warning / error) ────────────────────────────────────────── */
.status-banner {
	display: flex;
	align-items: center;
	padding: 10px 14px;
	border-radius: 8px;
	font-size: 13px;
	font-weight: 500;
	line-height: 1.5;
	max-width: 75%;
}
.status-banner--warning {
	background: #fffbeb;
	border: 1px solid #fcd34d;
	color: #92400e;
}
.status-banner--error {
	background: #fef2f2;
	border: 1px solid #fca5a5;
	color: #991b1b;
}

.chat-status-strip {
	display: flex;
	align-items: center;
	gap: 6px;
	flex-wrap: wrap;
	padding: 6px 28px 0;
	max-width: 960px;
	width: 100%;
	margin: 0 auto;
	box-sizing: border-box;
	font-size: 11px;
	color: var(--da-muted, #64748b);
}
.chat-status-strip__agent {
	font-weight: 600;
	color: var(--da-ink, #0f172a);
}
.chat-status-strip__model {
	padding: 2px 8px;
	border-radius: 999px;
	background: var(--da-primary-soft, #eff6ff);
	color: var(--da-primary, #1e40af);
	font-weight: 500;
}
.chat-status-strip__live {
	padding: 2px 8px;
	border-radius: 999px;
	background: #ecfdf5;
	color: #047857;
	font-weight: 600;
}
</style>
