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
	height: calc(100vh - 52px);
	overflow: hidden;
	background: var(--da-surface-soft, #f8fafc);
}

.chat-body {
	flex: 1;
	display: flex;
	flex-direction: column;
	overflow: hidden;
	min-width: 0;
	background: var(--da-surface, #fff);
	border-left: 1px solid var(--da-line-soft, #e8edf2);
	box-shadow: -4px 0 16px rgba(15, 23, 42, 0.03);
}
</style>
