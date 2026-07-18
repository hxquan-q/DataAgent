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
		<div class="agent-avatar-wrap da-reveal">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'primary'"
				size="64"
				rounded="circle"
				class="agent-avatar"
			>
				<v-icon v-if="!store.currentAgentAvatar" size="32" color="white">
					mdi-robot-outline
				</v-icon>
			</v-avatar>
		</div>

		<p class="welcome-kicker da-reveal da-reveal-delay-1">Data Agent</p>
		<h2 class="welcome-title da-reveal da-reveal-delay-1">
			您好，我是
			<span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>

		<div class="welcome-line da-hairline da-reveal-delay-2" aria-hidden="true" />

		<p class="welcome-desc da-reveal da-reveal-delay-2">
			{{
				store.currentAgentDescription ||
				'分析表结构、生成 SQL、输出可视化报告——把问题丢给我即可。'
			}}
		</p>

		<!-- R159: readiness checklist -->
		<ul v-if="!readyToChat" class="ready-list da-reveal da-reveal-delay-3" aria-label="开始前检查">
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
		<p v-else class="ready-ok da-reveal da-reveal-delay-3" role="status">已就绪，直接提问或点下方推荐问题。</p>

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
		// R184: 点击推荐问题时给出明确路径
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
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: 36px 20px;
	text-align: center;
	max-width: 600px;
	margin: 0 auto;
	width: 100%;
}

.agent-avatar-wrap {
	margin-bottom: 16px;
}

.agent-avatar {
	box-shadow: var(--da-shadow-md);
}

.welcome-kicker {
	margin: 0 0 6px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.welcome-title {
	font-family: var(--da-font-display);
	font-size: clamp(24px, 3.4vw, 32px);
	font-weight: 500;
	color: var(--da-ink, #1a2332);
	margin: 0 0 14px;
	letter-spacing: -0.02em;
	line-height: 1.25;
}

.agent-name {
	color: var(--da-primary, #2f84d6);
	font-weight: 600;
}

.welcome-line {
	max-width: 220px;
	margin: 0 auto 14px;
	background: var(--da-line);
}

.welcome-desc {
	margin: 0;
	font-size: 14px;
	font-weight: 400;
	color: var(--da-muted);
	max-width: 440px;
	line-height: 1.6;
	letter-spacing: -0.01em;
}

.preset-row {
	display: flex;
	flex-wrap: wrap;
	justify-content: center;
	gap: 8px;
	margin-top: 22px;
	max-width: 560px;
}

.preset-chip {
	appearance: none;
	border: 1px solid var(--da-line-soft, #e4edf5);
	background: var(--da-surface, #fff);
	color: var(--da-ink, #1a2332);
	border-radius: 999px;
	padding: 8px 14px;
	min-height: 36px;
	font-size: 12.5px;
	font-weight: 500;
	line-height: 1.35;
	cursor: pointer;
	transition:
		border-color var(--da-dur-fast, 0.15s) var(--da-ease-out),
		background var(--da-dur-fast, 0.15s) var(--da-ease-out),
		box-shadow var(--da-dur-fast, 0.15s) var(--da-ease-out);
	box-shadow: var(--da-shadow-sm);
	text-align: left;
	max-width: 100%;
}

.preset-chip:hover:not(:disabled) {
	border-color: color-mix(in srgb, var(--da-primary, #2f84d6) 40%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary, #2f84d6);
	box-shadow: var(--da-shadow-md);
}

.preset-chip:focus-visible {
	outline: 2px solid var(--da-ring, #2f84d6);
	outline-offset: 2px;
}

.preset-chip:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}

@media (prefers-reduced-motion: reduce) {
	.preset-chip {
		transition: none;
	}
}

.ready-list {
	list-style: none;
	margin: 18px 0 0;
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
	padding: 8px 12px;
	border-radius: var(--da-radius-md);
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface, #fff);
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
	outline: 2px solid var(--da-accent);
	outline-offset: 2px;
}

.ready-ok {
	margin: 16px 0 0;
	font-size: 12.5px;
	font-weight: 600;
	color: var(--da-success);
}
</style>
