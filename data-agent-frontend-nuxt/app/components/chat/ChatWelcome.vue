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
	<div class="welcome-wrap">
		<div class="welcome-title-group da-reveal">
			<h1 class="welcome-title">
				{{ greetingLine }}
			</h1>
			<span v-if="store.currentAgentName" class="welcome-badge" :title="store.currentAgentDescription || store.currentAgentName">
				{{ store.currentAgentName }}
			</span>
		</div>

		<p v-if="store.currentAgentDescription" class="welcome-desc da-reveal da-reveal-delay-1">
			{{ store.currentAgentDescription }}
		</p>
		<p v-else class="welcome-desc da-reveal da-reveal-delay-1">
			分析表结构、生成 SQL、输出可视化报告——把问题丢给我即可。
		</p>

		<!-- R159: readiness checklist -->
		<ul v-if="!readyToChat" class="ready-list da-reveal da-reveal-delay-2" aria-label="开始前检查">
			<li class="ready-item" :class="{ ok: hasModel }">
				<span class="ready-dot" aria-hidden="true" />
				<span class="ready-text">{{ hasModel ? 'CHAT 模型已就绪' : '需要激活 CHAT 模型' }}</span>
				<button v-if="!hasModel" type="button" class="ready-link" @click="goModels">去配置</button>
			</li>
			<li class="ready-item" :class="{ ok: hasDatasource }">
				<span class="ready-dot" aria-hidden="true" />
				<span class="ready-text">{{ hasDatasource ? '数据源已绑定' : '需要绑定并激活数据源' }}</span>
				<button v-if="!hasDatasource" type="button" class="ready-link" @click="goDatasource">去绑定</button>
			</li>
		</ul>
		<p v-else class="ready-ok da-reveal da-reveal-delay-2" role="status">已就绪 · 直接提问或点下方推荐</p>

		<div
			v-if="chips.length"
			class="preset-row da-reveal da-reveal-delay-3"
			role="list"
			aria-label="推荐问题"
		>
			<button
				v-for="(q, i) in chips"
				:key="i"
				type="button"
				:class="['preset-chip', { 'preset-chip--blocked': !readyToChat }]"
				role="listitem"
				:disabled="store.isStreaming || sending"
				:title="readyToChat ? q : '请先完成模型与数据源配置'"
				@click="ask(q)"
			>
				{{ q }}
			</button>
		</div>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';
import presetQuestionService from '~/services/presetQuestion/index';

const store = useChatStore();
const sending = ref(false);
const presets = ref<string[]>([]);

const hasModel = computed(
	() => store.chatModels.length > 0 && !!store.activeModelConfig,
);
const hasDatasource = computed(
	() => store.allDatasources.length > 0 && !!store.activeDatasource,
);
const readyToChat = computed(() => hasModel.value && hasDatasource.value);

/** DEEIX empty: one large greeting line, agent as quiet badge */
const greetingLine = computed(() => {
	const hour = new Date().getHours();
	if (hour < 11) return '早上好，今天想分析什么？';
	if (hour < 14) return '中午好，有什么可以帮你？';
	if (hour < 18) return '下午好，从哪个问题开始？';
	return '晚上好，需要我帮你看数据吗？';
});

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

const FALLBACK = [
	'最近有哪些关键指标异常？',
	'按维度汇总核心业务数据',
	'对比本月与上月变化趋势',
];

const chips = computed(() =>
	presets.value.length ? presets.value.slice(0, 6) : FALLBACK,
);

async function loadPresets(agentId?: number) {
	if (!agentId) {
		presets.value = [];
		return;
	}
	try {
		const list = await presetQuestionService.list(agentId);
		presets.value = list
			.filter((q) => q.isActive !== false && q.question?.trim())
			.sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
			.map((q) => q.question.trim());
	} catch {
		presets.value = [];
	}
}

async function ask(question: string) {
	const q = question.trim();
	if (!q || store.isStreaming || sending.value) return;
	const agentId = store.currentAgentId;
	if (!agentId) return;
	if (!readyToChat.value) {
		if (!hasModel.value) {
			navigateTo('/system/model-config');
		} else if (!hasDatasource.value) {
			goDatasource();
		}
		return;
	}

	sending.value = true;
	try {
		if (!store.currentSession) {
			await store.createNewSession(agentId);
		}
		await store.sendMessage(q);
	} catch (e) {
		console.error('预设问题发送失败', e);
	} finally {
		sending.value = false;
	}
}

watch(
	() => store.currentAgentId,
	(id) => {
		void loadPresets(id);
	},
	{ immediate: true },
);
</script>

<style scoped>
/* DEEIX empty: large economist title, no avatar chrome */
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	padding: clamp(24px, 6vh, 64px) 16px 16px;
	min-height: 0;
	max-width: min(100%, var(--da-answer-max, 880px));
	margin: 0 auto;
	box-sizing: border-box;
}

.welcome-title-group {
	position: relative;
	display: inline-flex;
	max-width: calc(100% - 2rem);
	justify-content: center;
	align-items: flex-start;
}

.welcome-title {
	margin: 0;
	font-family: var(--da-font-display);
	font-size: clamp(22px, 3.4vw, 32px);
	font-weight: 500;
	line-height: 1.12;
	letter-spacing: -0.005em;
	color: var(--da-ink);
	text-wrap: balance;
}

.welcome-badge {
	position: absolute;
	left: calc(100% + 6px);
	top: 2px;
	max-width: 7.5rem;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	padding: 1px 6px;
	border-radius: 999px;
	border: 1px solid color-mix(in srgb, var(--da-line) 70%, transparent);
	background: color-mix(in srgb, var(--da-surface) 70%, transparent);
	color: var(--da-muted);
	font-family: var(--da-font-sans);
	font-size: 9px;
	font-weight: 600;
	line-height: 1.4;
	letter-spacing: 0.02em;
}

.welcome-desc {
	margin: 14px 0 0;
	max-width: 28rem;
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: 14px;
	font-weight: 400;
	line-height: 1.6;
	letter-spacing: -0.01em;
	color: var(--da-muted);
}

.preset-row {
	display: flex;
	flex-wrap: wrap;
	justify-content: center;
	gap: 8px;
	margin-top: 28px;
	max-width: 36rem;
	width: 100%;
}

.preset-chip {
	appearance: none;
	border: 0.5px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	background: color-mix(in srgb, var(--da-surface) 88%, transparent);
	color: var(--da-ink);
	border-radius: 999px;
	padding: 8px 14px;
	min-height: 36px;
	font-size: 13px;
	font-weight: 500;
	line-height: 1.4;
	cursor: pointer;
	transition:
		border-color var(--da-dur-fast) var(--da-ease-out),
		background var(--da-dur-fast) var(--da-ease-out),
		box-shadow var(--da-dur-fast) var(--da-ease-out),
		color var(--da-dur-fast) var(--da-ease-out);
	box-shadow: var(--da-shadow-sm);
	text-align: left;
	max-width: 100%;
	backdrop-filter: blur(6px);
}

.preset-chip:hover:not(:disabled) {
	border-color: color-mix(in srgb, var(--da-primary) 40%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
	box-shadow: var(--da-shadow-md);
}

.preset-chip:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.preset-chip:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}

.preset-chip--blocked {
	opacity: 0.7;
}

.ready-list {
	list-style: none;
	margin: 22px 0 0;
	padding: 0;
	display: flex;
	flex-direction: column;
	gap: 8px;
	max-width: 360px;
	width: 100%;
}

.ready-item {
	display: flex;
	align-items: center;
	gap: 8px;
	padding: 10px 12px;
	border-radius: var(--da-radius-md);
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface);
	box-shadow: var(--da-shadow-sm);
	font-size: 13px;
	color: var(--da-ink);
	text-align: left;
}

.ready-item.ok {
	border-color: color-mix(in srgb, var(--da-success) 25%, white);
	background: color-mix(in srgb, var(--da-success) 8%, white);
	color: var(--da-success);
}

.ready-dot {
	width: 8px;
	height: 8px;
	border-radius: 50%;
	background: var(--da-warning);
	flex-shrink: 0;
}

.ready-item.ok .ready-dot {
	background: var(--da-success);
}

.ready-text {
	flex: 1;
	min-width: 0;
}

.ready-link {
	appearance: none;
	border: none;
	background: transparent;
	color: var(--da-primary);
	font-size: 12.5px;
	font-weight: 600;
	cursor: pointer;
	padding: 0;
	white-space: nowrap;
}

.ready-link:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.ready-ok {
	margin: 18px 0 0;
	font-size: 12.5px;
	font-weight: 500;
	color: var(--da-muted);
	letter-spacing: -0.01em;
}

@media (max-width: 640px) {
	.welcome-badge {
		position: static;
		display: inline-block;
		margin: 10px auto 0;
		max-width: 12rem;
	}

	.welcome-title-group {
		flex-direction: column;
		align-items: center;
	}
}

@media (prefers-reduced-motion: reduce) {
	.preset-chip {
		transition: none;
	}
}

/* R231: empty under DEEIX empty stage — less card chrome */
.ready-item {
	border: none !important;
	box-shadow: none !important;
	background: transparent !important;
	padding: 4px 0 !important;
	justify-content: center;
}
.ready-list {
	align-items: center;
}
.preset-chip {
	box-shadow: none !important;
	border-color: color-mix(in srgb, var(--da-line) 45%, transparent) !important;
	background: transparent !important;
}
.preset-chip:hover:not(:disabled) {
	box-shadow: none !important;
}

/* R231: DEEIX empty = title first; soft-pedal rest */
.welcome-desc {
	display: none;
}
.ready-list {
	display: none;
}
.ready-ok {
	margin-top: 10px !important;
	font-size: 12px !important;
}
</style>
