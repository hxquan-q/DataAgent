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
	<div class="markdown-report">
		<!-- MD only — no chrome -->
		<div ref="reportBodyRef" class="report-body report-body--bare">
			<div class="markdown-body" v-html="renderedContent" />
		</div>

		<!-- Fullscreen dialog -->
		<v-dialog v-model="store.showReportFullscreen" fullscreen>
			<v-card>
				<v-toolbar density="compact" color="surface" border="b" class="report-fullscreen-toolbar">
					<v-toolbar-title class="text-body-2 font-weight-medium">
						{{
							store.reportFormat === 'markdown' ? 'Markdown 报告' : 'HTML 报告'
						}}
					</v-toolbar-title>
					<v-spacer />
					<v-btn-toggle
						v-model="store.reportFormat"
						mandatory
						density="compact"
						class="mr-2"
					>
						<v-btn
							value="markdown"
							size="x-small"
							variant="text"
							class="fmt-btn"
							>Markdown</v-btn
						>
						<v-btn value="html" size="x-small" variant="text" class="fmt-btn"
							>HTML</v-btn
						>
					</v-btn-toggle>
					<v-btn
						icon
						variant="text"
						@click="store.showReportFullscreen = false"
					>
						<v-icon>mdi-close</v-icon>
					</v-btn>
				</v-toolbar>
				<v-card-text
					class="report-fullscreen-body"
				>
					<div
						v-if="store.reportFormat === 'markdown'"
						class="markdown-body"
						v-html="renderMarkdown(store.fullscreenReportContent)"
					/>
					<iframe
						v-else
						ref="fullscreenIframeRef"
						style="width: 100%; height: 100%; min-height: 600px; border: none"
						sandbox="allow-scripts"
						title="HTML报告预览"
					/>
				</v-card-text>
			</v-card>
		</v-dialog>
	</div>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue';
import DOMPurify from 'dompurify';
import { renderMarkdownContent } from '~/utils/markdown';
import { transformTableTagsInHtml } from '~/utils/tableTag';
import { buildReportHtml } from '~/utils/report-html-template';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import { useChatStore } from '~/stores/chat';

const props = defineProps<{ content: string }>();
const store = useChatStore();
const format = ref<'markdown' | 'html'>('markdown');
const reportBodyRef = ref<HTMLElement | null>(null);
const htmlIframeRef = ref<HTMLIFrameElement | null>(null);
const fullscreenIframeRef = ref<HTMLIFrameElement | null>(null);
const { renderECharts } = useEchartsRenderer();

function renderMarkdown(md: string): string {
	if (!md) return '';
	// 1) markdown → HTML（html:false，标记 [table::xxx] 作为纯文本保留在文本节点中）
	// 2) 将文本节点中的 [table::tableName] 转为 <span class="table-tag">（表名已转义）
	// 3) DOMPurify 消毒（默认允许 span + class，table-tag 可通过）
	return DOMPurify.sanitize(
		transformTableTagsInHtml(renderMarkdownContent(md)),
		{
			ADD_TAGS: ['div'],
			ADD_ATTR: ['style', 'class', 'data-echarts-config'],
		},
	);
}

function loadHtmlToIframe(
	iframe: HTMLIFrameElement | null,
	markdownContent: string,
) {
	if (!iframe) return;
	if (!markdownContent) {
		iframe.srcdoc =
			'<html><body style="padding:16px;color:var(--da-muted);">暂无报告内容</body></html>';
		return;
	}
	const html = buildReportHtml(markdownContent);
	const blob = new Blob([html], { type: 'text/html;charset=utf-8' });
	const url = URL.createObjectURL(blob);
	const onLoad = () => {
		URL.revokeObjectURL(url);
		iframe.removeEventListener('load', onLoad);
	};
	iframe.addEventListener('load', onLoad);
	iframe.src = url;
}

const renderedContent = computed(() => renderMarkdown(props.content));

watch(
	renderedContent,
	() => {
		nextTick(() => renderECharts(reportBodyRef.value));
	},
	{ immediate: true },
);

watch(format, (val) => {
	if (val === 'html') {
		nextTick(() => loadHtmlToIframe(htmlIframeRef.value, props.content));
	}
});

watch(
	() => store.reportFormat,
	(val) => {
		if (val === 'html') {
			nextTick(() =>
				loadHtmlToIframe(
					fullscreenIframeRef.value,
					store.fullscreenReportContent,
				),
			);
		}
	},
);

function downloadMd() {
	if (!props.content) return;
	const blob = new Blob([props.content], { type: 'text/markdown' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = `report_${Date.now()}.md`;
	document.body.appendChild(a);
	a.click();
	document.body.removeChild(a);
	URL.revokeObjectURL(url);
}

async function downloadHtml() {
	if (!props.content) return;
	try {
		await store.downloadHtmlReport(props.content);
	} catch (e) {
		console.error('下载HTML报告失败', e);
	}
}
</script>

<style scoped>
.markdown-report {
	background: transparent;
}

/* ── Header (quiet chrome — answer content is primary) ───────────────────────── */
.report-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 0 0 6px;
	background: transparent;
	border-bottom: none;
	flex-wrap: wrap;
	gap: 6px;
	opacity: 0.55;
	transition: opacity var(--da-dur-fast) var(--da-ease-out);
}
.report-header:hover,
.report-header:focus-within {
	opacity: 1;
}
.report-header--quiet .report-title {
	font-size: 11.5px;
}
.report-title {
	display: flex;
	align-items: center;
	font-size: 12.5px;
	font-weight: 500;
	color: var(--da-muted);
	gap: 4px;
	flex-wrap: wrap;
	min-width: 0;
}
.report-title > span {
	letter-spacing: -0.01em;
	color: var(--da-ink);
	font-weight: 600;
}
.report-actions {
	display: flex;
	align-items: center;
	gap: 6px;
	flex-wrap: wrap;
}
.report-action-btn {
	border-radius: 999px !important;
	letter-spacing: 0 !important;
	min-height: 28px !important;
}

.report-actions :deep(.v-btn) {
	text-transform: none !important;
	letter-spacing: 0 !important;
}

/* ── Format toggle ───────────────────────────────────────────────────────────── */
.format-toggle {
	border: 1px solid var(--da-line-soft);
	border-radius: 999px;
	overflow: hidden;
	background: var(--da-surface-soft);
	padding: 2px;
}

.fmt-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-size: 11.5px !important;
	font-weight: 600 !important;
	border-radius: 999px !important;
	min-height: 28px !important;
}


/* ── Body ────────────────────────────────────────────────────────────────────── */
.report-body {
	padding: 4px 0 8px;
}
.html-iframe {
	display: block;
	width: 100%;
	min-height: 600px;
	border: none;
}

/* ── Markdown body (DEEIX chat-font prose) ───────────────────────────────────── */
.markdown-body {
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: var(--da-chat-font-size, 15px);
	line-height: var(--da-chat-line-height, 1.75);
	color: var(--da-ink);
	letter-spacing: -0.01em;
}
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
	font-family: var(--da-font-display);
	font-weight: 500;
	margin: 1.1em 0 0.4em;
	color: var(--da-ink);
	letter-spacing: -0.02em;
	line-height: 1.3;
}
.markdown-body :deep(h1) {
	font-size: 1.35em;
}
.markdown-body :deep(h2) {
	font-size: 1.2em;
}
.markdown-body :deep(h3) {
	font-size: 1.08em;
}
.markdown-body :deep(p) {
	margin: 0 0 0.75em;
	line-height: var(--da-chat-line-height, 1.75);
	color: var(--da-ink);
	font-size: inherit;
}
.markdown-body :deep(ul),
.markdown-body :deep(ol) {
	padding-left: 1.35em;
	margin: 0 0 0.75em;
}
.markdown-body :deep(li) {
	line-height: var(--da-chat-line-height, 1.75);
	font-size: inherit;
	color: var(--da-ink);
	margin-bottom: 0.2em;
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
	border-collapse: separate;
	border-spacing: 0;
	margin: 10px 0;
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md);
	overflow: hidden;
	background: var(--da-surface);
	box-shadow: var(--da-shadow-sm);
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
	padding: 5px 8px;
	border: 1px solid var(--da-line-soft);
	font-weight: 600;
	font-size: 13px;
	text-align: left;
}
.markdown-body :deep(td) {
	display: table-cell;
	padding: 6px 10px;
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
	font-family: var(--da-font-mono);
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
	padding: 12px 14px;
	overflow-x: auto;
	overflow-y: hidden;
	background: var(--da-surface-soft);
	font-size: 13px;
	line-height: 1.55;
	white-space: pre;
}
.markdown-body :deep(pre.hljs code) {
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

/* ── ECharts containers ─────────────────────────────────────────────────────── */
:deep(.md-echarts) {
	margin: 10px 0;
	border-radius: var(--da-radius-sm);
}

/* ── ECharts skeleton placeholder ──────────────────────────────────────────── */
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

/* Arceage-inspired header accent */
.markdown-report {
	background: var(--da-surface);
}
.report-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 4px 0 8px;
	background: transparent;
	border-bottom: none;
	flex-wrap: wrap;
	gap: 6px;
	opacity: 0.9;
}
.report-hairline {
	height: 1px;
	margin: 0;
	background: linear-gradient(
		90deg,
		transparent 0%,
		color-mix(in srgb, var(--da-primary) 35%, var(--da-line-soft)) 20%,
		color-mix(in srgb, var(--da-primary) 35%, var(--da-line-soft)) 80%,
		transparent 100%
	);
}
.report-title span {
	letter-spacing: -0.01em;
	font-weight: 600;
}
.report-body {
	/* keep existing; soft top pad via hairline separation */
}

@media (prefers-reduced-motion: reduce) {
	* {
		animation: none !important;
	}
}

/* R8 tokens */
.markdown-body :deep(h1),
.markdown-body :deep(h2),
.markdown-body :deep(h3) {
	color: var(--da-ink);
}
.markdown-body :deep(p),
.markdown-body :deep(li) {
	color: var(--da-ink);
	letter-spacing: -0.01em;
}

.report-actions :deep(.v-btn) {
	min-width: 28px !important;
	height: 28px !important;
}
.report-header {
	display: flex;
	justify-content: space-between;
	align-items: center;
	padding: 4px 0 8px;
	background: transparent;
	border-bottom: none;
	flex-wrap: wrap;
	gap: 6px;
	opacity: 0.9;
}

.report-fullscreen-toolbar {
	background: color-mix(in srgb, var(--da-primary-soft) 45%, var(--da-surface)) !important;
	border-bottom: 1px solid var(--da-line-soft) !important;
}
.report-fullscreen-body {
	height: calc(100vh - 64px);
	overflow-y: auto;
	padding: 20px 22px;
	background: var(--da-surface-soft);
}

/* R231: demote report chrome */
.report-hairline { display: none !important; }
.report-title > span { font-weight: 500 !important; color: var(--da-muted) !important; }
.report-action-btn {
	border: none !important;
	box-shadow: none !important;
	background: transparent !important;
}
</style>
