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
	<div class="input-area">
		<!-- Status / Info bar -->
		<div class="status-bar">
			<div class="status-chips">

				<!-- Datasource selector (R151: empty-datasource guidance) -->
				<div class="ds-chip-wrap" @click.stop>
					<div
						class="status-chip status-chip--ds"
						:class="{ disabled: store.isStreaming, warn: store.allDatasources.length === 0 }"
						@click="toggleDsMenu"
					>
						<v-icon size="13" :color="store.allDatasources.length ? '#64748b' : '#f59e0b'">mdi-database-outline</v-icon>
						<span>{{
							store.activeDatasource?.name
								|| (store.allDatasources.length ? '选择数据库' : '未绑定数据源')
						}}</span>
						<v-icon size="13" color="#94a3b8">{{ showDsMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</div>
					<div v-if="showDsMenu" class="chip-dropdown">
						<template v-if="store.allDatasources.length">
							<div
								v-for="ds in store.allDatasources"
								:key="ds.id"
								class="chip-dropdown-item"
								:class="{ active: store.activeDatasource?.id === ds.id }"
								@click="selectDs(ds)"
							>
								<span class="item-name">{{ ds.name }}</span>
								<span class="item-tag">{{ ds.type?.toUpperCase() }}</span>
							</div>
						</template>
						<div v-else class="chip-dropdown-empty">
							<p class="chip-dropdown-empty__text">
								当前智能体未绑定可用数据源，NL2SQL 无法查库。请到「数据源配置」关联并激活。
							</p>
							<button type="button" class="chip-dropdown-empty__cta" @click="goAgentDatasource">
								去绑定数据源
							</button>
						</div>
					</div>
				</div>

				<!-- Model selector (R149: empty-model guidance) -->
				<div class="ds-chip-wrap" @click.stop>
					<div
						class="status-chip status-chip--model"
						:class="{ disabled: store.isStreaming, warn: store.chatModels.length === 0 }"
						@click="toggleModelMenu"
					>
						<v-icon size="13" :color="store.chatModels.length ? '#3b82f6' : '#f59e0b'">mdi-lightning-bolt</v-icon>
						<span>{{
							store.activeModelConfig?.modelName
								|| (store.chatModels.length ? '选择AI模型' : '未配置模型')
						}}</span>
						<v-icon size="13" color="#94a3b8">{{ showModelMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</div>
					<div v-if="showModelMenu" class="chip-dropdown">
						<template v-if="store.chatModels.length">
							<div
								v-for="m in store.chatModels"
								:key="m.id"
								class="chip-dropdown-item"
								:class="{ active: store.activeModelConfig?.id === m.id }"
								@click="selectModel(m)"
							>
								<span class="item-name">{{ m.modelName }}</span>
								<span class="item-tag">{{ m.provider }}</span>
							</div>
						</template>
						<div v-else class="chip-dropdown-empty">
							<p class="chip-dropdown-empty__text">尚未配置 CHAT 模型，无法生成回答。</p>
							<button type="button" class="chip-dropdown-empty__cta" @click="goModelConfig">
								去配置模型
							</button>
						</div>
					</div>
				</div>

			</div>
		</div>

		<!-- Textarea -->
		<div class="textarea-wrap">
			<textarea
				ref="textareaRef"
				v-model="inputText"
				class="chat-textarea"
				:disabled="store.isStreaming || store.showHumanFeedback"
				:placeholder="composerPlaceholder"
				rows="3"
				@keydown.enter.exact.prevent="handleSend"
				@input="autoResize"
			/>
			<p v-if="inputText.length > 200" class="input-char-count" aria-live="polite">
				{{ inputText.length }} 字
			</p>
		</div>

		<!-- Bottom action bar -->
		<div class="action-bar">
			<div class="action-bar-left">
				<div class="extra-options">
					<label class="option-chip" :class="{ active: store.requestOptions.humanFeedback }">
						<input
							v-model="store.requestOptions.humanFeedback"
							type="checkbox"
							:disabled="store.requestOptions.nl2sqlOnly || store.isStreaming"
							class="hidden-checkbox"
						/>
						<v-icon size="11">mdi-account-check-outline</v-icon>
						人工反馈
					</label>
					<label class="option-chip" :class="{ active: store.requestOptions.nl2sqlOnly }">
						<input
							v-model="store.requestOptions.nl2sqlOnly"
							type="checkbox"
							:disabled="store.isStreaming"
							class="hidden-checkbox"
							@change="onNl2sqlChange"
						/>
						<v-icon size="11">mdi-database-search-outline</v-icon>
						仅NL2SQL
					</label>
					<label class="option-chip" :class="{ active: store.requestOptions.showSqlResults }">
						<input
							v-model="store.requestOptions.showSqlResults"
							type="checkbox"
							:disabled="store.isStreaming"
							class="hidden-checkbox"
						/>
						<v-icon size="11">mdi-table-eye</v-icon>
						显示SQL结果
					</label>
				</div>
			</div>

			<div class="action-bar-right">
				<v-btn
					v-if="!store.isStreaming"
					class="send-btn"
					:disabled="!canSend"
					@click="handleSend"
				>
					发送
					<v-icon size="16" class="ml-1">mdi-arrow-right</v-icon>
				</v-btn>
				<v-btn v-else class="stop-btn" aria-label="停止生成" @click="handleStop">
					<v-icon size="16" color="white">mdi-stop</v-icon>
					停止
				</v-btn>
			</div>
		</div>
		<p v-if="sendBlockReason" class="send-block-hint" role="status">
			<span>{{ sendBlockReason }}</span>
			<button
				v-if="sendBlockReason.includes('模型')"
				type="button"
				class="send-block-hint__link"
				@click="showModelMenu = true"
			>
				打开模型菜单
			</button>
			<button
				v-else-if="sendBlockReason.includes('数据源')"
				type="button"
				class="send-block-hint__link"
				@click="showDsMenu = true"
			>
				打开数据源菜单
			</button>
		</p>

		<!-- Human Feedback Panel -->
		<Transition name="slide-up">
			<div v-if="store.showHumanFeedback" class="human-feedback-panel">
				<div class="feedback-header">
					<v-icon color="warning" size="16" class="mr-1">mdi-account-question-outline</v-icon>
					<span>请确认执行计划</span>
				</div>
				<textarea
					v-model="store.feedbackContent"
					class="feedback-textarea"
					maxlength="1200"
					rows="2"
					placeholder="输入您的反馈意见（留空表示接受计划）"
				/>
				<p class="feedback-count" aria-live="polite">
					{{ (store.feedbackContent || '').length }}/1200
				</p>
				<div class="feedback-actions">
					<v-btn class="feedback-btn feedback-btn--accept" @click="store.submitFeedback(false, store.feedbackContent)">
						<v-icon size="14" class="mr-1">mdi-check</v-icon>接受计划
					</v-btn>
					<v-btn class="feedback-btn feedback-btn--reject" @click="store.submitFeedback(true, store.feedbackContent)">
						<v-icon size="14" class="mr-1">mdi-close</v-icon>拒绝重规划
					</v-btn>
				</div>
			</div>
		</Transition>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';

const store = useChatStore();
const inputText = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const showDsMenu = ref(false);
const showModelMenu = ref(false);

const canSend = computed(() => {
	if (!inputText.value.trim()) return false;
	if (store.showHumanFeedback) return false;
	if (store.isStreaming) return false;
	if (!store.chatModels.length || !store.activeModelConfig) return false;
	if (!store.allDatasources.length || !store.activeDatasource) return false;
	return true;
});

const sendBlockReason = computed(() => {
	if (store.isStreaming) return '';
	if (store.showHumanFeedback) return '请先处理人工确认后再发送';
	if (!store.chatModels.length || !store.activeModelConfig) return '请先配置并选择 CHAT 模型';
	if (!store.allDatasources.length || !store.activeDatasource) return '请先绑定并选择数据源';
	return '';
});

const composerPlaceholder = computed(() => {
	if (sendBlockReason.value) {
		return `${sendBlockReason.value}（配置后即可提问）`;
	}
	return "在这里提问，例如：分析上月各产品的销售增长情况...";
});

function toggleDsMenu() {
	if (store.isStreaming) return;
	showDsMenu.value = !showDsMenu.value;
	if (showDsMenu.value) showModelMenu.value = false;
}

function toggleModelMenu() {
	if (store.isStreaming) return;
	// R149: 无模型时仍打开菜单，展示配置引导
	showModelMenu.value = !showModelMenu.value;
	if (showModelMenu.value) showDsMenu.value = false;
}

function goModelConfig() {
	showModelMenu.value = false;
	navigateTo('/system/model-config');
}

function goAgentDatasource() {
	showDsMenu.value = false;
	const id = store.currentAgentId;
	// R153: 绑定入口在全局数据源页（带 agentId query），非 agent 详情页
	if (id) {
		navigateTo({ path: '/system/data-sources', query: { agentId: String(id) } });
	} else {
		navigateTo('/system/data-sources');
	}
}

async function selectDs(ds: typeof store.allDatasources[0]) {
	showDsMenu.value = false;
	await store.switchDatasource(ds);
}

async function selectModel(m: typeof store.chatModels[0]) {
	showModelMenu.value = false;
	if (m.id !== undefined) await store.switchModel(m.id);
}

function onNl2sqlChange() {
	if (store.requestOptions.nl2sqlOnly) {
		store.requestOptions.humanFeedback = false;
	}
}

function autoResize() {
	const el = textareaRef.value;
	if (!el) return;
	el.style.height = 'auto';
	el.style.height = Math.min(el.scrollHeight, 200) + 'px';
}

async function handleSend() {
	const query = inputText.value.trim();
	if (!query) return;
	if (!store.currentSession) return;
	if (store.isStreaming) return;
	if (!store.chatModels.length || !store.activeModelConfig) {
		// R149: 无可用模型时阻断发送，引导配置
		showModelMenu.value = true;
		return;
	}
	if (!store.allDatasources.length || !store.activeDatasource) {
		// R151: 无数据源时阻断发送
		showDsMenu.value = true;
		return;
	}

	inputText.value = '';
	nextTick(() => {
		if (textareaRef.value) textareaRef.value.style.height = 'auto';
	});

	try {
		await store.sendMessage(query);
	} catch (e) {
		console.error('发送失败', e);
	}
}

async function handleStop() {
	try {
		await store.stopStreaming();
	} catch (e) {
		console.error('停止失败', e);
	}
}

function closeMenus() {
	showDsMenu.value = false;
	showModelMenu.value = false;
}

onMounted(() => document.addEventListener('click', closeMenus));
onUnmounted(() => document.removeEventListener('click', closeMenus));
</script>

<style scoped>
.input-area {
	flex-shrink: 0;
	background: white;
	border-top: 1px solid var(--da-line-soft, #e8edf2);
	padding: 6px 14px 8px;
	max-width: 960px;
	width: 100%;
	margin: 0 auto;
	box-sizing: border-box;
}

/* ── Status bar ──────────────────────────────────────────────────────────────── */
.status-bar {
	margin-bottom: 6px;
}
.status-chips {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}

.ds-chip-wrap {
	position: relative;
}

.status-chip {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	padding: 2px 8px;
	background: #f1f5f9;
	border: 1px solid #e2e8f0;
	border-radius: 20px;
	font-size: 12px;
	color: #475569;
	cursor: pointer;
	user-select: none;
	white-space: nowrap;
	transition: border-color 0.1s, background 0.1s;
}
.status-chip:hover:not(.disabled) {
	border-color: #94a3b8;
}
.status-chip.disabled {
	opacity: 0.5;
	cursor: not-allowed;
}
.status-chip--model {
	background: #eff6ff;
	border-color: #bfdbfe;
	color: #1d4ed8;
}
.status-chip--model:hover:not(.disabled) {
	border-color: #93c5fd;
}

.chip-dropdown {
	position: absolute;
	top: calc(100% + 4px);
	left: 0;
	z-index: 999;
	background: white;
	border: 1px solid #e2e8f0;
	border-radius: 8px;
	box-shadow: 0 4px 16px rgba(0,0,0,0.10);
	min-width: 180px;
	max-width: 300px;
	max-height: 220px;
	overflow-y: auto;
	padding: 4px 0;
}

.chip-dropdown-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 6px;
	padding: 5px 10px;
	font-size: 12.5px;
	color: #334155;
	cursor: pointer;
	transition: background 0.1s;
}
.chip-dropdown-item:hover {
	background: #f1f5f9;
}
.chip-dropdown-item.active {
	background: #eff6ff;
	color: #2563eb;
	font-weight: 500;
}
.item-name {
	font-size: 12.5px;
	flex: 1;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}
.item-tag {
	flex-shrink: 0;
	font-size: 10.5px;
	color: #94a3b8;
	background: #f1f5f9;
	border-radius: 4px;
	padding: 1px 4px;
}

/* ── Textarea ────────────────────────────────────────────────────────────────── */
.textarea-wrap {
	background: #f8fafc;
	border: 1.5px solid #e2e8f0;
	border-radius: 12px;
	overflow: hidden;
	transition: border-color 0.15s;
}
.textarea-wrap:focus-within {
	border-color: #3b82f6;
	background: #fff;
}
.chat-textarea {
	display: block;
	width: 100%;
	padding: 12px 14px 6px;
	background: none;
	border: none;
	outline: none;
	resize: vertical;
	font-size: 14px;
	line-height: 1.6;
	color: #1e293b;
	font-family: inherit;
	min-height: 56px;
	max-height: 240px;
}
.chat-textarea::placeholder {
	color: #94a3b8;
}
.chat-textarea:disabled {
	opacity: 0.6;
	cursor: not-allowed;
}

/* ── Action bar ──────────────────────────────────────────────────────────────── */
.action-bar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 4px 4px 0;
}
.action-bar-left {
	display: flex;
	align-items: center;
	gap: 4px;
	flex-wrap: wrap;
}
/* ── Extra options ───────────────────────────────────────────────────────────── */
.extra-options {
	display: flex;
	align-items: center;
	gap: 4px;
	flex-wrap: wrap;
}
.option-chip {
	display: inline-flex;
	align-items: center;
	gap: 3px;
	padding: 2px 7px;
	background: #f8fafc;
	border: 1px solid #e2e8f0;
	border-radius: 14px;
	font-size: 11.5px;
	color: #64748b;
	cursor: pointer;
	transition: border-color 0.1s, background 0.1s;
	user-select: none;
}
.option-chip:hover {
	border-color: #3b82f6;
	color: #3b82f6;
}
.option-chip.active {
	background: #eff6ff;
	border-color: #3b82f6;
	color: #2563eb;
}
.hidden-checkbox {
	position: absolute;
	opacity: 0;
	width: 0;
	height: 0;
}

/* ── Send button ─────────────────────────────────────────────────────────────── */
.send-btn {
	display: inline-flex;
	align-items: center;
	gap: 8px;
	padding: 8px 18px;
	background: #2563eb;
	color: white;
	border: none;
	border-radius: 20px;
	font-size: 13.5px;
	font-weight: 600;
	cursor: pointer;
	transition: background 0.15s, opacity 0.15s;
	white-space: nowrap;
}
.send-btn:hover:not(:disabled) {
	background: #1d4ed8;
}
.send-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
.send-icon {
	flex-shrink: 0;
}

/* ── Stop button ─────────────────────────────────────────────────────────────── */
.stop-btn {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	padding: 8px 16px;
	background: #ef4444;
	color: white;
	border: none;
	border-radius: 20px;
	font-size: 13.5px;
	font-weight: 600;
	cursor: pointer;
	transition: background 0.15s;
}
.stop-btn:hover {
	background: #dc2626;
}

/* ── Human feedback ──────────────────────────────────────────────────────────── */
.human-feedback-panel {
	margin-top: 8px;
	background: #fffbeb;
	border: 1px solid #fde68a;
	border-radius: 8px;
	padding: 8px 10px;
}
.feedback-header {
	display: flex;
	align-items: center;
	font-size: 13px;
	font-weight: 600;
	color: #92400e;
	margin-bottom: 8px;
}
.feedback-count {
	margin: 4px 0 0;
	font-size: 11px;
	color: #94a3b8;
	text-align: right;
}
.feedback-textarea {
	width: 100%;
	background: white;
	border: 1px solid #fde68a;
	border-radius: 6px;
	padding: 6px 8px;
	font-size: 12.5px;
	resize: none;
	outline: none;
	color: #1e293b;
	font-family: inherit;
	margin-bottom: 8px;
}
.feedback-actions {
	display: flex;
	gap: 8px;
}
.feedback-btn {
	display: inline-flex;
	align-items: center;
	padding: 5px 12px;
	border-radius: 6px;
	font-size: 12.5px;
	font-weight: 600;
	border: none;
	cursor: pointer;
	transition: opacity 0.1s;
}
.feedback-btn--accept {
	background: #22c55e;
	color: white;
}
.feedback-btn--reject {
	background: white;
	color: #ef4444;
	border: 1px solid #ef4444;
}
.feedback-btn:hover {
	opacity: 0.85;
}

/* ── Transitions ─────────────────────────────────────────────────────────────── */
.fade-enter-active, .fade-leave-active { transition: opacity 0.15s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-up-enter-active, .slide-up-leave-active { transition: all 0.2s ease; }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(10px); opacity: 0; }

.send-block-hint {
	margin: 6px 4px 0;
	font-size: 12px;
	line-height: 1.4;
	color: #c2410c;
}

.input-char-count {
	margin: 4px 8px 0;
	font-size: 11px;
	color: #94a3b8;
	text-align: right;
}
</style>
