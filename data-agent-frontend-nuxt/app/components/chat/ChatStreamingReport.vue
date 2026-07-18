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
	<div class="streaming-report">
		<div class="report-header">
			<v-icon color="primary" size="18" class="mr-2"
				>mdi-file-document-edit-outline</v-icon
			>
			<span>报告生成中（结论优先）...</span>
			<span class="typing-indicator">
				<span class="typing-dot" />
				<span class="typing-dot typing-dot--2" />
				<span class="typing-dot typing-dot--3" />
			</span>
		</div>
		<div ref="bodyRef" class="report-body">
			<div class="markdown-body streaming" v-html="renderedHtml" />
		</div>
	</div>
</template>

<script setup lang="ts">
import { ref, watch, computed, nextTick, onBeforeUnmount } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { transformTableTagsInHtml } from '~/utils/tableTag';
import { useTypewriter } from '~/composables/useTypewriter';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useChatStore } from '~/stores/chat';

const props = defineProps<{ content: string }>();
const bodyRef = ref<HTMLElement | null>(null);

const { displayedText, append, reset, flush } = useTypewriter();
const store = useChatStore();
const { renderECharts } = useEchartsRenderer();

// Track what we've already fed to the typewriter
let lastFedLength = 0;

// 流结束时同步清空队列，避免组件卸载前继续等待打字机动画。
watch(
	() => store.isReportStreaming,
	(isStreaming) => {
		if (!isStreaming) flush();
	},
	{ flush: 'sync' },
);

watch(
	() => props.content,
	(newVal, _oldVal) => {
		// If content was reset (new stream started), reset the typewriter
		if (!newVal || newVal.length < lastFedLength) {
			reset();
			lastFedLength = 0;
		}
		// Feed only the new delta to the typewriter queue
		if (newVal && newVal.length > lastFedLength) {
			const chunk = newVal.slice(lastFedLength);
			lastFedLength = newVal.length;
			append(chunk);
		}
	},
	{ immediate: true },
);

// Render markdown from the typewriter's displayed text (incremental)
// We use a throttled computed: only re-render when displayedText changes.
// This is much cheaper than re-rendering the full content on every SSE event.
const SANITIZE_OPTIONS = {
	ADD_TAGS: ['div'],
	ADD_ATTR: ['style', 'class', 'data-echarts-config'],
};

const renderedHtml = computed(() => {
	const text = displayedText.value;
	if (!text) return '';
	return DOMPurify.sanitize(
		transformTableTagsInHtml(renderMarkdownContent(text)),
		SANITIZE_OPTIONS,
	) as string;
});

// After each render, try to initialize any completed echarts blocks
watch(renderedHtml, () => {
	nextTick(() => renderECharts(bodyRef.value));
});

onBeforeUnmount(() => {
	lastFedLength = 0;
});
</script>

<style scoped>
 .streaming-report {
	background: var(--da-surface);
}

.report-header {
	display: flex;
	align-items: center;
	padding: 8px 12px;
	background: color-mix(in srgb, var(--da-primary-soft) 55%, var(--da-surface-soft));
	border-bottom: 1px solid var(--da-line-soft);
	font-size: 12.5px;
	font-weight: 600;
	color: var(--da-ink);
	gap: 6px;
}

.typing-indicator {
	display: inline-flex;
	align-items: center;
	gap: 2px;
	margin-left: 4px;
}

.typing-dot {
	width: 3px;
	height: 3px;
	background: var(--da-accent);
	border-radius: 50%;
	animation: typingBounce 1.2s infinite;
}
.typing-dot--2 {
	animation-delay: 0.2s;
}
.typing-dot--3 {
	animation-delay: 0.4s;
}
@keyframes typingBounce {
	0%,
	60%,
	100% {
		opacity: 0.3;
		transform: translateY(0);
	}
	30% {
		opacity: 1;
		transform: translateY(-2px);
	}
}

.report-body {
	padding: 8px;
	position: relative;
}

/* Blinking dot cursor — appended after last inline content via CSS ::after
 * Markdown renders block elements (p, h, li), so we target the last child.
 * The dot stays inline at the end of the last line of text.
 */
.markdown-body.streaming :deep(> :last-child::after) {
	content: '';
	display: inline-block;
	width: 5px;
	height: 5px;
	border-radius: 50%;
	background: var(--da-accent);
	margin-left: 3px;
	vertical-align: middle;
	animation: cursorDotBlink 0.7s step-end infinite;
}
@keyframes cursorDotBlink {
	0%,
	100% {
		opacity: 1;
	}
	50% {
		opacity: 0;
	}
}

/* ── Markdown body ───────────────────────────────────────────────────────────── */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
	font-weight: 700;
	margin: 10px 0 4px;
	color: var(--da-ink);
}
.markdown-body :deep(h1) {
	font-size: 18px;
}
.markdown-body :deep(h2) {
	font-size: 16px;
}
.markdown-body :deep(h3) {
	font-size: 14.5px;
}
.markdown-body :deep(p) {
	margin-bottom: 8px;
	line-height: 1.65;
	color: var(--da-ink);
	font-size: 13.5px;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
	padding-left: 22px;
	margin-bottom: 8px;
}
.markdown-body :deep(li) {
	line-height: 1.7;
	font-size: 14px;
	color: var(--da-ink);
}
.markdown-body :deep(code:not(pre code)) {
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	padding: 2px 5px;
	border-radius: var(--da-radius-sm);
	font-size: 12.5px;
	color: color-mix(in srgb, var(--da-primary) 55%, #be185d);
}
.markdown-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 10px 0;
	display: block;
	overflow-x: auto;
}
.markdown-body :deep(thead) {
	display: table-header-group;
}
.markdown-body :deep(tbody) {
	display: table-row-group;
}
.markdown-body :deep(tr) {
	display: table-row;
	border-top: 1px solid var(--da-line);
}
.markdown-body :deep(th) {
	display: table-cell;
	background: var(--da-surface-soft);
	padding: 8px 12px;
	border: 1px solid var(--da-line-soft);
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.markdown-body :deep(td) {
	display: table-cell;
	padding: 8px 12px;
	border: 1px solid var(--da-line-soft);
	font-size: 13px;
}
.markdown-body :deep(tr:nth-child(even) td) {
	background: var(--da-surface-soft);
}
.markdown-body :deep(blockquote) {
	border-left: 3px solid var(--da-accent);
	padding: 8px 14px;
	margin-left: 0;
	background: var(--da-primary-soft);
	border-radius: 0 6px 6px 0;
	color: var(--da-ink);
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.markdown-body :deep(.code-block-wrapper) {
	margin: 10px 0;
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md);
	overflow: auto;
	background: var(--da-surface);
	box-shadow: var(--da-shadow-sm);
}
.markdown-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: color-mix(in srgb, var(--da-primary-soft) 45%, var(--da-surface-soft));
	padding: 7px 12px;
	border-bottom: 1px solid var(--da-line-soft);
	font-size: 11px;
}
.markdown-body :deep(.code-language) {
	color: var(--da-muted);
	font-weight: 600;
	font-family: 'Monaco', 'Menlo', monospace;
	font-size: 10px;
	text-transform: uppercase;
}
.markdown-body :deep(.code-copy-button) {
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	padding: 3px 10px;
	border-radius: 999px;
	font-size: 10px;
	font-weight: 600;
	cursor: pointer;
	transition: background var(--da-dur-fast) var(--da-ease-out),
		border-color var(--da-dur-fast) var(--da-ease-out);
	color: var(--da-ink);
}
.markdown-body :deep(.code-copy-button:hover) {
	background: var(--da-primary-soft);
	border-color: color-mix(in srgb, var(--da-primary) 30%, transparent);
	color: var(--da-primary);
}
.markdown-body :deep(.code-copy-button.copied) {
	background: var(--da-success);
	border-color: var(--da-success);
	color: var(--da-on-primary, #fff);
}
.markdown-body :deep(pre.hljs) {
	margin: 0;
	padding: 8px;
	overflow-x: auto;
	overflow-y: hidden;
	background: var(--da-surface-soft);
	font-size: 11.5px;
	line-height: 1.4;
	white-space: pre;
}
.markdown-body :deep(pre.hljs code) {
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

/* ── ECharts containers ─────────────────────────────────────────────────────── */
:deep(.md-echarts) {
	margin: 10px 0;
	border-radius: var(--da-radius-sm);
}

/* ── ECharts skeleton placeholder (while streaming) ────────────────────────── */
:deep(.md-echarts-skeleton) {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 10px;
	margin: 10px 0;
	height: 120px;
	border-radius: var(--da-radius-md);
	border: 1px dashed var(--da-line);
	background: linear-gradient(90deg, var(--da-surface-soft) 25%, var(--da-surface-soft) 50%, var(--da-surface-soft) 75%);
	background-size: 200% 100%;
	animation: skeletonShimmer 1.6s ease-in-out infinite;
	color: var(--da-muted);
	font-size: 13px;
}
:deep(.md-echarts-skeleton-icon) {
	width: 16px;
	height: 16px;
	border: 2px solid var(--da-line);
	border-top-color: var(--da-accent);
	border-radius: 50%;
	animation: skeletonSpin 0.8s linear infinite;
}
@keyframes skeletonSpin {
	to {
		transform: rotate(360deg);
	}
}
:deep(.md-echarts-skeleton-text) {
	font-weight: 500;
	letter-spacing: 0.3px;
}
@keyframes skeletonShimmer {
	0% {
		background-position: 200% 0;
	}
	100% {
		background-position: -200% 0;
	}
}
@keyframes spinPulse {
	0%,
	100% {
		opacity: 1;
		transform: scale(1);
	}
	50% {
		opacity: 0.5;
		transform: scale(0.9);
	}
}

/* design tokens + reduced motion (R2) */
.report-header {
	background: var(--da-surface-soft);
	border-bottom-color: var(--da-line-soft);
	color: var(--da-ink);
}
.typing-dot {
	background: var(--da-accent);
}
.markdown-body.streaming :deep(> :last-child::after) {
	background: var(--da-accent);
}
@media (prefers-reduced-motion: reduce) {
	.typing-dot,
	.markdown-body.streaming :deep(> :last-child::after) {
		animation: none !important;
		opacity: 0.85;
	}
}
</style>
