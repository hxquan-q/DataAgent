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
	<div class="chat-page" :class="{ 'chat-page--empty': isEmptyCanvas }">
		<template v-if="currentAgentId">
			<!-- DEEIX empty: greeting + same composer nested & centered -->
			<div v-if="isEmptyCanvas" class="chat-empty-stage">
				<div class="chat-empty-stage__inner">
					<ChatWelcome />
					<div class="chat-empty-stage__composer">
						<ChatInputArea />
					</div>
				</div>
			</div>
			<!-- Conversation: messages + bottom dock -->
			<div v-else class="chat-body">
				<ChatMessageList />
				<ChatInputArea />
			</div>
		</template>
		<section v-else class="chat-no-agent" role="status">
			<div class="chat-no-agent__card">
				<p class="chat-no-agent__kicker">Data Agent</p>
				<h1 class="chat-no-agent__title">请先选择智能体</h1>
				<p class="chat-no-agent__desc">
					数据问答依赖智能体绑定的数据源与全局模型配置。请从左侧选择智能体，或先完成模型 / 数据源准备。
				</p>
				<div class="chat-no-agent__actions">
					<button type="button" class="chat-no-agent__btn chat-no-agent__btn--primary" @click="goAgents">
						选择智能体
					</button>
					<button type="button" class="chat-no-agent__btn" @click="goModels">配置模型</button>
					<button type="button" class="chat-no-agent__btn" @click="goDatasources">配置数据源</button>
				</div>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue';
import { useChatStore } from '~/stores/chat';
import agentService from '~/services/agent/index';
import ChatMessageList from '~/components/chat/ChatMessageList.vue';
import ChatInputArea from '~/components/chat/ChatInputArea.vue';
import ChatWelcome from '~/components/chat/ChatWelcome.vue';

definePageMeta({ layout: 'workspace' });

const route = useRoute();
const store = useChatStore();

useHead({
	title: computed(() =>
		store.currentAgentName ? `${store.currentAgentName} · 数据问答` : '数据问答',
	),
});

const currentAgentId = computed(() => {
	const q = route.query.agentId;
	return q ? Number(q) : undefined;
});

/** DEEIX empty canvas: no messages and not streaming */
const isEmptyCanvas = computed(
	() =>
		!!currentAgentId.value &&
		!store.isStreaming &&
		!store.isReportStreaming &&
		(!store.currentSession || store.currentMessages.length === 0),
);

async function init(agentId: number) {
	store.currentAgentId = agentId;
	try {
		const agent = await agentService.get(agentId);
		if (agent) {
			store.currentAgentName = agent.name || '';
			store.currentAgentAvatar = agent.avatar || '';
			store.currentAgentDescription = agent.description || '';
		}
	} catch {
		/* ignore */
	}
	store.connectSessionStream(agentId);
	await store.loadSessions(agentId);
}

function goAgents() {
	navigateTo('/system/agents');
}
function goModels() {
	navigateTo('/system/model-config');
}
function goDatasources() {
	navigateTo('/system/data-sources');
}

onMounted(async () => {
	if (currentAgentId.value) await init(currentAgentId.value);
});

watch(currentAgentId, async (newId, oldId) => {
	if (newId && newId !== oldId) {
		store.sessions = [];
		store.currentSession = null;
		store.currentMessages = [];
		store.isStreaming = false;
		store.nodeBlocks = [];
		await init(newId);
	}
});

onUnmounted(() => {
	store.disconnectSessionStream();
});
</script>

<style scoped>
.chat-page {
	display: flex;
	height: 100%;
	min-height: 0;
	overflow: hidden;
	background: transparent;
}
@media (max-width: 768px) {
	.chat-page {
		/* fill workspace-main under mobile bar */
		min-height: 0;
		flex: 1;
	}
	.chat-empty-stage {
		padding: 12px 12px calc(16px + var(--da-safe-bottom, 0px));
	}
	.chat-empty-stage__inner {
		width: 100%;
	}
	.chat-no-agent__card {
		width: min(100%, 420px);
		padding: 20px 16px;
	}
	.chat-body {
		flex: 1;
		min-height: 0;
	}
}

.chat-page--empty {
	background: var(--da-surface-soft);
}

.chat-body {
	position: relative;
	flex: 1;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	min-width: 0;
	background: transparent;
}

/* no bottom fog — keep canvas clean */

/* DEEIX empty stage: title + composer one unit */
.chat-empty-stage {
	flex: 1;
	min-height: 0;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 16px 20px 28px;
	overflow: auto;
}

.chat-empty-stage__inner {
	width: min(100%, var(--da-chat-max, 1080px));
	display: flex;
	flex-direction: column;
	align-items: center;
}

.chat-empty-stage__composer {
	width: min(100%, var(--da-chat-max, 1080px));
	margin-top: 4px;
}

.chat-empty-stage__composer :deep(.composer) {
	width: 100%;
	margin: 0;
	max-width: 100%;
}

.chat-empty-stage :deep(.welcome-wrap) {
	min-height: 0;
	padding: 8px 12px 4px;
	max-width: 100%;
}

.chat-empty-stage :deep(.preset-row) {
	margin-top: 16px;
}

.chat-no-agent {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 24px 16px;
	background: var(--da-surface-soft);
}

.chat-no-agent__card {
	width: min(480px, 100%);
	padding: 28px 24px;
	text-align: center;
}

.chat-no-agent__kicker {
	margin: 0 0 8px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.1em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.chat-no-agent__title {
	margin: 0 0 12px;
	font-family: var(--da-font-display);
	font-size: clamp(22px, 3vw, 28px);
	font-weight: 500;
	letter-spacing: -0.02em;
	color: var(--da-ink);
}

.chat-no-agent__desc {
	margin: 0 auto 20px;
	max-width: 400px;
	font-size: 14px;
	line-height: 1.6;
	color: var(--da-muted);
}

.chat-no-agent__actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
	justify-content: center;
}

.chat-no-agent__btn {
	appearance: none;
	min-height: 36px;
	padding: 8px 16px;
	border-radius: 999px;
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface);
	color: var(--da-ink);
	font: inherit;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
}

.chat-no-agent__btn--primary {
	background: var(--da-primary);
	border-color: var(--da-primary);
	color: var(--da-on-primary);
}

.chat-no-agent__btn:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}
</style>
