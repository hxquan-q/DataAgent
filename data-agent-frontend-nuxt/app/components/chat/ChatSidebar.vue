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
		class="sidebar-wrapper"
		:class="{ collapsed: store.chatSidebarCollapsed }"
	>
		<!-- Expanded panel -->
		<div class="chat-sidebar">
			<!-- Header -->
			<div class="sidebar-header">
				<div class="sidebar-header__text">
					<span class="sidebar-title">历史会话</span>
					<span v-if="store.currentAgentName" class="sidebar-agent">{{ store.currentAgentName }}</span>
				</div>
				<v-btn
					icon
					variant="text"
					density="compact"
					size="small"
					class="toggle-btn"
					title="折叠侧边栏"
					@click="store.chatSidebarCollapsed = true"
				>
					<v-icon size="18">mdi-chevron-left</v-icon>
				</v-btn>
			</div>

			<!-- Session List -->
			<div class="session-list custom-scrollbar">
				<div class="session-group-label">最近任务</div>

				<div
					v-for="session in store.sessions"
					:key="session.id"
					class="session-item"
					:class="{ active: store.currentSession?.id === session.id }"
					@click="handleSelectSession(session)"
				>
					<template v-if="session.editing">
						<input
							v-model="session.editingTitle"
							class="session-rename-input"
							@click.stop
							@blur="handleSaveTitle(session)"
							@keyup.enter="handleSaveTitle(session)"
							@keyup.esc="cancelEdit(session)"
						/>
					</template>
					<template v-else>
						<div class="session-item-info" @dblclick.stop="startEdit(session)">
							<span class="session-item-title">{{
								session.title || '新会话'
							}}</span>
							<span class="session-item-time">{{
								formatTime(session.createTime || session.updateTime) || '—'
							}}</span>
						</div>
						<div class="session-item-actions">
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--edit"
								title="重命名"
								@click.stop="startEdit(session)"
							>
								<v-icon size="14">mdi-pencil-outline</v-icon>
							</v-btn>
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--star"
								title="收藏"
								@click.stop="handlePin(session)"
							>
								<v-icon size="14" :color="session.isPinned ? 'warning' : undefined">
									{{ session.isPinned ? 'mdi-star' : 'mdi-star-outline' }}
								</v-icon>
							</v-btn>
							<v-btn
								icon
								variant="text"
								density="compact"
								size="x-small"
								class="action-btn--danger"
								title="删除"
								@click.stop="handleDelete(session)"
							>
								<v-icon size="14">mdi-delete-outline</v-icon>
							</v-btn>
						</div>
					</template>
				</div>

				<div v-if="store.sessions.length === 0" class="empty-sessions empty-sessions--hint">
					<p class="empty-sessions__title">暂无历史会话</p>
					<p class="empty-sessions__desc">在下方输入问题开始，或点击「新会话」。</p>
				</div>
			</div>

			<!-- Bottom: New Session + config links (R160) -->
			<div class="sidebar-bottom">
				<v-btn
					block
					variant="outlined"
					color="primary"
					prepend-icon="mdi-plus-circle-outline"
					class="new-session-btn"
					title="创建新的分析会话"
					@click="handleCreateNewSession"
				>
					新建分析会话
				</v-btn>
				<nav class="sidebar-config-links" aria-label="配置快捷入口">
					<button type="button" class="sidebar-config-link" @click="goModels">
						模型
					</button>
					<span class="sidebar-config-sep" aria-hidden="true">·</span>
					<button type="button" class="sidebar-config-link" @click="goDatasource">
						数据源
					</button>
					<span class="sidebar-config-sep" aria-hidden="true">·</span>
					<button type="button" class="sidebar-config-link" @click="goAgents">
						智能体
					</button>
				</nav>
			</div>
		</div>

		<!-- Collapsed FAB: floats at top-left of chat area -->
		<v-btn
			v-show="store.chatSidebarCollapsed"
			icon
			variant="tonal"
			color="primary"
			size="small"
			class="expand-fab"
			title="展开历史会话"
			@click="store.chatSidebarCollapsed = false"
		>
			<v-icon size="18">mdi-chevron-right</v-icon>
		</v-btn>
	</div>

	<!-- Confirm Dialog -->
	<v-dialog v-model="showDeleteConfirm" max-width="360">
		<v-card rounded="xl">
			<v-card-title class="text-subtitle-1 font-weight-medium pa-5 pb-2 dialog-title"
				>删除会话</v-card-title
			>
			<v-card-text class="px-5 text-body-2 text-medium-emphasis"
				>确定要删除这个会话吗？</v-card-text
			>
			<v-card-actions class="px-5 pb-4 gap-2">
				<v-spacer />
				<v-btn
					variant="text"
					size="small"
					class="text-none"
					@click="showDeleteConfirm = false"
					>取消</v-btn
				>
				<v-btn
					color="error"
					variant="flat"
					size="small"
					class="text-none"
					@click="confirmDelete"
					>确定</v-btn
				>
			</v-card-actions>
		</v-card>
	</v-dialog>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useChatStore, type ExtendedChatSession } from '~/stores/chat';
import type { ChatSession } from '~/services/chat/index';

const store = useChatStore();

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
function goAgents() {
	navigateTo('/system/agents');
}

const showDeleteConfirm = ref(false);
let sessionToDelete: ChatSession | null = null;

function formatTime(time: Date | string | undefined): string {
	if (!time) return '';
	const d = typeof time === 'string' ? new Date(time) : time;
	if (isNaN(d.getTime())) return '';
	const now = new Date();
	const isToday = d.toDateString() === now.toDateString();
	const pad = (n: number) => String(n).padStart(2, '0');
	if (isToday) return `今天 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	const isThisYear = d.getFullYear() === now.getFullYear();
	if (isThisYear)
		return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
	return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

async function handleCreateNewSession() {
	if (!store.currentAgentId) return;
	try {
		await store.createNewSession(store.currentAgentId);
	} catch (e) {
		console.error('创建会话失败', e);
	}
}

async function handleSelectSession(session: ChatSession) {
	if (store.currentSession?.id === session.id) return;
	try {
		await store.selectSession(session);
	} catch (e) {
		console.error('切换会话失败', e);
	}
}

function startEdit(session: ExtendedChatSession) {
	session.editing = true;
	session.editingTitle = session.title || '新会话';
}

function cancelEdit(session: ExtendedChatSession) {
	session.editing = false;
}

async function handleSaveTitle(session: ExtendedChatSession) {
	const newTitle = (session.editingTitle || '').trim();
	if (!newTitle) {
		session.editing = false;
		return;
	}
	if (newTitle === session.title) {
		session.editing = false;
		return;
	}
	try {
		await store.renameSession(session, newTitle);
	} catch (e) {
		console.error('重命名失败', e);
		session.editing = false;
	}
}

async function handlePin(session: ChatSession) {
	try {
		await store.pinSession(session);
	} catch (e) {
		console.error('置顶操作失败', e);
	}
}

function handleDelete(session: ChatSession) {
	sessionToDelete = session;
	showDeleteConfirm.value = true;
}

async function confirmDelete() {
	if (!sessionToDelete) return;
	showDeleteConfirm.value = false;
	try {
		await store.removeSession(sessionToDelete);
	} catch (e) {
		console.error('删除会话失败', e);
	}
	sessionToDelete = null;
}
</script>

<style scoped>
/* ── Wrapper: drives the width transition ────────────────────────────────────── */
.sidebar-wrapper {
	position: relative;
	width: 240px;
	min-width: 240px;
	transition:
		width 0.25s ease,
		min-width 0.25s ease;
	overflow: visible;
	height: 100%;
	flex-shrink: 0;
}

.sidebar-wrapper.collapsed {
	width: 0;
	min-width: 0;
}

/* ── Expanded panel ──────────────────────────────────────────────────────────── */
.chat-sidebar {
	width: 240px;
	background: var(--da-surface, #ffffff);
	border-right: 1px solid var(--da-line-soft, #e4edf5);
	display: flex;
	flex-direction: column;
	height: 100%;
	overflow: hidden;
	transition: opacity var(--da-dur-base, 0.2s) var(--da-ease-out);
	box-shadow: var(--da-shadow-sm);
}

.collapsed .chat-sidebar {
	opacity: 0;
	pointer-events: none;
}

/* ── Header ─────────────────────────────────────────────────────────────────── */
.sidebar-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 8px 8px 8px 14px;
	border-bottom: 1px solid var(--da-line-soft);
	min-height: 48px;
	flex-shrink: 0;
}

.sidebar-title {
	font-size: 13px;
	font-weight: 600;
	color: var(--da-muted);
	letter-spacing: 0.3px;
	white-space: nowrap;
}

.toggle-btn {
	color: var(--da-muted) !important;
}
.toggle-btn:hover {
	color: var(--da-primary, #2f84d6) !important;
}

/* ── Session list ────────────────────────────────────────────────────────────── */
.session-list {
	flex: 1;
	overflow-y: auto;
	padding: 0 6px;
}

.session-group-label {
	font-size: 10.5px;
	font-weight: 600;
	color: var(--da-muted);
	letter-spacing: 0.5px;
	text-transform: uppercase;
	padding: 6px 8px 4px;
}

.session-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 6px 10px;
	border-radius: var(--da-radius-sm, 8px);
	cursor: pointer;
	transition: background var(--da-dur-fast, 0.12s);
	margin-bottom: 2px;
	min-height: 40px;
}
.session-item:hover {
	background: var(--da-sidebar-hover, rgba(47, 132, 214, 0.08));
}
.session-item.active {
	background: var(--da-sidebar-active, rgba(47, 132, 214, 0.14));
	box-shadow: inset 0 0 0 1px var(--da-sidebar-chip-line, rgba(47, 132, 214, 0.18));
}

.session-item-info {
	display: flex;
	flex-direction: column;
	flex: 1;
	min-width: 0;
	gap: 1px;
}

.session-item-title {
	font-size: 12px;
	color: var(--da-ink);
	line-height: 1.35;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.session-item-time {
	font-size: 10.5px;
	color: var(--da-muted);
	font-style: italic;
	line-height: 1.2;
}

.session-item.active .session-item-title {
	color: var(--da-primary);
	font-weight: 500;
}

/* ── Actions ─────────────────────────────────────────────────────────────────── */
.session-item-actions {
	display: none;
	flex-shrink: 0;
	align-items: center;
	gap: 6px;
	margin-left: 6px;
}
.session-item:hover .session-item-actions,
.session-item.active .session-item-actions {
	display: flex;
}

.action-btn--edit:hover {
	color: var(--da-accent) !important;
}
.action-btn--star:hover {
	color: var(--da-warning) !important;
}
.action-btn--danger:hover {
	color: var(--da-danger) !important;
}

/* ── Rename input ────────────────────────────────────────────────────────────── */
.session-rename-input {
	flex: 1;
	font-size: 13px;
	border: 1px solid var(--da-accent);
	border-radius: var(--da-radius-sm);
	padding: 1px 5px;
	outline: none;
	min-width: 0;
	background: var(--da-surface);
}

/* ── Empty state ─────────────────────────────────────────────────────────────── */
.empty-sessions--hint {
	display: flex;
	flex-direction: column;
	gap: 4px;
	align-items: center;
	margin: 12px 8px;
	padding: 16px 12px;
	border: 1px dashed var(--da-line-soft);
	border-radius: var(--da-radius-md);
	background: color-mix(in srgb, var(--da-surface-soft) 70%, var(--da-surface));
}
.empty-sessions__title {
	margin: 0;
	font-size: 12.5px;
	font-weight: 600;
	color: var(--da-muted);
}
.empty-sessions__desc {
	margin: 0;
	font-size: 11.5px;
	line-height: 1.45;
	color: var(--da-muted);
	max-width: 160px;
}
.empty-sessions {
	text-align: center;
	font-size: 12px;
	color: var(--da-muted);
	padding: 14px 0;
}

/* ── Bottom new session ──────────────────────────────────────────────────────── */
.sidebar-bottom {
	padding: 8px 10px 10px;
	border-top: 1px solid var(--da-line-soft);
	flex-shrink: 0;
}

.new-session-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-size: 13px !important;
	border-style: dashed !important;
	border-radius: var(--da-radius-md) !important;
	min-height: 40px !important;
}

.sidebar-config-links {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 6px;
	margin-top: 8px;
	flex-wrap: wrap;
}

.sidebar-config-link {
	appearance: none;
	border: none;
	background: transparent;
	padding: 2px 4px;
	font: inherit;
	font-size: 11.5px;
	font-weight: 600;
	color: var(--da-primary);
	cursor: pointer;
	border-radius: var(--da-radius-sm);
}

.sidebar-config-link:hover {
	background: var(--da-primary-soft);
}

.sidebar-config-link:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 1px;
}

.sidebar-config-sep {
	color: var(--da-muted);
	font-size: 11px;
	user-select: none;
}

/* ── Collapsed expand FAB ────────────────────────────────────────────────────── */
.expand-fab {
	position: absolute;
	top: 10px;
	left: 8px;
	z-index: 10;
	box-shadow: var(--da-shadow-md) !important;
	border: 1px solid var(--da-line-soft) !important;
	background: var(--da-surface) !important;
}

/* ── Scrollbar ───────────────────────────────────────────────────────────────── */
.custom-scrollbar::-webkit-scrollbar {
	width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
	background: var(--da-line);
	border-radius: var(--da-radius-sm);
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
	background: var(--da-muted);
}
@media (prefers-reduced-motion: reduce) {
	.sidebar-wrapper {
		transition: none;
	}
}

.sidebar-header__text {
	display: flex;
	flex-direction: column;
	gap: 2px;
	min-width: 0;
}
.sidebar-agent {
	font-size: 11px;
	font-weight: 600;
	color: var(--da-primary);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	max-width: 160px;
}
</style>
