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
				class="preset-chip"
				role="listitem"
				:disabled="store.isStreaming || sending"
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
	padding: 40px 24px;
	text-align: center;
	max-width: 600px;
	margin: 0 auto;
	width: 100%;
}

.agent-avatar-wrap {
	margin-bottom: 16px;
}

.agent-avatar {
	box-shadow:
		0 1px 2px rgba(15, 23, 42, 0.06),
		0 12px 28px rgba(30, 64, 175, 0.14);
}

.welcome-kicker {
	margin: 0 0 6px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted, #64748b);
}

.welcome-title {
	font-size: clamp(22px, 3.2vw, 28px);
	font-weight: 600;
	color: var(--da-ink, #0f172a);
	margin: 0 0 14px;
	letter-spacing: -0.02em;
	line-height: 1.25;
}

.agent-name {
	color: var(--da-primary, #1e40af);
	font-weight: 700;
}

.welcome-line {
	max-width: 220px;
	margin: 0 auto 14px;
	background: var(--da-line, #d9d9d9);
}

.welcome-desc {
	margin: 0;
	font-size: 14px;
	font-weight: 400;
	color: var(--da-muted, #64748b);
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
	border: 1px solid var(--da-line-soft, #e8edf2);
	background: var(--da-surface, #fff);
	color: var(--da-ink, #0f172a);
	border-radius: 999px;
	padding: 8px 14px;
	min-height: 36px;
	font-size: 12.5px;
	font-weight: 500;
	line-height: 1.35;
	cursor: pointer;
	transition:
		border-color 0.15s ease,
		background 0.15s ease,
		box-shadow 0.15s ease;
	box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
	text-align: left;
	max-width: 100%;
}

.preset-chip:hover:not(:disabled) {
	border-color: #93c5fd;
	background: var(--da-primary-soft, #eff6ff);
	color: var(--da-primary, #1e40af);
}

.preset-chip:focus-visible {
	outline: 2px solid var(--da-accent, #3b82f6);
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
</style>
