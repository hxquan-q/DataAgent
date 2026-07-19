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
	<v-app id="app" class="admin-app">
		<v-main>
			<BaseDrawer v-model="drawer" :drawer-width="280">
				<template #drawer>
					<div class="d-flex flex-column h-100">
						<div class="pa-3 border-b border-white-5 brand-block">
							<div class="d-flex align-center justify-space-between mb-2 brand-row">
								<div class="text-subtitle-2 font-weight-medium brand-title">管理后台</div>
							</div>

							<div class="agent-switcher-box">
								<p
									class="text-caption mb-2 font-weight-medium agent-switcher-label"
								>
									当前智能体
								</p>
								<v-select
									v-model="selectedAgentId"
									:items="agentOptions"
									item-title="title"
									item-value="value"
									variant="outlined"
									density="compact"
									hide-details
									placeholder="请选择智能体"
									class="agent-switcher"
									menu-icon="mdi-chevron-down"
									:menu-props="{
										contentClass: 'agent-switcher-menu',
										offset: [0, 8],
									}"
									:list-props="{ bgColor: 'var(--da-surface)', elevation: 2 }"
									item-color="primary"
									@update:model-value="handleAgentSwitch"
								>
									<template #selection="{ item }">
										<div
											class="agent-option agent-option--selection d-flex align-center w-100"
										>
											<v-avatar size="24" class="mr-2 border">
												<v-img
													v-if="item.raw.avatar"
													:src="item.raw.avatar"
													cover
												/>
												<v-icon
													v-else
													icon="mdi-robot"
													size="14"
													color="primary"
												/>
											</v-avatar>
											<div class="agent-option__text">
												<div
													class="agent-option__title agent-option__title--active"
												>
													{{ item.raw.title }}
												</div>
												<div class="agent-option__subtitle">
													{{ item.raw.subtitle }}
												</div>
											</div>
										</div>
									</template>
									<template #item="{ props, item }">
										<v-list-item
											v-bind="props"
											:title="undefined"
											:subtitle="undefined"
											class="agent-option"
											:class="{
												'agent-option--active':
													item.raw.value === selectedAgentId,
											}"
										>
											<template #prepend>
												<v-avatar size="28" class="mr-2 border">
													<v-img
														v-if="item.raw.avatar"
														:src="item.raw.avatar"
														cover
													/>
													<v-icon
														v-else
														icon="mdi-robot"
														size="15"
														color="primary"
													/>
												</v-avatar>
											</template>
											<v-list-item-title class="agent-option__title">{{
												item.raw.title
											}}</v-list-item-title>
											<v-list-item-subtitle class="agent-option__subtitle">
												<span class="agent-tags-text">{{
													item.raw.subtitle
												}}</span>
											</v-list-item-subtitle>
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
						</div>

						<v-list
							v-model:opened="openedGroups"
							density="compact"
							nav
							class="flex-grow-1 pa-1 px-2 custom-scrollbar bg-transparent"
													>
							<v-list-item
								prepend-icon="mdi-chat-processing-outline"
								title="数据问答"
								:active="isActive('/chat')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								@click="navigateToPath('/chat')"
							/>
							<!-- <v-list-item
								prepend-icon="mdi-chart-box-outline"
								:active="isActive('/dashboard')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								title="数据看板"
								@click="navigateToPath('/dashboard')"
							/> -->
							<v-list-item
								prepend-icon="mdi-auto-fix"
								:active="isActive('/prompt-config')"
								class="rounded-lg mb-1 navigation-item"
								color="primary"
								title="提示词配置"
								@click="navigateToPath('/prompt-config')"
							/>

							<v-list-group value="knowledge">
								<template #activator="{ props }">
									<v-list-item
										v-bind="props"
										title="知识库管理"
										class="text-overline nav-group-label mt-4"
									/>
								</template>
								<v-list-item
									prepend-icon="mdi-book-open-variant"
									title="业务知识配置"
									:active="isActive('/knowledge/business')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/business')"
								/>
								<v-list-item
									prepend-icon="mdi-brain"
									title="智能体知识库"
									:active="isActive('/knowledge/agents')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/agents')"
								/>
								<v-list-item
									prepend-icon="mdi-vector-intersection"
									title="语义模型配置"
									:active="isActive('/knowledge/semantic-models')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/knowledge/semantic-models')"
								/>
							</v-list-group>

							<v-list-group value="system">
								<template #activator="{ props }">
									<v-list-item
										v-bind="props"
										title="通用设置"
										class="text-overline nav-group-label mt-2"
									/>
								</template>
								<v-list-item
									prepend-icon="mdi-robot-outline"
									title="智能体管理"
									:active="isActive('/system/agents')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/agents')"
								/>
								<v-list-item
									prepend-icon="mdi-database-refresh-outline"
									title="数据连接"
									:active="isActive('/system/data-sources')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/data-sources')"
								/>
								<v-list-item
									prepend-icon="mdi-cpu-64-bit"
									title="模型配置"
									:active="isActive('/system/model-config')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/model-config')"
								/>
								<v-list-item
									prepend-icon="mdi-chart-line"
									title="指标配置"
									:active="isActive('/system/metrics')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/metrics')"
								/>
								<v-list-item
									prepend-icon="mdi-format-list-bulleted-type"
									title="口径版本"
									:active="isActive('/system/metric-versions')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/metric-versions')"
								/>
								<v-list-item
									prepend-icon="mdi-tag-multiple"
									title="语义别名"
									:active="isActive('/system/semantic-aliases')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/semantic-aliases')"
								/>
								<v-list-item
									prepend-icon="mdi-shield-search"
									title="查询证据链"
									:active="isActive('/system/query-log')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/query-log')"
								/>
								<v-list-item
									prepend-icon="mdi-chart-bar"
									title="评测游乐场"
									:active="isActive('/system/eval-playground')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/eval-playground')"
								/>
								<v-list-item
									prepend-icon="mdi-shield-lock"
									title="安全状态"
									:active="isActive('/system/security')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/security')"
								/>
									<v-list-item
										prepend-icon="mdi-web-box"
									title="网页嵌入"
									:active="isActive('/system/embed')"
									density="compact"
									class="rounded-lg mb-1 navigation-sub-item"
									color="primary"
									@click="navigateToPath('/system/embed')"
								/>
							</v-list-group>

							<div class="mt-6 pt-4 sidebar-footer-sep">
								<v-list-item
									color="primary"
									density="compact"
									:active="isActive('/agent/new')"
									variant="flat"
									class="rounded-xl mx-2 new-agent-item"
									@click="navigateToPath('/agent/new')"
								>
									<div class="d-flex align-center justify-center gap-2 w-100">
										<v-icon icon="mdi-plus-box-outline" size="16" rounded />
										<span class="font-weight-medium text-caption mx-1"
											>新建智能体</span
										>
									</div>
								</v-list-item>
							</div>
						</v-list>

						<div class="pa-2 sidebar-footer-sep">
							<v-list-item
								class="rounded-lg navigation-item logout-item"
								color="error"
								@click="openChangePassword"
							>
								<template #prepend>
									<v-avatar size="24" color="primary" variant="tonal">
										<v-icon icon="mdi-account" size="14" color="primary" />
									</v-avatar>
								</template>
								<v-list-item-title class="text-caption font-weight-medium ms-2">
									{{ authDisplayName }}
								</v-list-item-title>
								<template #append>
									<v-btn
										icon
										variant="text"
										size="small"
										color="red"
										:title="'退出登录'"
										@click.stop="onLogout"
									>
										<v-icon icon="mdi-logout" size="20" />
									</v-btn>
								</template>
							</v-list-item>
						</div>

						<v-dialog v-model="pwdDialog" max-width="380">
							<v-card rounded="lg" class="pa-5 pwd-card" elevation="0">
								<div class="dialog-title mb-1">修改密码</div>
								<p class="pwd-hint mb-4">使用当前账号修改登录密码</p>
								<v-alert
									v-if="pwdError"
									type="error"
									variant="tonal"
									density="compact"
									class="mb-3"
									:text="pwdError"
								/>
								<v-text-field
									v-model="oldPassword"
									label="原密码"
									type="password"
									density="comfortable"
									variant="outlined"
									hide-details="auto"
									class="mb-3"
								/>
								<v-text-field
									v-model="newPassword"
									label="新密码（≥8位）"
									type="password"
									density="comfortable"
									variant="outlined"
									hide-details="auto"
									class="mb-5"
								/>
								<div class="d-flex justify-end ga-2">
									<v-btn variant="text" class="text-none pwd-btn" @click="pwdDialog = false">取消</v-btn>
									<v-btn color="primary" class="text-none px-5 pwd-btn" :loading="pwdLoading" @click="submitChangePassword">
										确认
									</v-btn>
								</div>
							</v-card>
						</v-dialog>
					</div>
				</template>

				<template #header="{ toggle, isOpen }">
					<v-btn icon variant="text" size="small" class="mr-2 header-menu-btn" @click="toggle">
						<v-icon :icon="isOpen ? 'mdi-menu-open' : 'mdi-menu'" />
					</v-btn>
					<div class="text-subtitle-1 font-weight-medium app-header-title">
						{{ currentRouteTitle }}
					</div>
					<v-spacer />
					<button type="button" class="header-back-chat" @click="goChatWorkspace">
						返回问答
					</button>
				</template>

				<slot />
			</BaseDrawer>
		</v-main>

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
import BaseDrawer from '../components/BaseDrawer/index.vue';
import agentService from '~/services/agent/index';
import modelConfigService from '~/services/modelConfig/index';
import { useAuthStore } from '~/stores/auth';

const { dialogState, handleGlobalConfirm } = useConfirm();
const drawer = ref(true);
const router = useRouter();
const route = useRoute();
const authStore = useAuthStore();
const authDisplayName = computed(
	() => authStore.displayName || authStore.username || 'admin',
);
const pwdDialog = ref(false);
const oldPassword = ref('');
const newPassword = ref('');
const pwdLoading = ref(false);
const pwdError = ref('');
// 默认都展开
const openedGroups = ref(['knowledge', 'system']);

function openChangePassword() {
	pwdError.value = '';
	oldPassword.value = '';
	newPassword.value = '';
	pwdDialog.value = true;
}

async function submitChangePassword() {
	pwdLoading.value = true;
	pwdError.value = '';
	try {
		await authStore.changePassword(oldPassword.value, newPassword.value);
		pwdDialog.value = false;
		await router.push('/login');
	}
	catch (e: any) {
		pwdError.value = e?.response?.data?.message || e?.message || '改密失败';
	}
	finally {
		pwdLoading.value = false;
	}
}

async function onLogout() {
	await authStore.logout();
	await router.push('/login');
}

type DrawerAgentOption = {
	id: number;
	name: string;
	title: string;
	value: number;
	subtitle: string;
	avatar?: string;
	tags?: string;
};

const agents = ref<DrawerAgentOption[]>([]);
const selectedAgentId = ref<number | undefined>(undefined);
const globalChatModelName = ref('');

const routeTitleMap: Record<string, string> = {
	'/chat': '数据问答',
	'/dashboard': '数据看板',
	'/prompt-config': '提示词配置',
	'/knowledge/business': '业务知识配置',
	'/knowledge/agents': '智能体知识库',
	'/knowledge/semantic-models': '语义模型配置',
	'/system/data-sources': '数据连接',
	'/system/model-config': '模型配置',
	'/system/metrics': '指标配置',
	'/system/metric-versions': '口径版本配置',
	'/system/semantic-aliases': '语义别名配置',
	'/system/query-log': '查询证据链',
	'/system/eval-playground': '评测游乐场',
	'/system/security': '安全状态',
	'/system/embed': '网页嵌入',
	'/system/settings': '通用设置',
	'/agent/new': '新建智能体',
};

const agentOptions = computed(() => agents.value);

const currentRouteTitle = computed(() => {
	if (route.path.startsWith('/agent/') && route.path !== '/agent/new') {
		return '智能体详情';
	}
	return routeTitleMap[route.path] || 'Data Agent';
});

function parseRouteAgentId() {
	const pathId = Number(route.params.id);
	if (
		route.path.startsWith('/agent/') &&
		route.path !== '/agent/new' &&
		Number.isFinite(pathId) &&
		pathId > 0
	) {
		return pathId;
	}
	const queryId = Number(route.query.agentId);
	if (Number.isFinite(queryId) && queryId > 0) {
		return queryId;
	}
	return undefined;
}

function getQueryWithAgentId(agentId?: number) {
	const query: Record<string, string> = {};
	Object.keys(route.query).forEach((key) => {
		const value = route.query[key];
		if (key === 'agentId') return;
		if (Array.isArray(value)) {
			if (value[0]) query[key] = String(value[0]);
		} else if (value !== undefined) {
			query[key] = String(value);
		}
	});
	if (agentId) query.agentId = String(agentId);
	return query;
}

function applyAgentToCurrentRoute(agentId: number, replace = false) {
	if (route.path === '/agent/new') return;
	const target =
		route.path.startsWith('/agent/') && route.path !== '/agent/new'
			? { path: `/agent/${agentId}` }
			: { path: route.path, query: getQueryWithAgentId(agentId) };
	if (replace) {
		router.replace(target);
	} else {
		router.push(target);
	}
}

function goChatWorkspace() {
	const id = selectedAgentId.value;
	if (id) {
		router.push({ path: '/chat', query: { agentId: String(id) } });
		return;
	}
	router.push('/chat');
}

function navigateToPath(path: string) {
	if (path === '/agent/new') {
		if (route.path !== path) router.push({ path });
		return;
	}
	if (
		route.path === path &&
		path.startsWith('/agent/') &&
		selectedAgentId.value
	) {
		return;
	}
	if (selectedAgentId.value) {
		if (path.startsWith('/agent/') && path !== '/agent/new') {
			router.push({ path: `/agent/${selectedAgentId.value}` });
			return;
		}
		router.push({ path, query: { agentId: String(selectedAgentId.value) } });
		return;
	}
	router.push({ path });
}

function handleAgentSwitch(value: number | string | undefined) {
	const id = Number(value);
	if (!Number.isFinite(id) || id <= 0) return;
	selectedAgentId.value = id;
	applyAgentToCurrentRoute(id);
}

const isActive = (path: string) => route.path === path;

async function loadGlobalModelName() {
	try {
		const configs = await modelConfigService.list();
		const activeChat = configs.find(
			(item) => item.modelType === 'CHAT' && item.isActive,
		);
		globalChatModelName.value = activeChat?.modelName || '';
	} catch (e) {
		console.error('Failed to load global model name', e);
	}
}

async function loadAgents() {
	const list = await agentService.list();
	agents.value = list
		.filter((item) => item.id !== undefined && item.id > 0)
		.map((item) => {
			const raw = item as unknown as Record<string, unknown>;
			return {
				id: item.id as number,
				name: item.name || `Agent ${item.id}`,
				title: item.name || `Agent ${item.id}`,
				value: item.id as number,
				subtitle: typeof raw.tags === 'string' ? raw.tags : '',
				avatar: typeof raw.avatar === 'string' ? raw.avatar : undefined,
				tags: typeof raw.tags === 'string' ? raw.tags : '',
			};
		});
}

function syncSelectedFromRoute() {
	const routeAgentId = parseRouteAgentId();
	if (routeAgentId && agents.value.some((item) => item.id === routeAgentId)) {
		selectedAgentId.value = routeAgentId;
	}
}

onMounted(async () => {
	await loadGlobalModelName();
	await loadAgents();
	syncSelectedFromRoute();
	const firstAgent = agents.value[0];
	if (!selectedAgentId.value && firstAgent?.id) {
		selectedAgentId.value = firstAgent.id;
		applyAgentToCurrentRoute(selectedAgentId.value, true);
	}
});

watch(
	() => route.fullPath,
	() => {
		syncSelectedFromRoute();
	},
);
</script>

<style scoped>
.admin-app {
	background: var(--da-surface-soft) !important;
}

.border-white-5 {
	border-color: var(--da-sidebar-line, #e4edf5) !important;
}

.agent-switcher-label {
	color: var(--da-sidebar-muted, #64748b);
}

.nav-group-label {
	color: var(--da-sidebar-muted, #64748b) !important;
	letter-spacing: 0.08em;
	opacity: 0.95;
}

.brand-subtitle {
	font-size: 10px;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-sidebar-muted, #64748b);
}

.brand-block {
	padding-bottom: 10px !important;
}
.brand-title {
	color: var(--da-sidebar-ink, #1a2332);
	font-family: var(--da-font-display);
	letter-spacing: -0.02em;
	font-size: 0.95rem;
}

.app-header-title {
	color: var(--da-ink, #1a2332);
	letter-spacing: -0.02em;
	font-size: 0.95rem !important;
}

.header-menu-btn {
	color: var(--da-muted, #64748b) !important;
}

.app-header-meta {
	font-size: 11.5px;
	font-weight: 500;
	color: var(--da-muted);
	letter-spacing: 0.02em;
	user-select: none;
}

/* rebuild-ui: light paper sidebar brand */
:deep(.base-drawer__left .text-white) {
	color: var(--da-sidebar-ink, #1a2332) !important;
}

.agent-switcher :deep(.v-field) {
	background: var(--da-sidebar-field, #ffffff);
	border-radius: var(--da-radius-md, 12px);
	border: 1px solid var(--da-sidebar-line, #e4edf5);
	box-shadow: var(--da-shadow-sm);
}

.agent-switcher :deep(.v-field__input),
.agent-switcher :deep(.v-field-label),
.agent-switcher :deep(.v-icon) {
	color: var(--da-sidebar-ink, #1a2332);
}

:deep(.agent-switcher-menu) {
	background: var(--da-surface, #ffffff) !important;
	border: 1px solid var(--da-line-soft, #e4edf5) !important;
	border-radius: var(--da-radius-md, 12px) !important;
	box-shadow: var(--da-shadow-md) !important;
	overflow: hidden;
	z-index: var(--da-z-dropdown, 1000);
}

:deep(.agent-switcher-menu .v-list) {
	background: transparent !important;
	padding: 4px !important;
}

:deep(.agent-switcher-menu .v-list-item) {
	border-radius: var(--da-radius-sm, 8px) !important;
	margin-bottom: 2px !important;
	min-height: 44px !important;
	color: var(--da-ink, #1a2332) !important;
}

:deep(.agent-switcher-menu .v-list-item:hover) {
	background: var(--da-sidebar-hover, rgba(47, 132, 214, 0.08)) !important;
}

.agent-option__text {
	min-width: 0;
	flex: 1;
}

.agent-option__title {
	font-size: 13px;
	line-height: 1.2;
	font-weight: 600;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 170px;
}

.agent-option__title--active {
	color: var(--da-sidebar-accent, #2f84d6);
}

.agent-option__subtitle {
	font-size: 10px;
	line-height: 1.2;
	color: var(--da-sidebar-muted, #64748b);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 170px;
	margin-top: 2px;
}

.agent-tags-text {
	background: var(--da-sidebar-chip-bg, rgba(47, 132, 214, 0.1));
	color: var(--da-sidebar-chip-ink, #1e5fa8);
	padding: 1px 6px;
	border-radius: var(--da-radius-sm);
	font-size: 9px;
	border: 1px solid var(--da-sidebar-chip-line, rgba(47, 132, 214, 0.18));
}

.agent-option--selection .agent-option__title {
	max-width: 145px;
}

.agent-option--selection .agent-option__subtitle {
	max-width: 145px;
}

.navigation-item {
	--v-list-item-padding-start: 10px;
	--v-list-item-min-height: 32px;
	border-radius: 8px !important;
	color: var(--da-sidebar-ink, #1a2332) !important;
	margin-inline: 2px;
	margin-bottom: 1px !important;
	font-size: 13px !important;
	font-weight: 400 !important;
}

.navigation-item :deep(.v-list-item-title) {
	font-size: 13px !important;
	font-weight: 400 !important;
	letter-spacing: -0.01em;
}

.navigation-item:hover {
	background: var(--da-sidebar-hover, rgba(47, 132, 214, 0.08)) !important;
}

.navigation-item.v-list-item--active {
	background: var(--da-sidebar-active, rgba(47, 132, 214, 0.12)) !important;
	color: var(--da-sidebar-accent, #2f84d6) !important;
	box-shadow: none;
	font-weight: 500 !important;
}

.navigation-item.v-list-item--active :deep(.v-list-item-title) {
	font-weight: 500 !important;
}

.navigation-sub-item {
	--v-list-item-padding-start: 22px;
	--v-list-item-min-height: 32px;
	font-size: 13px !important;
}

.custom-scrollbar::-webkit-scrollbar {
	width: 4px;
}

.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}

.custom-scrollbar::-webkit-scrollbar-thumb {
	background: color-mix(in srgb, var(--da-muted, #64748b) 35%, transparent);
	border-radius: var(--da-radius-sm);
}

.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: color-mix(in srgb, var(--da-muted, #64748b) 55%, transparent);
}

.sidebar-footer-sep {
	border-top: 1px solid var(--da-sidebar-line, #e4edf5);
}

.logout-item :deep(.v-list-item-title) {
	color: var(--da-sidebar-ink, #1a2332);
}

.new-agent-item {
	min-height: 32px !important;
	background: transparent !important;
	color: var(--da-primary, #2f84d6) !important;
	border: 0.5px solid color-mix(in srgb, var(--da-primary) 22%, transparent) !important;
	box-shadow: none !important;
	border-radius: 8px !important;
	transition: background var(--da-dur-fast) var(--da-ease-out),
		border-color var(--da-dur-fast) var(--da-ease-out);
}
.new-agent-item:hover {
	background: var(--da-primary-soft, #e8f3fc) !important;
	box-shadow: none !important;
}

:deep(.v-list-group__items .v-list-item) {
	padding-inline-start: 16px !important;
}

:deep(.flex-grow-1.v-list .v-list-item) {
	min-height: 32px !important;
}

:deep(.v-list-item__spacer) {
	width: 10px !important;
}

/* Quieter group labels */
.nav-group-label {
	font-size: 10.5px !important;
	letter-spacing: 0.08em !important;
	min-height: 28px !important;
	margin-top: 10px !important;
	opacity: 0.85;
}

.agent-switcher-box {
	padding: 0;
}

.agent-switcher :deep(.v-field) {
	min-height: 36px !important;
	box-shadow: none !important;
	border-radius: 10px !important;
}

.agent-switcher :deep(.v-field__input) {
	min-height: 36px !important;
	padding-top: 0 !important;
	padding-bottom: 0 !important;
	font-size: 13px !important;
}

.agent-option__title {
	font-weight: 400 !important;
	font-size: 13px !important;
}

.agent-option__title--active {
	font-weight: 500 !important;
}

.sidebar-footer-sep {
	border-top: 0.5px solid color-mix(in srgb, var(--da-sidebar-line) 80%, transparent);
}

.pwd-card {
	border: 0.5px solid color-mix(in srgb, var(--da-line) 50%, transparent) !important;
	box-shadow: var(--da-shadow-md) !important;
}
.pwd-hint {
	margin: 0;
	font-size: 12.5px;
	color: var(--da-muted);
}
.pwd-btn {
	border-radius: 10px !important;
	min-height: 36px !important;
	letter-spacing: 0 !important;
}

.header-back-chat {
	appearance: none;
	border: 1px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	background: var(--da-surface);
	color: var(--da-ink);
	border-radius: 999px;
	min-height: 32px;
	padding: 4px 14px;
	font: inherit;
	font-size: 12.5px;
	font-weight: 600;
	letter-spacing: -0.01em;
	cursor: pointer;
	box-shadow: var(--da-shadow-sm);
	transition: border-color var(--da-dur-fast) var(--da-ease-out), background var(--da-dur-fast) var(--da-ease-out);
}
.header-back-chat:hover {
	border-color: color-mix(in srgb, var(--da-primary) 40%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
}
.header-back-chat:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}
.brand-row {
	min-height: 28px;
}
</style>
