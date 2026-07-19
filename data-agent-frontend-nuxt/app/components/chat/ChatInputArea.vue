<template>
	<div class="composer" :class="{ 'composer--streaming': store.isStreaming }">
		<!-- DEEIX InputGroup: textarea body first -->
		<div class="composer__body">
			<textarea
				ref="textareaRef"
				v-model="inputText"
				class="composer__textarea"
				:disabled="store.isStreaming || store.showHumanFeedback"
				:placeholder="composerPlaceholder"
				rows="2"
				@keydown.enter.exact.prevent="handleSend"
				@input="autoResize"
			/>
			<p v-if="inputText.length > 200" class="composer__count" aria-live="polite">
				{{ inputText.length }}
			</p>
		</div>

		<!-- Bottom toolbar: tools · options · send -->
		<div class="composer__toolbar">
			<div class="composer__tools">
				<!-- Datasource -->
				<div class="tool-wrap" @click.stop>
					<button
						type="button"
						class="tool-chip"
						:class="{ warn: store.allDatasources.length === 0 }"
						:disabled="store.isStreaming"
						:aria-expanded="showDsMenu"
						aria-haspopup="listbox"
						:aria-label="dsChipAriaLabel"
						@click="toggleDsMenu"
					>
						<v-icon size="14" aria-hidden="true">mdi-database-outline</v-icon>
						<span class="tool-chip__text">{{
							store.activeDatasource?.name
								|| (store.allDatasources.length ? '数据源' : '未绑定')
						}}</span>
						<v-icon size="12" aria-hidden="true">{{ showDsMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</button>
					<div v-if="showDsMenu" class="tool-menu" role="listbox" aria-label="数据源列表">
						<template v-if="store.allDatasources.length">
							<button
								v-for="ds in store.allDatasources"
								:key="ds.id"
								type="button"
								class="tool-menu__item"
								role="option"
								:class="{ active: store.activeDatasource?.id === ds.id }"
								:aria-selected="store.activeDatasource?.id === ds.id"
								@click="selectDs(ds)"
							>
								<span>{{ ds.name }}</span>
								<span class="tool-menu__tag">{{ ds.type?.toUpperCase() }}</span>
							</button>
						</template>
						<div v-else class="tool-menu__empty">
							<p>当前智能体未绑定数据源</p>
							<button type="button" class="tool-menu__cta" @click="goAgentDatasource">去绑定</button>
						</div>
					</div>
				</div>

				<!-- Model -->
				<div class="tool-wrap" @click.stop>
					<button
						type="button"
						class="tool-chip tool-chip--model"
						:class="{ warn: store.chatModels.length === 0 }"
						:disabled="store.isStreaming"
						:aria-expanded="showModelMenu"
						aria-haspopup="listbox"
						:aria-label="modelChipAriaLabel"
						@click="toggleModelMenu"
					>
						<v-icon size="14" aria-hidden="true">mdi-lightning-bolt</v-icon>
						<span class="tool-chip__text">{{
							store.activeModelConfig?.modelName
								|| (store.chatModels.length ? '模型' : '未配置')
						}}</span>
						<v-icon size="12" aria-hidden="true">{{ showModelMenu ? 'mdi-chevron-up' : 'mdi-chevron-down' }}</v-icon>
					</button>
					<div v-if="showModelMenu" class="tool-menu" role="listbox" aria-label="模型列表">
						<template v-if="store.chatModels.length">
							<button
								v-for="m in store.chatModels"
								:key="m.id"
								type="button"
								class="tool-menu__item"
								role="option"
								:class="{ active: store.activeModelConfig?.id === m.id }"
								:aria-selected="store.activeModelConfig?.id === m.id"
								@click="selectModel(m)"
							>
								<span>{{ m.modelName }}</span>
								<span class="tool-menu__tag">{{ m.provider }}</span>
							</button>
						</template>
						<div v-else class="tool-menu__empty">
							<p>尚未配置 CHAT 模型</p>
							<button type="button" class="tool-menu__cta" @click="goModelConfig">去配置</button>
						</div>
					</div>
				</div>

				<!-- Advanced options tucked away (clean dock) -->
				<div class="tool-wrap" @click.stop>
					<button
						type="button"
						class="tool-chip tool-chip--more"
						:disabled="store.isStreaming"
						:aria-expanded="showMoreMenu"
						aria-haspopup="menu"
						aria-label="更多选项"
						title="更多选项"
						@click="showMoreMenu = !showMoreMenu; if (showMoreMenu) { showDsMenu = false; showModelMenu = false; }"
					>
						<v-icon size="16" aria-hidden="true">mdi-dots-horizontal</v-icon>
					</button>
					<div v-if="showMoreMenu" class="tool-menu tool-menu--opts" role="menu" aria-label="请求选项">
						<label class="opt-row" role="menuitemcheckbox">
							<input v-model="store.requestOptions.showSqlResults" type="checkbox" :disabled="store.isStreaming" />
							<span>显示 SQL 结果</span>
						</label>
						<label class="opt-row" role="menuitemcheckbox">
							<input v-model="store.requestOptions.nl2sqlOnly" type="checkbox" :disabled="store.isStreaming" />
							<span>仅 NL2SQL</span>
						</label>
						<label class="opt-row" role="menuitemcheckbox">
							<input
								v-model="store.requestOptions.humanFeedback"
								type="checkbox"
								:disabled="store.requestOptions.humanFeedback || store.isStreaming"
							/>
							<span>人工反馈</span>
						</label>
					</div>
				</div>
			</div>

			<div class="composer__send">
				<button
					v-if="!store.isStreaming"
					type="button"
					class="send"
					:disabled="!canSend"
					aria-label="发送"
					title="发送"
					@click="handleSend"
				>
					<v-icon size="18" aria-hidden="true">mdi-arrow-up</v-icon>
				</button>
				<button
					v-else
					type="button"
					class="stop"
					aria-label="停止生成"
					title="停止生成"
					@click="handleStop"
				>
					<v-icon size="16" aria-hidden="true">mdi-stop</v-icon>
				</button>
			</div>
		</div>

		<p v-if="sendBlockReason" class="composer__hint" role="status">
			<span>{{ sendBlockReason }}</span>
			<button
				v-if="sendBlockReason.includes('模型')"
				type="button"
				class="composer__hint-link"
				@click="showModelMenu = true"
			>
				打开模型
			</button>
			<button
				v-else-if="sendBlockReason.includes('数据源')"
				type="button"
				class="composer__hint-link"
				@click="showDsMenu = true"
			>
				打开数据源
			</button>
		</p>

		<!-- Human feedback -->
		<div v-if="store.showHumanFeedback" class="feedback-panel">
			<div class="feedback-panel__head">
				<span>需要人工确认</span>
			</div>
			<textarea
				v-model="store.feedbackContent"
				class="feedback-panel__input"
				placeholder="输入反馈或修改意见…"
				rows="3"
				maxlength="1200"
			/>
			<p class="feedback-panel__count">{{ (store.feedbackContent || '').length }}/1200</p>
			<div class="feedback-panel__actions">
				<button type="button" class="feedback-panel__btn" @click="store.submitFeedback(true, store.feedbackContent)">
					拒绝
				</button>
				<button type="button" class="feedback-panel__btn feedback-panel__btn--primary" @click="store.submitFeedback(false, store.feedbackContent)">
					接受并继续
				</button>
			</div>
		</div>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';

const store = useChatStore();
const inputText = ref('');
const textareaRef = ref<HTMLTextAreaElement | null>(null);
const showDsMenu = ref(false);
const showModelMenu = ref(false);
const showMoreMenu = ref(false);

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
	if (showDsMenu.value) {
		showModelMenu.value = false;
		showMoreMenu.value = false;
	}
}

function toggleModelMenu() {
	if (store.isStreaming) return;
	// R149: 无模型时仍打开菜单，展示配置引导
	showModelMenu.value = !showModelMenu.value;
	if (showModelMenu.value) {
		showDsMenu.value = false;
		showMoreMenu.value = false;
	}
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
		// Empty canvas may have no session yet (DEEIX first message)
		if (!store.currentSession && store.currentAgentId) {
			await store.createNewSession(store.currentAgentId);
		}
		if (!store.currentSession) return;
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
	showMoreMenu.value = false;
}

onMounted(() => document.addEventListener('click', closeMenus));
onUnmounted(() => document.removeEventListener('click', closeMenus));
</script>


<style scoped>
/* DEEIX floating InputGroup composer */
.composer {
	position: relative;
	flex-shrink: 0;
	z-index: 5;
	width: calc(100% - 40px);
	max-width: min(100%, var(--da-chat-max, 1080px));
	margin: 0 auto 20px;
	padding: 10px 12px 8px;
	box-sizing: border-box;
	background: var(--da-surface);
	border: 0.5px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	border-radius: var(--da-composer-radius, 26px);
	box-shadow: 0 1px 2px rgba(15, 35, 55, 0.05);
	transition:
		border-color var(--da-dur-fast) var(--da-ease-out),
		box-shadow var(--da-dur-fast) var(--da-ease-out);
}
.composer:focus-within {
	border-color: color-mix(in srgb, var(--da-line) 90%, transparent);
	box-shadow: var(--da-shadow-md);
}

.composer__body {
	position: relative;
	min-height: 48px;
}
.composer__textarea {
	display: block;
	width: 100%;
	min-height: 48px;
	max-height: 200px;
	resize: none;
	border: none;
	outline: none;
	background: transparent;
	color: var(--da-ink);
	font: inherit;
	font-family: var(--da-font-chat, var(--da-font-sans));
	font-size: 15px;
	line-height: 1.55;
	letter-spacing: -0.01em;
	padding: 4px 4px 8px;
}
.composer__textarea::placeholder {
	color: color-mix(in srgb, var(--da-muted) 78%, transparent);
}
.composer__textarea:disabled {
	opacity: 0.55;
	cursor: not-allowed;
}
.composer__count {
	position: absolute;
	right: 4px;
	bottom: 0;
	margin: 0;
	font-size: 11px;
	color: var(--da-muted);
}

.composer__toolbar {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 10px;
	padding-top: 4px;
}
.composer__tools {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 6px;
	min-width: 0;
	flex: 1;
}
.composer__send {
	flex-shrink: 0;
}

.tool-wrap {
	position: relative;
}
.tool-chip {
	appearance: none;
	display: inline-flex;
	align-items: center;
	gap: 4px;
	min-height: 30px;
	max-width: 160px;
	padding: 4px 10px;
	border-radius: 999px;
	border: 1px solid color-mix(in srgb, var(--da-line-soft) 95%, transparent);
	background: transparent;
	color: var(--da-muted);
	font: inherit;
	font-size: 12px;
	font-weight: 500;
	cursor: pointer;
	transition: background var(--da-dur-fast) var(--da-ease-out), border-color var(--da-dur-fast) var(--da-ease-out);
}
.tool-chip:hover:not(:disabled) {
	background: var(--da-surface-soft);
	color: var(--da-ink);
}
.tool-chip:disabled {
	opacity: 0.5;
	cursor: not-allowed;
}
.tool-chip:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}
.tool-chip.warn {
	border-color: color-mix(in srgb, var(--da-warning) 45%, transparent);
	color: var(--da-warning);
}
.tool-chip--model {
	background: transparent;
	border-color: color-mix(in srgb, var(--da-line-soft) 95%, transparent);
	color: var(--da-muted);
}
.tool-chip--model:hover:not(:disabled) {
	color: var(--da-primary);
	background: var(--da-primary-soft);
}
.tool-chip__text {
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	max-width: 110px;
}

.tool-menu {
	position: absolute;
	bottom: calc(100% + 6px);
	left: 0;
	z-index: var(--da-z-dropdown, 1000);
	min-width: 200px;
	max-width: 300px;
	max-height: 240px;
	overflow: auto;
	padding: 4px;
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-md);
	box-shadow: var(--da-shadow-md);
}
.tool-menu__item {
	appearance: none;
	width: 100%;
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 8px;
	min-height: 34px;
	padding: 6px 10px;
	border: none;
	border-radius: 8px;
	background: transparent;
	color: var(--da-ink);
	font: inherit;
	font-size: 13px;
	text-align: left;
	cursor: pointer;
}
.tool-menu__item:hover,
.tool-menu__item.active {
	background: var(--da-primary-soft);
	color: var(--da-primary);
}
.tool-menu__tag {
	font-size: 10.5px;
	color: var(--da-muted);
}
.tool-menu__empty {
	padding: 10px 12px;
	font-size: 12.5px;
	color: var(--da-muted);
}
.tool-menu__cta {
	appearance: none;
	margin-top: 8px;
	border: none;
	background: var(--da-primary);
	color: var(--da-on-primary);
	border-radius: 8px;
	padding: 6px 10px;
	font: inherit;
	font-size: 12.5px;
	font-weight: 600;
	cursor: pointer;
}

.opt {
	display: inline-flex;
	align-items: center;
	min-height: 28px;
	padding: 2px 8px;
	border-radius: 999px;
	border: 1px solid transparent;
	color: var(--da-muted);
	font-size: 11.5px;
	font-weight: 600;
	cursor: pointer;
	user-select: none;
}
.opt:hover {
	background: var(--da-surface-soft);
	color: var(--da-ink);
}
.opt.on {
	background: var(--da-primary-soft);
	color: var(--da-primary);
	border-color: color-mix(in srgb, var(--da-primary) 22%, transparent);
}
.tool-chip--more {
	min-width: 30px;
	padding: 4px 8px;
	color: var(--da-muted);
}
.tool-menu--opts {
	min-width: 180px;
	bottom: calc(100% + 6px);
	left: auto;
	right: 0;
}
.opt-row {
	display: flex;
	align-items: center;
	gap: 8px;
	min-height: 34px;
	padding: 6px 10px;
	border-radius: 8px;
	font-size: 12.5px;
	color: var(--da-ink);
	cursor: pointer;
}
.opt-row:hover {
	background: var(--da-primary-soft);
}
.opt-row input {
	accent-color: var(--da-primary);
}
.sr {
	position: absolute;
	opacity: 0;
	width: 0;
	height: 0;
	pointer-events: none;
}

.send,
.stop {
	appearance: none;
	width: 36px;
	height: 36px;
	border-radius: 50%;
	border: none;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	cursor: pointer;
	transition: transform var(--da-dur-fast) var(--da-ease-out), opacity var(--da-dur-fast) var(--da-ease-out);
}
.send {
	background: var(--da-primary);
	color: var(--da-on-primary);
	box-shadow: 0 1px 2px rgba(15, 35, 55, 0.12);
}
.send:hover:not(:disabled) {
	transform: translateY(-1px);
}
.send:disabled {
	opacity: 0.35;
	cursor: not-allowed;
}
.stop {
	background: var(--da-ink);
	color: var(--da-surface);
}
.send:focus-visible,
.stop:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.composer__hint {
	display: flex;
	align-items: center;
	flex-wrap: wrap;
	gap: 8px;
	margin: 8px 2px 0;
	font-size: 12px;
	color: var(--da-warning);
}
.composer__hint-link {
	appearance: none;
	border: none;
	background: transparent;
	color: var(--da-primary);
	font: inherit;
	font-size: 12px;
	font-weight: 600;
	cursor: pointer;
	text-decoration: underline;
}

.feedback-panel {
	margin-top: 10px;
	padding: 12px;
	border-radius: var(--da-radius-md);
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface-soft);
}
.feedback-panel__head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	margin-bottom: 8px;
	font-size: 13px;
	font-weight: 600;
	color: var(--da-ink);
}
.feedback-panel__close {
	appearance: none;
	border: none;
	background: transparent;
	color: var(--da-muted);
	cursor: pointer;
	width: 28px;
	height: 28px;
	border-radius: 8px;
}
.feedback-panel__input {
	width: 100%;
	min-height: 72px;
	resize: vertical;
	border: 1px solid var(--da-line-soft);
	border-radius: 10px;
	padding: 8px 10px;
	font: inherit;
	font-size: 13.5px;
	background: var(--da-surface);
	color: var(--da-ink);
	box-sizing: border-box;
}
.feedback-panel__actions {
	display: flex;
	justify-content: flex-end;
	gap: 8px;
	margin-top: 8px;
}
.feedback-panel__btn {
	appearance: none;
	min-height: 32px;
	padding: 4px 12px;
	border-radius: 999px;
	border: 1px solid var(--da-line-soft);
	background: var(--da-surface);
	color: var(--da-ink);
	font: inherit;
	font-size: 12.5px;
	font-weight: 600;
	cursor: pointer;
}
.feedback-panel__btn--primary {
	background: var(--da-primary);
	border-color: var(--da-primary);
	color: var(--da-on-primary);
}

@media (max-width: 768px) {
	.composer {
		width: calc(100% - 16px);
		margin: 0 auto calc(10px + var(--da-safe-bottom, 0px));
		padding: 8px 10px 8px;
		border-radius: 20px;
	}
	.composer__textarea {
		min-height: 40px;
		font-size: 16px; /* prevent iOS zoom */
	}
	.tool-chip__text {
		max-width: 64px;
	}
	.tool-chip {
		min-height: 32px;
		padding: 4px 8px;
	}
	.send,
	.stop {
		width: 40px;
		height: 40px;
	}
	/* hide model/ds text on very narrow — keep icons via text truncation already */
}
@media (max-width: 400px) {
	.tool-chip__text {
		display: none;
	}
	.tool-chip {
		padding: 4px 8px;
	}
}

@media (prefers-reduced-motion: reduce) {
	.composer,
	.send {
		transition: none;
	}
	.send:hover:not(:disabled) {
		transform: none;
	}
}
</style>
