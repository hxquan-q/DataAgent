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
	<div class="chat-page">
		<template v-if="currentAgentId">
			<ChatSidebar />
			<div class="chat-body">
				<ChatMessageList />
				<ChatInputArea />
			</div>
		</template>
		<!-- R157: no agent selected -->
		<section v-else class="chat-no-agent" role="status">
			<div class="chat-no-agent__card">
				<div class="chat-no-agent__icon" aria-hidden="true">
					<svg width="40" height="40" viewBox="0 0 24 24" fill="none">
						<path
							d="M12 2a4 4 0 0 1 4 4v1h1a3 3 0 0 1 3 3v7a3 3 0 0 1-3 3h-1v1a4 4 0 0 1-8 0v-1H8a3 3 0 0 1-3-3V10a3 3 0 0 1 3-3h1V6a4 4 0 0 1 4-4Z"
							stroke="currentColor"
							stroke-width="1.5"
						/>
					</svg>
				</div>
				<p class="chat-no-agent__kicker">Data Agent</p>
				<h1 class="chat-no-agent__title">请先选择智能体</h1>
				<p class="chat-no-agent__desc">
					数据问答依赖智能体绑定的数据源与全局模型配置。请从智能体列表进入，或先完成模型 / 数据源准备。
				</p>
				<div class="chat-no-agent__actions">
					<button type="button" class="chat-no-agent__btn chat-no-agent__btn--primary" @click="goAgents">
						选择智能体
					</button>
					<button type="button" class="chat-no-agent__btn" @click="goModels">
						配置模型
					</button>
					<button type="button" class="chat-no-agent__btn" @click="goDatasources">
						配置数据源
					</button>
				</div>
			</div>
		</section>
	</div>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted, watch, computed } from 'vue';
import { useChatStore } from '~/stores/chat';
import agentService from '~/services/agent/index';
import ChatSidebar from '~/components/chat/ChatSidebar.vue';
import ChatMessageList from '~/components/chat/ChatMessageList.vue';
import ChatInputArea from '~/components/chat/ChatInputArea.vue';

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

async function init(agentId: number) {
	store.currentAgentId = agentId;

	// Load agent info
	try {
		const agent = await agentService.get(agentId);
		if (agent) {
			store.currentAgentName = agent.name || '';
			store.currentAgentAvatar = agent.avatar || '';
			store.currentAgentDescription = agent.description || '';
		}
	} catch { /* ignore */ }

	// Load active model — handled by store.loadSessions

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
	height: calc(100vh - var(--da-header-height, 52px));
	overflow: hidden;
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
	border-left: 1px solid var(--da-line-soft);
}
/* Soft fade above floating dock — quieter than hard chrome */
.chat-body::after {
	content: '';
	pointer-events: none;
	position: absolute;
	left: 0;
	right: 0;
	bottom: 0;
	height: 88px;
	z-index: 4;
	background: linear-gradient(
		to top,
		color-mix(in srgb, var(--da-surface-soft) 96%, transparent) 0%,
		color-mix(in srgb, var(--da-surface-soft) 55%, transparent) 42%,
		transparent 100%
	);
}

/* Empty agent gate — DEEIX-like centered paper card */
.chat-no-agent {
	flex: 1;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 24px 16px;
	background:
		radial-gradient(
			900px 360px at 50% -80px,
			color-mix(in srgb, var(--da-primary) 10%, transparent),
			transparent 65%
		),
		var(--da-surface-soft);
}

.chat-no-agent__card {
	width: min(520px, 100%);
	padding: 32px 28px;
	text-align: center;
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-lg);
	box-shadow: var(--da-shadow-lg);
}

.chat-no-agent__icon {
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 64px;
	height: 64px;
	margin: 0 auto 16px;
	border-radius: 50%;
	color: var(--da-primary);
	background: var(--da-primary-soft);
	box-shadow: var(--da-shadow-sm);
}

.chat-no-agent__kicker {
	margin: 0 0 8px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
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
	margin: 0 auto 22px;
	max-width: 420px;
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
	min-height: var(--da-control-height-md);
	padding: 8px 16px;
	border-radius: 999px;
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface);
	color: var(--da-ink);
	font: inherit;
	font-size: 13px;
	font-weight: 600;
	cursor: pointer;
	box-shadow: var(--da-shadow-sm);
	transition:
		background var(--da-dur-fast) var(--da-ease-out),
		border-color var(--da-dur-fast) var(--da-ease-out),
		box-shadow var(--da-dur-fast) var(--da-ease-out);
}

.chat-no-agent__btn:hover {
	border-color: color-mix(in srgb, var(--da-primary) 35%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
}

.chat-no-agent__btn:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.chat-no-agent__btn--primary {
	background: var(--da-primary);
	border-color: var(--da-primary);
	color: var(--da-on-primary);
	box-shadow: 0 8px 18px color-mix(in srgb, var(--da-primary) 28%, transparent);
}

.chat-no-agent__btn--primary:hover {
	background: color-mix(in srgb, var(--da-primary) 88%, #000);
	border-color: transparent;
	color: var(--da-on-primary);
}

@media (max-width: 480px) {
	.chat-no-agent__card {
		padding: 24px 18px;
	}
	.chat-no-agent__btn {
		width: 100%;
	}
}

@media (prefers-reduced-motion: reduce) {
	.chat-no-agent__btn {
		transition: none;
	}
}

@media (max-width: 640px) {
	.chat-no-agent {
		padding: 24px 16px;
	}
	.chat-no-agent__card {
		padding: 28px 18px;
	}
	.chat-no-agent__actions {
		flex-direction: column;
	}
	.chat-no-agent__btn {
		width: 100%;
	}
}
</style>
