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
		<!-- Status / Info bar (R228: keyboard-accessible selectors) -->
		<div class="status-bar">
			<div class="status-chips">

				<!-- Datasource selector (R151: empty-datasource guidance) -->
				<div class="ds-chip-wrap" @click.stop>
					<button
						type="button"
						class="status-chip status-chip--ds"
						:class="{ warn: store.allDatasources.length === 0 }"
						:disabled="store.isStreaming"
						:aria-expanded="showDsMenu"
						aria-haspopup="listbox"
						:aria-label="dsChipAriaLabel"
						@click="toggleDsMenu"
					>
						<v-icon size="13" :color="store.allDatasources.length ? '#64748b' : '#f59e0b'" aria-hidden="true">mdi-database-outline</v-icon>
						<span>{{
							store.activeDatasource?.name
								|| (store.allDatasources.length ? '选择数据库' : '未绑定数据源')
						}}</span>
						<v-icon size="13" color="#94a3b8" aria-hidden="true">{{ showDsMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</button>
					<div
						v-if="showDsMenu"
						class="chip-dropdown"
						role="listbox"
						aria-label="数据源列表"
					>
						<template v-if="store.allDatasources.length">
							<button
								v-for="ds in store.allDatasources"
								:key="ds.id"
								type="button"
								class="chip-dropdown-item"
								role="option"
								:class="{ active: store.activeDatasource?.id === ds.id }"
								:aria-selected="store.activeDatasource?.id === ds.id"
								@click="selectDs(ds)"
							>
								<span class="item-name">{{ ds.name }}</span>
								<span class="item-tag">{{ ds.type?.toUpperCase() }}</span>
							</button>
						</template>
						<div v-else class="chip-dropdown-empty" role="presentation">
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
					<button
						type="button"
						class="status-chip status-chip--model"
						:class="{ warn: store.chatModels.length === 0 }"
						:disabled="store.isStreaming"
						:aria-expanded="showModelMenu"
						aria-haspopup="listbox"
						:aria-label="modelChipAriaLabel"
						@click="toggleModelMenu"
					>
						<v-icon size="13" :color="store.chatModels.length ? '#3b82f6' : '#f59e0b'" aria-hidden="true">mdi-lightning-bolt</v-icon>
						<span>{{
							store.activeModelConfig?.modelName
								|| (store.chatModels.length ? '选择AI模型' : '未配置模型')
						}}</span>
						<v-icon size="13" color="#94a3b8" aria-hidden="true">{{ showModelMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</button>
					<div
						v-if="showModelMenu"
						class="chip-dropdown"
						role="listbox"
						aria-label="模型列表"
					>
						<template v-if="store.chatModels.length">
							<button
								v-for="m in store.chatModels"
								:key="m.id"
								type="button"
								class="chip-dropdown-item"
								role="option"
								:class="{ active: store.activeModelConfig?.id === m.id }"
								:aria-selected="store.activeModelConfig?.id === m.id"
								@click="selectModel(m)"
							>
								<span class="item-name">{{ m.modelName }}</span>
								<span class="item-tag">{{ m.provider }}</span>
							</button>
						</template>
						<div v-else class="chip-dropdown-empty" role="presentation">
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
					<label class="option-chip" :class="{ active: store.requestOptions.nl2sqlOnly }">
						<input
							v-model="store.requestOptions.nl2sqlOnly"
							type="checkbox"
							:disabled="store.requestOptions.nl2sqlOnly || store.isStreaming"
							class="hidden-checkbox"
						/>
						<v-icon size="11">mdi-database-search-outline</v-icon>
						仅NL2SQL
					</label>
					<label class="option-chip" :class="{ active: store.requestOptions.humanFeedback }">
						<input
							v-model="store.requestOptions.humanFeedback"
							type="checkbox"
							:disabled="store.requestOptions.humanFeedback || store.isStreaming"
							class="hidden-checkbox"
						/>
						<v-icon size="11">mdi-account-check-outline</v-icon>
						人工反馈
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

// R228: accessible names for status selectors (keyboard / SR)
const dsChipAriaLabel = computed(() => {
	const name =
		store.activeDatasource?.name ||
		(store.allDatasources.length ? '选择数据库' : '未绑定数据源');
	return `数据源：${name}`;
});

const modelChipAriaLabel = computed(() => {
	const name =
		store.activeModelConfig?.modelName ||
		(store.chatModels.length ? '选择AI模型' : '未配置模型');
	return `模型：${name}`;
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
	background: var(--da-surface, #fff);
	border: 1px solid var(--da-line-soft, #e4edf5);
	border-radius: var(--da-composer-radius, 18px);
	padding: 10px 14px 12px;
	max-width: var(--da-chat-max, 960px);
	width: calc(100% - 28px);
	margin: 0 auto 14px;
	box-sizing: border-box;
	box-shadow: var(--da-shadow-composer);
}

/* ── Status bar ──────────────────────────────────────────────────────────────── */
.status-bar {
	margin-bottom: 6px;
}
.status-chips {
	display: flex;
	align-items: center;
	gap: var(--da-space-2, 8px);
	flex-wrap: wrap;
}

.ds-chip-wrap {
	position: relative;
}

/* R228: native button reset + density tokens */
.status-chip {
	display: inline-flex;
	align-items: center;
	gap: 4px;
	min-height: var(--da-control-height-sm, 32px);
	padding: 4px 10px;
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	border-radius: 20px;
	font: inherit;
	font-size: 12px;
	color: var(--da-muted);
	cursor: pointer;
	user-select: none;
	white-space: nowrap;
	transition: border-color var(--da-dur-fast, 0.1s), background var(--da-dur-fast, 0.1s);
}
.status-chip:hover:not(:disabled) {
	border-color: var(--da-muted);
}
.status-chip:disabled {
	opacity: var(--da-disabled-opacity, 0.5);
	cursor: not-allowed;
}
.status-chip:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: 2px;
}
.status-chip.warn {
	border-color: color-mix(in srgb, var(--da-warning) 45%, transparent);
}
.status-chip--model {
	background: var(--da-primary-soft);
	border-color: color-mix(in srgb, var(--da-primary) 35%, transparent);
	color: var(--da-primary);
}
.status-chip--model:hover:not(:disabled) {
	border-color: color-mix(in srgb, var(--da-primary) 45%, transparent);
}

.chip-dropdown {
	position: absolute;
	top: calc(100% + 4px);
	left: 0;
	z-index: var(--da-z-dropdown, 999);
	background: var(--da-surface, #fff);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md, 8px);
	box-shadow: var(--da-shadow-md, 0 4px 16px rgba(0, 0, 0, 0.1));
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
	width: 100%;
	min-height: var(--da-control-height-sm, 32px);
	padding: 6px 10px;
	border: none;
	background: transparent;
	font: inherit;
	font-size: 12.5px;
	text-align: left;
	color: var(--da-ink);
	cursor: pointer;
	transition: background var(--da-dur-fast, 0.1s);
}
.chip-dropdown-item:hover {
	background: var(--da-surface-soft);
}
.chip-dropdown-item:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: -2px;
	background: var(--da-primary-soft);
}
.chip-dropdown-item.active {
	background: var(--da-primary-soft);
	color: var(--da-primary);
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
	color: var(--da-muted);
	background: var(--da-surface-soft);
	border-radius: var(--da-radius-sm, 4px);
	padding: 1px 4px;
}

.chip-dropdown-empty {
	padding: 10px 12px;
	max-width: 260px;
}
.chip-dropdown-empty__text {
	margin: 0 0 8px;
	font-size: 12px;
	line-height: 1.45;
	color: var(--da-muted);
}
.chip-dropdown-empty__cta {
	display: inline-flex;
	align-items: center;
	min-height: var(--da-control-height-sm, 32px);
	padding: 4px 10px;
	border: 1px solid var(--da-primary);
	border-radius: var(--da-radius-sm, 6px);
	background: var(--da-primary-soft);
	color: var(--da-primary);
	font: inherit;
	font-size: 12px;
	font-weight: 600;
	cursor: pointer;
}
.chip-dropdown-empty__cta:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: 2px;
}

/* ── Textarea ────────────────────────────────────────────────────────────────── */
.textarea-wrap {
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft, #e4edf5);
	border-radius: var(--da-radius-md, 12px);
	overflow: hidden;
	transition: border-color var(--da-dur-fast, 0.15s) var(--da-ease-out),
		background var(--da-dur-fast, 0.15s) var(--da-ease-out),
		box-shadow var(--da-dur-fast, 0.15s) var(--da-ease-out);
}
.textarea-wrap:focus-within {
	border-color: color-mix(in srgb, var(--da-primary, #2f84d6) 55%, transparent);
	background: var(--da-surface, #fff);
	box-shadow: 0 0 0 3px color-mix(in srgb, var(--da-primary, #2f84d6) 14%, transparent);
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
	color: var(--da-ink);
	font-family: inherit;
	min-height: 56px;
	max-height: 240px;
}
.chat-textarea::placeholder {
	color: var(--da-muted);
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
	background: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	border-radius: 14px;
	font-size: 11.5px;
	color: var(--da-muted);
	cursor: pointer;
	transition: border-color 0.1s, background 0.1s;
	user-select: none;
}
.option-chip:hover {
	border-color: var(--da-accent);
	color: var(--da-accent);
}
.option-chip:focus-within {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}
.option-chip.active {
	background: var(--da-primary-soft);
	border-color: var(--da-accent);
	color: var(--da-primary);
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
	min-height: var(--da-control-height-md, 40px);
	padding: 8px 18px;
	background: var(--da-primary, #2f84d6);
	color: var(--da-on-primary, #fff);
	border: none;
	border-radius: 999px;
	font-size: 13.5px;
	font-weight: 600;
	cursor: pointer;
	transition: background var(--da-dur-fast, 0.15s) var(--da-ease-out),
		opacity var(--da-dur-fast, 0.15s),
		box-shadow var(--da-dur-fast, 0.15s) var(--da-ease-out);
	white-space: nowrap;
	box-shadow: 0 6px 16px color-mix(in srgb, var(--da-primary, #2f84d6) 28%, transparent);
}
.send-btn:hover:not(:disabled) {
	background: color-mix(in srgb, var(--da-primary, #2f84d6) 88%, #000);
}
.send-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
.send-btn:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: 2px;
}
.send-icon {
	flex-shrink: 0;
}

/* ── Stop button ─────────────────────────────────────────────────────────────── */
.stop-btn {
	display: inline-flex;
	align-items: center;
	gap: 6px;
	min-height: var(--da-control-height-md, 40px);
	padding: 8px 16px;
	background: var(--da-danger);
	color: var(--da-on-danger, #fff);
	border: none;
	border-radius: 20px;
	font-size: 13.5px;
	font-weight: 600;
	cursor: pointer;
	transition: background var(--da-dur-fast, 0.15s);
}
.stop-btn:hover {
	background: var(--da-danger);
}
.stop-btn:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: 2px;
}

/* ── Human feedback ──────────────────────────────────────────────────────────── */
.human-feedback-panel {
	margin-top: 8px;
	background: color-mix(in srgb, var(--da-warning) 10%, white);
	border: 1px solid color-mix(in srgb, var(--da-warning) 35%, white);
	border-radius: var(--da-radius-md);
	padding: 10px 12px;
	box-shadow: var(--da-shadow-sm);
}
.feedback-header {
	display: flex;
	align-items: center;
	font-size: 13px;
	font-weight: 600;
	color: var(--da-warning);
	margin-bottom: 8px;
}
.feedback-count {
	margin: 4px 0 0;
	font-size: 11px;
	color: var(--da-muted);
	text-align: right;
}
.feedback-textarea {
	width: 100%;
	background: var(--da-surface);
	border: 1px solid color-mix(in srgb, var(--da-warning) 35%, white);
	border-radius: var(--da-radius-sm);
	padding: 8px 10px;
	font-size: 12.5px;
	resize: none;
	outline: none;
	color: var(--da-ink);
	font-family: inherit;
	margin-bottom: 8px;
}
.feedback-textarea:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 1px;
}
.feedback-actions {
	display: flex;
	gap: 8px;
	flex-wrap: wrap;
}
.feedback-btn {
	display: inline-flex;
	align-items: center;
	min-height: var(--da-control-height-sm);
	padding: 5px 12px;
	border-radius: 999px;
	font-size: 12.5px;
	font-weight: 600;
	border: none;
	cursor: pointer;
	transition: opacity var(--da-dur-fast) var(--da-ease-out);
}
.feedback-btn--accept {
	background: var(--da-success);
	color: var(--da-on-primary);
}
.feedback-btn--reject {
	background: var(--da-surface);
	color: var(--da-danger);
	border: 1px solid var(--da-danger);
}
.feedback-btn:hover {
	opacity: 0.9;
}
.feedback-btn:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

/* ── Transitions ─────────────────────────────────────────────────────────────── */
.fade-enter-active, .fade-leave-active { transition: opacity var(--da-dur-fast, 0.15s); }
.fade-enter-from, .fade-leave-to { opacity: 0; }
.slide-up-enter-active, .slide-up-leave-active { transition: all var(--da-dur-base, 0.2s) var(--da-ease-out, ease); }
.slide-up-enter-from, .slide-up-leave-to { transform: translateY(10px); opacity: 0; }

.send-block-hint {
	margin: 6px 4px 4px;
	font-size: 12px;
	line-height: 1.4;
	color: var(--da-warning);
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
}
.send-block-hint__link {
	border: none;
	background: transparent;
	padding: 0;
	font: inherit;
	font-size: 12px;
	font-weight: 600;
	color: var(--da-primary);
	cursor: pointer;
	text-decoration: underline;
}
.send-block-hint__link:focus-visible {
	outline: 2px solid var(--da-ring, var(--da-primary));
	outline-offset: 2px;
	border-radius: 2px;
}

.input-char-count {
	margin: 4px 8px 0;
	font-size: 11px;
	color: var(--da-muted);
	text-align: right;
}

@media (prefers-reduced-motion: reduce) {
	.status-chip,
	.chip-dropdown-item,
	.send-btn,
	.stop-btn,
	.fade-enter-active,
	.fade-leave-active,
	.slide-up-enter-active,
	.slide-up-leave-active {
		transition: none !important;
	}
	.slide-up-enter-from,
	.slide-up-leave-to {
		transform: none;
	}
}
</style>
