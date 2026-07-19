<template>
	<div
		ref="timelineRef"
		class="process-bubble"
		:class="{
			'process-bubble--done': completed,
			'process-bubble--live': !completed,
			'process-bubble--open': processOpen,
		}"
	>
		<!-- DEEIX: one small process chip -->
		<button
			type="button"
			class="process-chip"
			:aria-expanded="processOpen ? 'true' : 'false'"
			@click="processOpen = !processOpen"
		>
			<span class="process-chip__dot" :class="{ live: !completed }" aria-hidden="true" />
			<span class="process-chip__title">
				{{ completed ? '过程' : '分析中' }}
			</span>
			<span v-if="timelineSteps.length" class="process-chip__meta">
				{{ doneCount }}/{{ timelineSteps.length }}
			</span>
			<span v-if="!completed && activeLabel" class="process-chip__active">
				{{ activeLabel }}
			</span>
			<v-icon size="14" class="process-chip__chevron">
				{{ processOpen ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
			</v-icon>
		</button>

		<!-- Expanded: tiny step bubbles (not heavy timeline chrome) -->
		<div v-show="processOpen" class="process-steps" role="list">
			<button
				type="button"
				class="process-steps__toggle"
				@click.stop="toggleAll"
			>
				{{ allExpanded ? '全部折叠' : '全部展开' }}
			</button>

			<div
				v-for="step in timelineSteps"
				:key="step.nodeName"
				class="step-bubble"
				:class="{
					'step-bubble--done': step.status === 'done',
					'step-bubble--active': step.status === 'active',
					'step-bubble--open': step.expanded,
					'step-bubble--report': step.isReport,
				}"
				role="listitem"
			>
				<button type="button" class="step-bubble__head" @click="toggleStep(step.nodeName)">
					<span class="step-bubble__status" aria-hidden="true">
						<span v-if="step.status === 'done'">✓</span>
						<span v-else-if="step.status === 'active'" class="step-bubble__pulse" />
						<span v-else>·</span>
					</span>
					<span class="step-bubble__label">{{ step.label }}</span>
					<span v-if="step.status === 'active'" class="step-bubble__badge">进行中</span>
					<v-icon size="14" class="step-bubble__chevron">
						{{ step.expanded ? 'mdi-chevron-up' : 'mdi-chevron-down' }}
					</v-icon>
				</button>

				<div v-show="step.expanded" class="step-bubble__body">
					<ChatResultSet
						v-if="step.block[0]?.textType === 'RESULT_SET' && step.block[0]?.text"
						:data="safeParseJson(step.block[0].text)"
						:page-size="10"
						:pending-report="!completed && !step.isReport"
					/>
					<div v-else-if="step.isReport" class="step-bubble__brief">
						<span v-if="step.status === 'active'">报告生成中…内容在下方实时展示</span>
						<span v-else>报告已生成，见下方答案</span>
					</div>
					<div v-else-if="isPureCodeBlock(step.block)" class="step-bubble__code" v-html="renderCode(step.block)" />
					<div v-else class="step-bubble__text" v-html="renderTextWithJsonDetection(step.block)" />
				</div>
			</div>
		</div>
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
// R231: process tree collapsed by default
const processOpen = ref(false);

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
	return 'grey';
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
	() => props.nodeBlocks.length,
	() => {
		if (!processOpen.value) return;
		nextTick(() => renderECharts(timelineRef.value));
	},
);
</script>


<style scoped>
/* DEEIX process: small chip + mini step bubbles */
.process-bubble {
	width: 100%;
	max-width: min(100%, var(--da-answer-max, 960px));
}

.process-chip {
	appearance: none;
	display: inline-flex;
	align-items: center;
	gap: 6px;
	max-width: 100%;
	min-height: 28px;
	padding: 4px 10px 4px 8px;
	border-radius: 999px;
	border: 0.5px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	background: color-mix(in srgb, var(--da-surface) 88%, var(--da-surface-soft));
	box-shadow: var(--da-shadow-sm);
	color: var(--da-muted);
	font: inherit;
	font-size: 12.5px;
	font-weight: 500;
	line-height: 1.2;
	cursor: pointer;
	text-align: left;
	transition:
		border-color var(--da-dur-fast) var(--da-ease-out),
		color var(--da-dur-fast) var(--da-ease-out),
		background var(--da-dur-fast) var(--da-ease-out);
}
.process-chip:hover {
	color: var(--da-ink);
	border-color: color-mix(in srgb, var(--da-line) 80%, transparent);
	background: var(--da-surface);
}
.process-chip:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.process-bubble--live .process-chip {
	border-color: color-mix(in srgb, var(--da-primary) 28%, transparent);
	color: var(--da-ink);
}
.process-bubble--done .process-chip {
	opacity: 0.72;
	box-shadow: none;
	background: transparent;
	border-color: color-mix(in srgb, var(--da-line) 40%, transparent);
}
.process-bubble--done .process-chip:hover {
	opacity: 1;
	background: var(--da-surface);
}

.process-chip__dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--da-success);
	flex-shrink: 0;
}
.process-chip__dot.live {
	background: var(--da-primary);
	animation: processPulse 1.2s ease-in-out infinite;
}
.process-chip__title {
	font-weight: 600;
	color: inherit;
	flex-shrink: 0;
}
.process-chip__meta {
	font-weight: 400;
	opacity: 0.85;
	flex-shrink: 0;
}
.process-chip__active {
	min-width: 0;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	opacity: 0.8;
	font-weight: 400;
}
.process-chip__chevron {
	margin-left: 2px;
	opacity: 0.7;
	flex-shrink: 0;
}

.process-steps {
	margin-top: 8px;
	display: flex;
	flex-direction: column;
	gap: 6px;
	padding-left: 2px;
}
.process-steps__toggle {
	appearance: none;
	align-self: flex-start;
	border: none;
	background: transparent;
	color: var(--da-muted);
	font: inherit;
	font-size: 11.5px;
	font-weight: 600;
	cursor: pointer;
	padding: 0 2px 2px;
}
.process-steps__toggle:hover {
	color: var(--da-primary);
}

.step-bubble {
	border-radius: 12px;
	border: 0.5px solid color-mix(in srgb, var(--da-line-soft) 90%, transparent);
	background: color-mix(in srgb, var(--da-surface) 70%, var(--da-surface-soft));
	overflow: hidden;
	transition: border-color var(--da-dur-fast) var(--da-ease-out), background var(--da-dur-fast) var(--da-ease-out);
}
.step-bubble--active {
	border-color: color-mix(in srgb, var(--da-primary) 30%, transparent);
	background: color-mix(in srgb, var(--da-primary-soft) 55%, var(--da-surface));
}
.step-bubble--done {
	opacity: 0.92;
}
.step-bubble--open {
	background: var(--da-surface);
}

.step-bubble__head {
	appearance: none;
	width: 100%;
	display: flex;
	align-items: center;
	gap: 8px;
	min-height: 32px;
	padding: 6px 10px;
	border: none;
	background: transparent;
	color: var(--da-muted);
	font: inherit;
	font-size: 12.5px;
	cursor: pointer;
	text-align: left;
}
.step-bubble__head:hover {
	color: var(--da-ink);
}
.step-bubble--active .step-bubble__head {
	color: var(--da-ink);
}
.step-bubble__status {
	width: 14px;
	height: 14px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	font-size: 11px;
	font-weight: 700;
	color: var(--da-success);
	flex-shrink: 0;
}
.step-bubble--active .step-bubble__status {
	color: var(--da-primary);
}
.step-bubble__pulse {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--da-primary);
	animation: processPulse 1.2s ease-in-out infinite;
}
.step-bubble__label {
	flex: 1;
	min-width: 0;
	font-weight: 500;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.step-bubble__badge {
	font-size: 10.5px;
	font-weight: 600;
	color: var(--da-primary);
	background: color-mix(in srgb, var(--da-primary-soft) 80%, transparent);
	border-radius: 999px;
	padding: 1px 6px;
	flex-shrink: 0;
}
.step-bubble__chevron {
	opacity: 0.65;
	flex-shrink: 0;
}

.step-bubble__body {
	padding: 0 10px 10px 32px;
	font-size: 12.5px;
	line-height: 1.55;
	color: var(--da-ink);
}
.step-bubble__brief {
	color: var(--da-muted);
	font-size: 12.5px;
}
.step-bubble__text :deep(p) {
	margin: 0 0 6px;
}
.step-bubble__code :deep(pre.tl-code),
.step-bubble__body :deep(pre.tl-code) {
	margin: 0;
	padding: 8px 10px;
	border-radius: 10px;
	background: color-mix(in srgb, var(--da-surface-soft) 90%, #0f172a);
	font-size: 11.5px;
	overflow: auto;
	max-height: 220px;
}

@keyframes processPulse {
	0%, 100% { opacity: 0.4; }
	50% { opacity: 1; }
}

@media (max-width: 768px) {
	.process-chip {
		max-width: 100%;
		font-size: 12px;
	}
	.step-bubble__body {
		padding-left: 12px;
		padding-right: 8px;
	}
}
@media (prefers-reduced-motion: reduce) {
	.process-chip__dot.live,
	.step-bubble__pulse {
		animation: none;
	}
}
</style>
