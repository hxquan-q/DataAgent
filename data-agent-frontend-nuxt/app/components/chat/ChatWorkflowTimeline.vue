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
	<div
		ref="timelineRef"
		class="workflow-timeline"
		:class="{ 'is-completed': completed }"
	>
		<!-- Title + global toggle (WeKnora-style: process secondary to answer) -->
		<div class="timeline-title-bar">
			<div class="timeline-title-group">
				<v-card-title class="timeline-title pa-0">
					<v-icon
						size="18"
						:color="completed ? 'success' : 'blue'"
						class="mr-1"
					>
						{{
							completed
								? 'mdi-check-decagram-outline'
								: 'mdi-rocket-launch-outline'
						}}
					</v-icon>
					{{ completed ? '过程详情（可展开）' : '任务进行中' }}
				</v-card-title>
				<span v-if="timelineSteps.length" class="timeline-summary">
					{{ doneCount }}/{{ timelineSteps.length }} 步
					<span v-if="activeLabel" class="timeline-active-hint"
						>· {{ activeLabel }}</span
					>
				</span>
			</div>
			<v-btn
				variant="outlined"
				size="x-small"
				color="grey"
				class="toggle-all-btn"
				:prepend-icon="
					allExpanded
						? 'mdi-unfold-less-horizontal'
						: 'mdi-unfold-more-horizontal'
				"
				@click="toggleAll"
			>
				{{ allExpanded ? '折叠过程' : '展开过程' }}
			</v-btn>
		</div>

		<v-timeline density="compact" side="end" truncate-line="both">
			<v-timeline-item
				v-for="step in timelineSteps"
				:key="step.nodeName"
				:dot-color="dotColor(step.status)"
				:icon="dotIcon(step.status)"
				size="small"
				:class="{
					'step-muted': completed && step.status === 'done' && !step.isReport,
				}"
			>
				<!-- Step header: clickable to toggle -->
				<div class="step-header" @click="toggleStep(step.nodeName)">
					<div class="step-header-left">
						<span class="step-label">{{ step.label }}</span>
						<span v-if="step.status === 'active'" class="step-badge active">
							<span class="badge-dot" />进行中
						</span>
						<span v-else-if="step.status === 'done'" class="step-badge done"
							>完成</span
						>
					</div>
					<v-icon size="16" color="grey">
						{{ step.expanded ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
					</v-icon>
				</div>

				<!-- Collapsible content -->
				<v-expand-transition>
					<div
						v-show="step.expanded"
						class="step-content"
						:class="{ 'is-muted': step.status === 'done' && !step.isReport }"
					>
						<!-- Result Set -->
						<ChatResultSet
							v-if="
								step.block[0]?.textType === 'RESULT_SET' && step.block[0]?.text
							"
							:data="safeParseJson(step.block[0].text)"
							:page-size="10"
							:pending-report="!completed && !step.isReport"
						/>
						<!-- Report node: show brief status, not full content -->
						<div v-else-if="step.isReport" class="text-body report-brief">
							<v-icon size="14" color="success" class="mr-1"
								>mdi-file-chart-outline</v-icon
							>
							<span v-if="step.status === 'active'"
								>正在生成报告，内容在下方实时展示...</span
							>
							<span v-else>报告已生成完毕，查看下方报告卡片</span>
						</div>
						<!-- Pure code block (all items share same code type) -->
						<div
							v-else-if="isPureCodeBlock(step.block)"
							v-html="renderCode(step.block)"
						/>
						<!-- Mixed content: text with possible embedded JSON/code -->
						<div
							v-else
							class="text-body"
							v-html="renderTextWithJsonDetection(step.block)"
						/>
					</div>
				</v-expand-transition>
			</v-timeline-item>
		</v-timeline>
	</div>
</template>

<script setup lang="ts">
import hljs from 'highlight.js';
import DOMPurify from 'dompurify';
import { useEchartsRenderer } from '~/composables/useEchartsRenderer';
import type { GraphNodeResponse } from '~/services/graph/index';
import type { ResultData } from '~/services/resultSet/index';
import ChatResultSet from './ChatResultSet.vue';

const props = withDefaults(
	defineProps<{
		nodeBlocks: GraphNodeResponse[][];
		completed?: boolean;
	}>(),
	{
		completed: false,
	},
);

const expandedSteps = ref<Record<string, boolean>>({});

const allExpanded = computed(() => {
	const steps = timelineSteps.value;
	if (steps.length === 0) return false;
	return steps.some((s) => s.expanded);
});

const doneCount = computed(
	() => timelineSteps.value.filter((s) => s.status === 'done').length,
);

const activeLabel = computed(() => {
	const active = timelineSteps.value.find((s) => s.status === 'active');
	return active?.label ?? '';
});

function toggleAll() {
	const shouldExpand = !allExpanded.value;
	for (const step of timelineSteps.value) {
		expandedSteps.value[step.nodeName] = shouldExpand;
	}
}

function toggleStep(nodeName: string) {
	const step = timelineSteps.value.find((s) => s.nodeName === nodeName);
	const defaultExpanded = getDefaultExpanded(nodeName, step?.status);
	expandedSteps.value[nodeName] = !(
		expandedSteps.value[nodeName] ?? defaultExpanded
	);
}

function getDefaultExpanded(_nodeName: string, status?: string): boolean {
	// 完成后全收起；执行中只展开当前 active 步（过程有界）
	if (props.completed) return false;
	return status === 'active';
}

interface NodeDef {
	nodeName: string;
	label: string;
	icon: string;
}

const NODE_LABEL_MAP: Record<string, NodeDef> = {
	IntentRecognitionNode: {
		nodeName: 'IntentRecognitionNode',
		label: '意图识别',
		icon: 'mdi-magnify',
	},
	QueryEnhanceNode: {
		nodeName: 'QueryEnhanceNode',
		label: '查询增强',
		icon: 'mdi-text-search',
	},
	SchemaRecallNode: {
		nodeName: 'SchemaRecallNode',
		label: 'Schema 召回',
		icon: 'mdi-database-search',
	},
	FeasibilityAssessmentNode: {
		nodeName: 'FeasibilityAssessmentNode',
		label: '可行性评估',
		icon: 'mdi-check-circle-outline',
	},
	EvidenceRecallNode: {
		nodeName: 'EvidenceRecallNode',
		label: '证据召回',
		icon: 'mdi-file-search-outline',
	},
	TableRelationNode: {
		nodeName: 'TableRelationNode',
		label: '表关系分析',
		icon: 'mdi-table-network',
	},
	PlannerNode: {
		nodeName: 'PlannerNode',
		label: '制定计划',
		icon: 'mdi-clipboard-list-outline',
	},
	HumanFeedbackNode: {
		nodeName: 'HumanFeedbackNode',
		label: '人工反馈',
		icon: 'mdi-account-check-outline',
	},
	PlanExecutorNode: {
		nodeName: 'PlanExecutorNode',
		label: '执行计划',
		icon: 'mdi-play-circle-outline',
	},
	SqlGenerateNode: {
		nodeName: 'SqlGenerateNode',
		label: 'SQL 生成',
		icon: 'mdi-code-braces',
	},
	SemanticConsistencyNode: {
		nodeName: 'SemanticConsistencyNode',
		label: '语义一致性校验',
		icon: 'mdi-check-decagram',
	},
	SqlExecuteNode: {
		nodeName: 'SqlExecuteNode',
		label: 'SQL 执行',
		icon: 'mdi-database-arrow-right',
	},
	PythonGenerateNode: {
		nodeName: 'PythonGenerateNode',
		label: 'Python 生成',
		icon: 'mdi-language-python',
	},
	PythonAnalyzeNode: {
		nodeName: 'PythonAnalyzeNode',
		label: 'Python 分析',
		icon: 'mdi-chart-line',
	},
	PythonExecuteNode: {
		nodeName: 'PythonExecuteNode',
		label: 'Python 执行',
		icon: 'mdi-play-outline',
	},
	ReportGeneratorNode: {
		nodeName: 'ReportGeneratorNode',
		label: '报告生成',
		icon: 'mdi-file-chart-outline',
	},
};

interface TimelineStep extends NodeDef {
	status: 'pending' | 'active' | 'done';
	block: GraphNodeResponse[];
	expanded: boolean;
	isReport: boolean;
}

const timelineSteps = computed<TimelineStep[]>(() => {
	const seen = new Set<string>();
	const orderedNodeNames: string[] = [];
	for (const block of props.nodeBlocks) {
		const name = block[0]?.nodeName;
		if (name && !seen.has(name)) {
			seen.add(name);
			orderedNodeNames.push(name);
		}
	}

	if (orderedNodeNames.length === 0) return [];
	const lastIdx = orderedNodeNames.length - 1;

	return orderedNodeNames.map((nodeName, idx) => {
		const def = NODE_LABEL_MAP[nodeName] || {
			nodeName,
			label: nodeName,
			icon: 'mdi-lightning-bolt',
		};
		const block =
			props.nodeBlocks.find((b) => b[0]?.nodeName === nodeName) || [];
		const isReport = nodeName === 'ReportGeneratorNode';

		let status: 'pending' | 'active' | 'done' = 'pending';
		if (props.completed) {
			status = 'done';
		} else {
			status = idx < lastIdx ? 'done' : 'active';
		}

		return {
			...def,
			status,
			block,
			expanded: expandedSteps.value[nodeName] ?? getDefaultExpanded(nodeName, status),
			isReport,
		};
	});
});

function dotColor(status: string): string {
	if (status === 'done') return 'green';
	if (status === 'active') return 'primary';
	return 'grey-lighten-1';
}

function dotIcon(status: string): string {
	if (status === 'done') return 'mdi-check';
	if (status === 'active') return 'mdi-dots-horizontal';
	return '';
}

function safeParseJson(content: string): ResultData | null {
	try {
		return JSON.parse(content);
	} catch {
		return null;
	}
}

function escapeHtml(text: string): string {
	const div = document.createElement('div');
	div.textContent = text;
	return div.innerHTML;
}

const timelineRef = ref<HTMLElement | null>(null);
const { renderECharts } = useEchartsRenderer();

const CODE_TEXT_TYPES = new Set(['SQL', 'PYTHON', 'JSON']);

function isPureCodeBlock(block: GraphNodeResponse[]): boolean {
	return (
		block.length > 0 && block.every((n) => CODE_TEXT_TYPES.has(n.textType))
	);
}

const SANITIZE_OPTIONS = {
	ADD_TAGS: ['pre', 'code'],
	ADD_ATTR: ['class'],
	RETURN_TRUSTED_TYPE: false as const,
};

function truncateProcess(text: string, limit = 1600): string {
	if (!text || text.length <= limit) return text;
	return text.slice(0, limit) + '\n…(过程输出已截断)';
}

function renderCode(block: GraphNodeResponse[]): string {
	const lang = (block[0]?.textType || 'text').toLowerCase();
	const code = truncateProcess(block.map((n) => n.text).join(''));
	try {
		const h = hljs.highlight(code, { language: lang });
		return DOMPurify.sanitize(
			`<pre class="tl-code"><code class="hljs ${lang}">${h.value}</code></pre>`,
			SANITIZE_OPTIONS,
		) as string;
	} catch {
		return DOMPurify.sanitize(
			`<pre class="tl-code"><code>${escapeHtml(code)}</code></pre>`,
			SANITIZE_OPTIONS,
		) as string;
	}
}

function tryExtractJson(
	text: string,
): { before: string; json: string; after: string } | null {
	const start = text.indexOf('{');
	const end = text.lastIndexOf('}');
	if (start === -1 || end === -1 || end <= start) return null;
	const candidate = text.substring(start, end + 1);
	try {
		JSON.parse(candidate);
		return {
			before: text.substring(0, start).trim(),
			json: candidate,
			after: text.substring(end + 1).trim(),
		};
	} catch {
		return null;
	}
}

function renderTextWithJsonDetection(block: GraphNodeResponse[]): string {
	const fullText = truncateProcess(block.map((n) => n.text).join(''));

	const extracted = tryExtractJson(fullText);
	if (extracted) {
		const parts: string[] = [];
		if (extracted.before) {
			parts.push(
				`<div class="text-body">${escapeHtml(extracted.before).replace(/\n/g, '<br>')}</div>`,
			);
		}
		try {
			const formatted = JSON.stringify(JSON.parse(extracted.json), null, 2);
			const h = hljs.highlight(formatted, { language: 'json' });
			parts.push(
				`<pre class="tl-code"><code class="hljs json">${h.value}</code></pre>`,
			);
		} catch {
			parts.push(
				`<pre class="tl-code"><code>${escapeHtml(extracted.json)}</code></pre>`,
			);
		}
		if (extracted.after) {
			parts.push(
				`<div class="text-body">${escapeHtml(extracted.after).replace(/\n/g, '<br>')}</div>`,
			);
		}
		return DOMPurify.sanitize(parts.join(''), SANITIZE_OPTIONS) as string;
	}

	return DOMPurify.sanitize(
		`<div class="text-body">${escapeHtml(fullText).replace(/\n/g, '<br>')}</div>`,
		SANITIZE_OPTIONS,
	) as string;
}

watch(
	() => props.completed,
	(done) => {
		if (!done) return;
		const next: Record<string, boolean> = {};
		for (const s of timelineSteps.value) {
			next[s.nodeName] = false;
		}
		expandedSteps.value = next;
	},
);

watch(
	() => props.nodeBlocks,
	() => {
		nextTick(() => renderECharts(timelineRef.value));
	},
	{ deep: true },
);
</script>

<style scoped>
.workflow-timeline {
	width: 100%;
}

.workflow-timeline.is-completed {
	opacity: 0.96;
}

/* ── Title bar ───────────────────────────────────────────────────────────────── */
.timeline-title-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 6px;
	margin-bottom: 6px;
	padding: 3px 5px;
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-sm);
}

.timeline-title-group {
	display: flex;
	align-items: baseline;
	flex-wrap: wrap;
	gap: 6px;
	min-width: 0;
}

.timeline-title {
	font-size: 12.5px !important;
	font-weight: 700;
	color: var(--da-primary);
	display: flex;
	align-items: center;
	line-height: 1;
}

.workflow-timeline.is-completed .timeline-title {
	color: var(--da-success);
}

.timeline-summary {
	font-size: 12px;
	color: var(--da-muted);
	font-weight: 500;
}

.timeline-active-hint {
	color: var(--da-primary);
}

.toggle-all-btn {
	font-size: 11px !important;
	text-transform: none !important;
	letter-spacing: 0 !important;
	flex-shrink: 0;
}

:deep(.step-muted .v-timeline-item__body) {
	opacity: 0.88;
}

/* ── Step header ─────────────────────────────────────────────────────────────── */
.step-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	cursor: pointer;
	padding: 6px 8px;
	user-select: none;
	border-radius: var(--da-radius-sm);
	transition: background var(--da-dur-fast) var(--da-ease-out);
}
.step-header:hover {
	background: color-mix(in srgb, var(--da-primary-soft) 55%, transparent);
}

.step-header-left {
	display: flex;
	align-items: center;
	gap: 6px;
}

.step-label {
	font-size: 12.5px;
	font-weight: 600;
	color: var(--da-ink);
}

/* ── Badge ───────────────────────────────────────────────────────────────────── */
.step-badge {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	font-size: 10.5px;
	padding: 2px 7px;
	border-radius: 999px;
}

.step-badge.active {
	background: var(--da-primary-soft);
	color: var(--da-primary);
}

.step-badge.done {
	background: color-mix(in srgb, var(--da-success) 12%, white);
	color: var(--da-success);
}

.badge-dot {
	width: 5px;
	height: 5px;
	background: var(--da-primary);
	border-radius: 50%;
	animation: dotBlink 1s infinite;
}

@keyframes dotBlink {
	0%,
	100% {
		opacity: 1;
	}
	50% {
		opacity: 0.3;
	}
}

/* ── Step content ────────────────────────────────────────────────────────────── */
.step-content {
	margin-top: 6px;
	font-size: 12.5px;
	line-height: 1.65;
	color: var(--da-ink);
	min-width: 0;
	overflow: hidden;
}

.text-body {
	white-space: pre-wrap;
	word-break: break-word;
}

.is-muted .text-body {
	color: var(--da-muted);
	font-style: italic;
}

.report-body {
	color: var(--da-ink) !important;
	font-style: normal !important;
}

.report-brief {
	display: flex;
	align-items: center;
	color: var(--da-muted) !important;
	font-style: normal !important;
	font-size: 12px;
}

:deep(.tl-code) {
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-sm);
	padding: 6px 8px;
	font-size: 12.5px;
	overflow-x: auto;
	white-space: pre;
	margin: 4px 0 0;
}

/* ── Markdown inside step content ────────────────────────────────────────────── */
.md-body :deep(h1),
.md-body :deep(h2),
.md-body :deep(h3) {
	font-weight: 700;
	margin: 10px 0 4px;
}
.md-body :deep(p) {
	margin-bottom: 6px;
}
.md-body :deep(ul),
.md-body :deep(ol) {
	padding-left: 18px;
	margin-bottom: 6px;
}
.md-body :deep(code:not(pre code)) {
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	padding: 1px 5px;
	border-radius: 3px;
	font-size: 12px;
	color: color-mix(in srgb, var(--da-primary) 55%, #be185d);
}
.md-body :deep(table) {
	width: 100%;
	border-collapse: collapse;
	margin: 6px 0;
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
	padding: 3px 6px;
	border: 1px solid var(--da-line-soft);
	font-weight: 600;
	font-size: 12px;
}
.md-body :deep(td) {
	display: table-cell;
	padding: 3px 6px;
	border: 1px solid var(--da-line-soft);
	font-size: 12px;
}

/* ── Code block with header ─────────────────────────────────────────────────── */
.md-body :deep(.code-block-wrapper) {
	margin: 8px 0;
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md);
	overflow: auto;
	background: var(--da-surface);
	box-shadow: var(--da-shadow-sm);
}
.md-body :deep(.code-block-header) {
	display: flex;
	justify-content: space-between;
	align-items: center;
	background: color-mix(in srgb, var(--da-primary-soft) 45%, var(--da-surface-soft));
	padding: 6px 10px;
	border-bottom: 1px solid var(--da-line-soft);
	font-size: 11px;
}
.md-body :deep(.code-language) {
	color: var(--da-muted);
	font-weight: 600;
	font-family: var(--da-font-mono);
	font-size: 10px;
	text-transform: uppercase;
}
.md-body :deep(.code-copy-button) {
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	padding: 2px 10px;
	border-radius: 999px;
	font-size: 10px;
	font-weight: 600;
	cursor: pointer;
	transition: background var(--da-dur-fast) var(--da-ease-out),
		border-color var(--da-dur-fast) var(--da-ease-out);
	color: var(--da-ink);
}
.md-body :deep(.code-copy-button:hover) {
	background: var(--da-primary-soft);
	border-color: color-mix(in srgb, var(--da-primary) 30%, transparent);
	color: var(--da-primary);
}
.md-body :deep(.code-copy-button.copied) {
	background: var(--da-success);
	border-color: var(--da-success);
	color: var(--da-on-primary, #fff);
}
.md-body :deep(pre.hljs) {
	margin: 0;
	padding: 8px 10px;
	overflow-x: auto;
	overflow-y: hidden;
	background: var(--da-surface-soft);
	font-size: 11px;
	line-height: 1.35;
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

/* ── ECharts containers ─────────────────────────────────────────────────────── */
:deep(.md-echarts) {
	margin: 8px 0;
	border-radius: 6px;
}

/* 过程详情有界，防止长 SQL/JSON 撑满屏 */
.step-content {
	max-height: 96px;
	overflow: auto;
}
.workflow-timeline.is-completed {
	opacity: 0.95;
}

@media (prefers-reduced-motion: reduce) {
	.badge-dot {
		animation: none !important;
		opacity: 0.85;
	}

	* {
		transition: none !important;
		animation: none !important;
	}
}
</style>
