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
		<!-- Messages (empty canvas owned by chat.vue) -->
		<template v-if="!showWelcome">
			<div class="messages-inner">
				<template v-for="message in filteredMessages" :key="message.id">
					<div class="message-wrapper da-msg-enter">
						<!-- ── User message (DEEIX: no avatar chrome) ──────── -->
						<div v-if="message.role === 'user'" class="row user-row">
							<v-card class="user-card" elevation="0">
								<span
									v-html="escapeHtml(message.content).replace(/\n/g, '<br>')"
								/>
							</v-card>
						</div>

						<!-- ── AI messages (DEEIX open canvas · no avatar) ── -->
						<div v-else class="row ai-row">

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

							<!-- DEEIX: tiny process chip, then open answer -->
							<template v-else-if="message.messageType === 'timeline'">
								<div class="process-slot process-slot--above">
									<ChatWorkflowTimeline
										:node-blocks="safeParseBlocks(message.content)"
										:completed="true"
									/>
								</div>
								<div
									v-if="extractReportContent(message.content)"
									class="ai-answer report-card"
								>
									<ChatMarkdownReport
										:content="extractReportContent(message.content)!"
									/>
								</div>
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

				<!-- Streaming: process chip · answer open canvas -->
				<div
					v-if="store.isStreaming && store.nodeBlocks.length > 0"
					class="row ai-row process-row"
				>
					<div class="process-slot process-slot--above">
						<ChatWorkflowTimeline :node-blocks="store.nodeBlocks" />
					</div>
				</div>
				<div
					v-else-if="store.isStreaming && store.nodeBlocks.length === 0"
					class="row ai-row"
				>
					<div class="thinking-chip" role="status" aria-live="polite">
						<span class="thinking-chip__dot" aria-hidden="true" />
						<span>思考中…</span>
					</div>
				</div>

				<div
					v-if="store.isReportStreaming && store.streamingReportContent"
					class="row ai-row"
				>
					<div class="ai-answer report-card">
						<ChatStreamingReport :content="store.streamingReportContent" />
					</div>
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
	background: var(--da-surface-soft);
}

.messages-inner {
	padding: 16px 24px 56px;
	display: flex;
	flex-direction: column;
	gap: 18px;
	width: 100%;
	max-width: min(100%, var(--da-chat-max, 1080px));
	margin: 0 auto;
}

.row.ai-row {
	width: 100%;
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

/* ── User card (DEEIX: muted soft bubble, no avatar) ────────────────────────── */
.user-card {
	background: color-mix(in srgb, var(--da-surface-soft) 55%, var(--da-primary-soft)) !important;
	color: var(--da-ink) !important;
	padding: 10px 14px;
	border-radius: 18px 18px 6px 18px !important;
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: var(--da-chat-font-size, 15px);
	font-weight: 400;
	line-height: var(--da-chat-line-height, 1.75);
	max-width: min(72%, 640px);
	word-break: break-word;
	border: 0.5px solid color-mix(in srgb, var(--da-line) 45%, transparent) !important;
	box-shadow: none !important;
	letter-spacing: -0.01em;
}

/* ── AI card (DEEIX answer-first: open canvas, minimal chrome) ─────────────── */
.ai-card {
	padding: 4px 2px 8px;
	border-radius: 0 !important;
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: var(--da-chat-font-size, 15px);
	font-weight: 400;
	line-height: var(--da-chat-line-height, 1.75);
	max-width: min(100%, var(--da-answer-max, 960px));
	word-break: break-word;
	color: var(--da-ink, #1a2332);
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
	letter-spacing: -0.01em;
}

.ai-card:hover {
	border-color: transparent !important;
	box-shadow: none !important;
}

/* Report: open canvas answer (DEEIX) — no heavy paper card */
.report-card {
	max-width: 100% !important;
	padding: 0 !important;
	flex: 1;
	min-width: 0;
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
	overflow: visible;
	border-radius: 0 !important;
}

/* Timeline card: process secondary (DEEIX hierarchy) */
.timeline-card {
	padding: 4px 2px 6px !important;
	max-width: 100% !important;
	flex: 1;
	min-width: 0;
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
	border-radius: 0 !important;
	opacity: 0.92;
}
.timeline-card:hover {
	opacity: 1;
	border-color: transparent !important;
}

/* ── Thinking feedback (DEEIX process marker — muted, not a card) ─────────────── */
.thinking-card {
	padding: 2px 0 4px !important;
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
}
.thinking-row {
	display: flex;
	align-items: center;
	gap: 8px;
}
.thinking-label {
	font-size: 13px;
	font-weight: 500;
	color: var(--da-muted);
	letter-spacing: -0.01em;
}
.thinking-dots {
	display: flex;
	align-items: center;
	gap: 4px;
	padding: 2px 0;
}
.dot {
	width: 5px;
	height: 5px;
	background: var(--da-muted);
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

/* ── Markdown body inside AI card (DEEIX chat-font) ──────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
	font-family: var(--da-font-display);
	font-weight: 500;
	margin: 0.9em 0 0.35em;
	line-height: 1.3;
	letter-spacing: -0.02em;
	color: var(--da-ink);
}
.md-body :deep(p) {
	margin: 0 0 0.7em;
}
.md-body :deep(ul),
.md-body :deep(ol) {
	padding-left: 1.3em;
	margin: 0 0 0.7em;
}
.md-body :deep(li) {
	margin-bottom: 0.15em;
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
	border-collapse: separate;
	border-spacing: 0;
	margin: 10px 0;
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md);
	overflow: hidden;
	background: var(--da-surface);
	box-shadow: var(--da-shadow-sm);
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
	background: color-mix(in srgb, var(--da-surface-soft) 85%, var(--da-primary-soft));
	color: var(--da-muted);
	font-weight: 600;
	font-size: 12px;
	padding: 8px 10px;
	border-bottom: 1px solid var(--da-line-soft);
	text-align: left;
}
.md-body :deep(td) {
	padding: 7px 10px;
	border-bottom: 1px solid var(--da-line-soft);
	font-size: 12.5px;
	color: var(--da-ink);
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
	gap: 8px;
	flex-wrap: wrap;
	padding: 6px 4px 2px;
	max-width: min(100%, var(--da-answer-max, 960px));
	width: calc(100% - 40px);
	margin: 8px auto 0;
	box-sizing: border-box;
	font-size: 11.5px;
	color: var(--da-muted);
	background: transparent;
	border: none;
	border-radius: 0;
	box-shadow: none;
}
.chat-status-strip__agent {
	font-weight: 600;
	color: var(--da-ink);
	letter-spacing: -0.01em;
}
.chat-status-strip__model {
	padding: 0;
	border-radius: 0;
	background: transparent;
	color: var(--da-muted);
	font-weight: 400;
}
.chat-status-strip__model::before {
	content: '·';
	margin-right: 8px;
	color: color-mix(in srgb, var(--da-muted) 55%, transparent);
}
.chat-status-strip__live {
	padding: 1px 0;
	border-radius: 0;
	background: transparent;
	color: var(--da-primary);
	font-weight: 500;
}

.chat-status-strip__ready {
	appearance: none;
	cursor: pointer;
	font: inherit;
	padding: 1px 0;
	border-radius: 0;
	font-size: 11.5px;
	font-weight: 500;
	background: transparent;
	color: var(--da-warning);
	border: none;
	text-decoration: underline;
	text-underline-offset: 2px;
}
.chat-status-strip__ready.ok {
	background: transparent;
	color: var(--da-muted);
	border: none;
	text-decoration: none;
	cursor: default;
}

.chat-status-strip__switch {
	appearance: none;
	margin-left: auto;
	border: none;
	background: transparent;
	color: var(--da-primary);
	border-radius: 0;
	padding: 1px 0;
	font: inherit;
	font-size: 11.5px;
	font-weight: 500;
	cursor: pointer;
	text-decoration: underline;
	text-underline-offset: 2px;
	transition: color var(--da-dur-fast) var(--da-ease-out);
}
.chat-status-strip__switch:hover:not(:disabled) {
	background: transparent;
	color: color-mix(in srgb, var(--da-primary) 80%, #000);
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

/* R231 DEEIX: answer open canvas, process demoted */
.chat-status-strip {

	opacity: 0.92;
	border-bottom: 0.5px solid color-mix(in srgb, var(--da-line) 35%, transparent) !important;
	background: transparent !important;
	backdrop-filter: none !important;
}
.report-card {
	border: none !important;
	box-shadow: none !important;
	background: transparent !important;
	padding-left: 0 !important;
	padding-right: 0 !important;
}
.timeline-card {
	border: 0.5px dashed color-mix(in srgb, var(--da-line) 55%, transparent) !important;
	background: color-mix(in srgb, var(--da-surface-soft) 70%, transparent) !important;
	box-shadow: none !important;
	opacity: 0.92;
}
.ai-card {
	border-color: transparent !important;
	box-shadow: none !important;
}
.user-card {
	border: none !important;
	box-shadow: none !important;
}

/* R231 strip: hairline only */
.chat-status-strip {

	border-bottom: 0.5px solid color-mix(in srgb, var(--da-line) 30%, transparent) !important;
	background: transparent !important;
	backdrop-filter: none !important;
	box-shadow: none !important;
	min-height: 36px !important;
	padding: 4px 16px !important;
}

/* Process as tiny bubble — not a card */
.process-slot {
	width: 100%;
	max-width: min(100%, var(--da-answer-max, 960px));
	margin-top: 2px;
}
.process-row {
	margin-top: -8px;
}
.ai-answer {
	width: 100%;
	max-width: min(100%, var(--da-answer-max, 960px));
	color: var(--da-ink);
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: var(--da-chat-font-size, 15px);
	line-height: var(--da-chat-line-height, 1.75);
	letter-spacing: -0.01em;
}
.thinking-chip {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	min-height: 28px;
	padding: 4px 12px 4px 10px;
	border-radius: 999px;
	border: 0.5px solid color-mix(in srgb, var(--da-primary) 25%, transparent);
	background: color-mix(in srgb, var(--da-primary-soft) 70%, var(--da-surface));
	color: var(--da-muted);
	font-size: 12.5px;
	font-weight: 600;
}
.thinking-chip__dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--da-primary);
	animation: processPulse 1.2s ease-in-out infinite;
}
@keyframes processPulse {
	0%, 100% { opacity: 0.4; }
	50% { opacity: 1; }
}
@media (prefers-reduced-motion: reduce) {
	.thinking-chip__dot { animation: none; }
}
/* Demote legacy timeline-card if any remain */
.timeline-card {
	padding: 0 !important;
	background: transparent !important;
	border: none !important;
	box-shadow: none !important;
}


@media (max-width: 768px) {
	.messages-inner {
		padding: 12px 14px calc(24px + var(--da-safe-bottom, 0px));
		gap: 14px;
	}
	.user-card {
		max-width: min(88%, 100%) !important;
		padding: 10px 12px !important;
		font-size: 15px !important;
	}
	.ai-card,
	.ai-answer {
		max-width: 100% !important;
		font-size: 15px;
	}
	.process-slot {
		max-width: 100%;
	}
}
</style>
