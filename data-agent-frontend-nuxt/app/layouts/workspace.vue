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
	<v-app id="app" class="workspace-app">
		<div class="workspace">
			<!-- Single rail: brand · agent · sessions (DEEIX workspace) -->
			<aside
				class="workspace-rail"
				:class="{ 'workspace-rail--collapsed': store.chatSidebarCollapsed }"
				aria-label="工作台导航"
			>
				<div class="workspace-rail__panel">
					<div class="ws-brand">
						<button type="button" class="ws-brand__title" title="数据问答" @click="goChatHome">
							DataAgent
						</button>
						<button
							type="button"
							class="ws-icon-btn"
							title="折叠侧栏"
							aria-label="折叠侧栏"
							@click="store.chatSidebarCollapsed = true"
						>
							<v-icon size="18">mdi-chevron-left</v-icon>
						</button>
					</div>

					<button
						type="button"
						class="ws-new"
						:disabled="!selectedAgentId"
						title="新建分析会话"
						@click="handleCreateNewSession"
					>
						<v-icon size="16" class="me-1">mdi-plus</v-icon>
						新会话
					</button>

					<div class="ws-agent">
						<label class="ws-agent__label" for="ws-agent-select">智能体</label>
						<v-select
							id="ws-agent-select"
							v-model="selectedAgentId"
							:items="agentOptions"
							item-title="title"
							item-value="value"
							variant="outlined"
							density="compact"
							hide-details
							placeholder="选择智能体"
							class="ws-agent__select"
							menu-icon="mdi-chevron-down"
							:menu-props="{ contentClass: 'ws-agent-menu', offset: [0, 6] }"
							:list-props="{ bgColor: 'var(--da-surface)', elevation: 2 }"
							item-color="primary"
							@update:model-value="handleAgentSwitch"
						>
							<template #selection="{ item }">
								<span class="ws-agent__sel">{{ item.raw.title }}</span>
							</template>
							<template #item="{ props: itemProps, item }">
								<v-list-item
									v-bind="itemProps"
									:title="item.raw.title"
									:subtitle="item.raw.subtitle || undefined"
									density="compact"
								>
									<template #append>
										<v-icon
											v-if="item.raw.value === selectedAgentId"
											icon="mdi-check"
											color="primary"
											size="16"
										/>
									</template>
								</v-list-item>
							</template>
						</v-select>
					</div>

					<div class="ws-sessions">
						<ChatSidebar variant="workspace" />
					</div>

					<div class="ws-footer">
						<button type="button" class="ws-footer__admin" @click="goAdmin">
							管理后台
						</button>
						<div class="ws-footer__user">
							<span class="ws-footer__name" :title="authDisplayName">{{ authDisplayName }}</span>
							<button
								type="button"
								class="ws-icon-btn ws-icon-btn--danger"
								title="退出登录"
								aria-label="退出登录"
								@click="onLogout"
							>
								<v-icon size="18">mdi-logout</v-icon>
							</button>
						</div>
					</div>
				</div>

				<button
					v-show="store.chatSidebarCollapsed"
					type="button"
					class="ws-expand-fab"
					title="展开侧栏"
					aria-label="展开侧栏"
					@click="store.chatSidebarCollapsed = false"
				>
					<v-icon size="18">mdi-chevron-right</v-icon>
				</button>
			</aside>

			<main class="workspace-main">
				<header class="ws-mobile-bar" aria-label="移动端导航">
					<button
						type="button"
						class="ws-icon-btn"
						title="打开菜单"
						aria-label="打开侧栏"
						@click="store.chatSidebarCollapsed = false"
					>
						<v-icon size="20">mdi-menu</v-icon>
					</button>
					<button type="button" class="ws-mobile-bar__title" @click="goChatHome">
						{{ store.currentAgentName || 'DataAgent' }}
					</button>
					<button
						type="button"
						class="ws-icon-btn"
						title="新会话"
						aria-label="新会话"
						:disabled="!selectedAgentId"
						@click="handleCreateNewSession"
					>
						<v-icon size="20">mdi-plus</v-icon>
					</button>
				</header>
				<slot />
			</main>

			<!-- mobile scrim when rail open -->
			<button
				v-show="!store.chatSidebarCollapsed"
				type="button"
				class="ws-scrim"
				aria-label="关闭侧栏"
				@click="store.chatSidebarCollapsed = true"
			/>
		</div>

		<ConfirmDialog
			v-model="dialogState.isVisible"
			:title="dialogState.title"
			:message="dialogState.message"
			:prepend-icon="dialogState.icon"
			:confirm-text="dialogState.confirmText"
			@confirm="handleGlobalConfirm"
		/>
		<Tip />
	</v-app>
</template>

<script setup lang="ts">
import agentService from '~/services/agent/index';
import { useAuthStore } from '~/stores/auth';
import { useChatStore } from '~/stores/chat';

const { dialogState, handleGlobalConfirm } = useConfirm();
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const store = useChatStore();

const authDisplayName = computed(
	() => authStore.displayName || authStore.username || 'admin',
);

type AgentOption = {
	id: number;
	title: string;
	value: number;
	subtitle: string;
};

const agents = ref<AgentOption[]>([]);
const selectedAgentId = ref<number | undefined>(undefined);

const agentOptions = computed(() => agents.value);

function parseRouteAgentId() {
	const queryId = Number(route.query.agentId);
	if (Number.isFinite(queryId) && queryId > 0) return queryId;
	return store.currentAgentId;
}

function goChatHome() {
	const id = selectedAgentId.value;
	if (id) {
		router.push({ path: '/chat', query: { agentId: String(id) } });
		return;
	}
	router.push('/chat');
}

function goAdmin() {
	const id = selectedAgentId.value;
	if (id) {
		router.push({ path: '/system/agents', query: { agentId: String(id) } });
		return;
	}
	router.push('/system/agents');
}

async function onLogout() {
	await authStore.logout();
	await router.push('/login');
}

function handleAgentSwitch(value: number | string | undefined) {
	const id = Number(value);
	if (!Number.isFinite(id) || id <= 0) return;
	selectedAgentId.value = id;
	// Keep chat route in sync; chat.vue watch reloads sessions
	if (route.path === '/chat') {
		router.replace({ path: '/chat', query: { agentId: String(id) } });
	} else {
		router.push({ path: '/chat', query: { agentId: String(id) } });
	}
}

async function handleCreateNewSession() {
	const id = selectedAgentId.value || store.currentAgentId;
	if (!id) return;
	try {
		if (store.currentAgentId !== id) {
			store.currentAgentId = id;
		}
		await store.createNewSession(id);
	} catch (e) {
		console.error('创建会话失败', e);
	}
}

async function loadAgents() {
	try {
		const list = await agentService.list();
		agents.value = list
			.filter((item) => item.id !== undefined && item.id > 0)
			.map((item) => {
				const raw = item as unknown as Record<string, unknown>;
				return {
					id: item.id as number,
					title: item.name || `Agent ${item.id}`,
					value: item.id as number,
					subtitle: typeof raw.tags === 'string' ? raw.tags : '',
				};
			});
	} catch (e) {
		console.error('Failed to load agents', e);
	}
}

function syncSelectedFromRoute() {
	const routeAgentId = parseRouteAgentId();
	if (routeAgentId && agents.value.some((item) => item.id === routeAgentId)) {
		selectedAgentId.value = routeAgentId;
		return;
	}
	if (!selectedAgentId.value && agents.value[0]) {
		selectedAgentId.value = agents.value[0].id;
	}
}

const isMobile = ref(false);
let mq: MediaQueryList | null = null;

function applyMobileLayout() {
	if (typeof window === 'undefined') return;
	isMobile.value = window.matchMedia('(max-width: 768px)').matches;
	if (isMobile.value) {
		store.chatSidebarCollapsed = true;
	}
}

function onMqChange(e: MediaQueryListEvent) {
	isMobile.value = e.matches;
	if (e.matches) store.chatSidebarCollapsed = true;
}

onMounted(async () => {
	applyMobileLayout();
	if (typeof window !== 'undefined') {
		mq = window.matchMedia('(max-width: 768px)');
		mq.addEventListener?.('change', onMqChange);
	}
	await loadAgents();
	syncSelectedFromRoute();
	// Seed /chat with first agent when missing
	if (
		route.path === '/chat' &&
		!route.query.agentId &&
		selectedAgentId.value
	) {
		router.replace({
			path: '/chat',
			query: { agentId: String(selectedAgentId.value) },
		});
	}
});

onUnmounted(() => {
	mq?.removeEventListener?.('change', onMqChange);
});

watch(
	() => route.fullPath,
	() => {
		syncSelectedFromRoute();
	},
);

watch(
	() => store.currentSession?.id,
	() => {
		if (isMobile.value) store.chatSidebarCollapsed = true;
	},
);

</script>

<style scoped>
.workspace-app {
	background: var(--da-surface-soft);
}

.workspace {
	display: flex;
	width: 100%;
	height: 100vh;
	overflow: hidden;
	background: var(--da-surface-soft);
}

.workspace-rail {
	position: relative;
	width: var(--da-rail-width, 272px);
	min-width: var(--da-rail-width, 272px);
	height: 100%;
	flex-shrink: 0;
	transition:
		width var(--da-dur-base) var(--da-ease-out),
		min-width var(--da-dur-base) var(--da-ease-out);
}

.workspace-rail--collapsed {
	width: 0;
	min-width: 0;
}

.workspace-rail__panel {
	display: flex;
	flex-direction: column;
	width: var(--da-rail-width, 272px);
	height: 100%;
	background: color-mix(in srgb, var(--da-surface-soft) 55%, var(--da-surface));
	border-right: 1px solid var(--da-line-soft);
	overflow: hidden;
	transition: opacity var(--da-dur-base) var(--da-ease-out);
}

.workspace-rail--collapsed .workspace-rail__panel {
	opacity: 0;
	pointer-events: none;
}

.ws-brand {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	padding: 12px 12px 8px 16px;
	min-height: 44px;
	flex-shrink: 0;
}

.ws-brand__title {
	appearance: none;
	border: none;
	background: transparent;
	padding: 0;
	margin: 0;
	font-family: var(--da-font-display);
	font-size: 0.95rem;
	font-weight: 500;
	letter-spacing: -0.02em;
	color: var(--da-ink);
	cursor: pointer;
	text-align: left;
}

.ws-brand__title:hover {
	color: var(--da-primary);
}

.ws-brand__title:focus-visible,
.ws-icon-btn:focus-visible,
.ws-new:focus-visible,
.ws-footer__admin:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.ws-icon-btn {
	appearance: none;
	border: none;
	background: transparent;
	width: 32px;
	height: 32px;
	border-radius: 8px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	color: var(--da-muted);
	cursor: pointer;
	flex-shrink: 0;
}

.ws-icon-btn:hover {
	background: var(--da-sidebar-hover);
	color: var(--da-ink);
}

.ws-icon-btn--danger:hover {
	color: var(--da-danger);
	background: color-mix(in srgb, var(--da-danger) 8%, transparent);
}

.ws-new {
	appearance: none;
	margin: 4px 12px 8px;
	min-height: 34px;
	border-radius: 10px;
	border: 1px solid color-mix(in srgb, var(--da-primary) 28%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
	font: inherit;
	font-size: 13px;
	font-weight: 600;
	letter-spacing: -0.01em;
	cursor: pointer;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	transition: background var(--da-dur-fast) var(--da-ease-out), border-color var(--da-dur-fast) var(--da-ease-out);
}

.ws-new:hover:not(:disabled) {
	background: color-mix(in srgb, var(--da-primary) 88%, #000);
}

.ws-new:disabled {
	opacity: var(--da-disabled-opacity);
	cursor: not-allowed;
	box-shadow: none;
}

.ws-agent {
	padding: 0 10px 10px;
	flex-shrink: 0;
}

.ws-agent__label {
	display: block;
	font-size: 10.5px;
	font-weight: 600;
	letter-spacing: 0.06em;
	text-transform: uppercase;
	color: var(--da-muted);
	margin-bottom: 4px;
}

.ws-agent__select :deep(.v-field) {
	min-height: 34px !important;
	border-radius: 10px !important;
	background: var(--da-surface) !important;
	box-shadow: none !important;
}

.ws-agent__select :deep(.v-field__input) {
	min-height: 34px !important;
	padding-top: 0 !important;
	padding-bottom: 0 !important;
	font-size: 13px !important;
}

.ws-agent__sel {
	font-size: 13px;
	font-weight: 500;
	color: var(--da-ink);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.ws-sessions {
	flex: 1;
	min-height: 0;
	display: flex;
	flex-direction: column;
	overflow: hidden;
}

/* ChatSidebar fills remaining height inside rail */
.ws-sessions :deep(.sidebar-wrapper) {
	width: 100% !important;
	min-width: 0 !important;
	height: 100%;
}

.ws-sessions :deep(.chat-sidebar) {
	width: 100% !important;
	border-right: none;
	background: transparent;
}

.ws-sessions :deep(.expand-fab) {
	display: none !important;
}

.ws-footer {
	flex-shrink: 0;
	padding: 8px 10px 10px;
	border-top: 0.5px solid color-mix(in srgb, var(--da-sidebar-line) 80%, transparent);
	display: flex;
	flex-direction: column;
	gap: 6px;
}

.ws-footer__admin {
	appearance: none;
	width: 100%;
	min-height: 32px;
	border-radius: 8px;
	border: 1px solid color-mix(in srgb, var(--da-line) 50%, transparent);
	background: var(--da-surface);
	color: var(--da-ink);
	font: inherit;
	font-size: 12.5px;
	font-weight: 600;
	cursor: pointer;
	text-align: center;
	transition: background var(--da-dur-fast) var(--da-ease-out);
}

.ws-footer__admin:hover {
	background: var(--da-primary-soft);
}

.ws-footer__user {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	min-height: 32px;
	padding: 0 2px;
}

.ws-footer__name {
	font-size: 12px;
	font-weight: 500;
	color: var(--da-ink);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.ws-expand-fab {
	position: absolute;
	top: 12px;
	left: 10px;
	z-index: 10;
	width: 32px;
	height: 32px;
	border-radius: 10px;
	border: 0.5px solid color-mix(in srgb, var(--da-line) 50%, transparent);
	background: color-mix(in srgb, var(--da-surface) 92%, transparent);
	backdrop-filter: blur(8px);
	box-shadow: var(--da-shadow-sm);
	color: var(--da-muted);
	display: inline-flex;
	align-items: center;
	justify-content: center;
	cursor: pointer;
}

.ws-expand-fab:hover {
	color: var(--da-ink);
	background: var(--da-surface);
	box-shadow: var(--da-shadow-md);
}

.workspace-main {
	flex: 1;
	min-width: 0;
	height: 100%;
	overflow: hidden;
	display: flex;
	flex-direction: column;
}

:deep(.ws-agent-menu) {
	background: var(--da-surface) !important;
	border: 1px solid var(--da-line-soft) !important;
	border-radius: var(--da-radius-md) !important;
	box-shadow: var(--da-shadow-md) !important;
	overflow: hidden;
}

@media (prefers-reduced-motion: reduce) {
	.workspace-rail,
	.workspace-rail__panel {
		transition: none !important;
	}
}

/* Mobile: rail as overlay drawer */
.ws-mobile-bar {
	display: none;
}
.ws-scrim {
	display: none;
}

@media (max-width: 768px) {
	.workspace-rail {
		position: fixed;
		inset: 0 auto 0 0;
		z-index: 40;
		width: min(86vw, 300px);
		min-width: 0;
		height: 100%;
		max-height: 100dvh;
		transition: transform var(--da-dur-base) var(--da-ease-out);
		transform: translateX(0);
	}
	.workspace-rail--collapsed {
		width: min(86vw, 300px);
		min-width: 0;
		transform: translateX(-105%);
		pointer-events: none;
	}
	.workspace-rail__panel {
		width: 100%;
		height: 100%;
		max-height: 100dvh;
		padding-top: var(--da-safe-top, 0px);
		padding-bottom: var(--da-safe-bottom, 0px);
		box-shadow: var(--da-shadow-lg);
		background: var(--da-surface);
		opacity: 1 !important;
		pointer-events: auto;
	}
	.workspace-rail--collapsed .workspace-rail__panel {
		opacity: 1 !important;
	}
	.ws-expand-fab {
		display: none !important;
	}
	.workspace-main {
		width: 100%;
		min-width: 0;
		min-height: 100dvh;
		height: 100dvh;
		display: flex;
		flex-direction: column;
	}
	.ws-mobile-bar {
		display: flex;
		align-items: center;
		justify-content: space-between;
		gap: 8px;
		flex-shrink: 0;
		height: calc(48px + var(--da-safe-top, 0px));
		padding: var(--da-safe-top, 0px) 8px 0;
		background: color-mix(in srgb, var(--da-surface) 92%, transparent);
		border-bottom: 0.5px solid var(--da-line-soft);
		backdrop-filter: blur(10px);
		z-index: 5;
	}
	.ws-mobile-bar__title {
		appearance: none;
		border: none;
		background: transparent;
		flex: 1;
		min-width: 0;
		font-family: var(--da-font-display);
		font-size: 0.95rem;
		font-weight: 500;
		letter-spacing: -0.02em;
		color: var(--da-ink);
		text-align: center;
		overflow: hidden;
		text-overflow: ellipsis;
		white-space: nowrap;
		cursor: pointer;
		padding: 0 4px;
	}
	.ws-scrim {
		display: block;
		position: fixed;
		inset: 0;
		z-index: 35;
		border: none;
		padding: 0;
		margin: 0;
		background: var(--da-overlay, rgba(15, 35, 55, 0.4));
		cursor: pointer;
	}
}

</style>
