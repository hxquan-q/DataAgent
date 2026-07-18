<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import embedService from '~/services/embed/index';
import agentService from '~/services/agent/index';
import type { EmbedConfig } from '~/services/embed/index';

/**
 * 网页嵌入（embed）管理页：选择 Agent → 配置白名单/UI/限流 → 启用；
 * 管理 apiKey（发布令牌）生成/重置；生成嵌入代码片段；iframe 实时预览（exchange + postMessage token）。
 */
const route = useRoute();
const $tip = (useNuxtApp() as unknown as { $tip: (o: { message: string; color?: string }) => void }).$tip;
const ok = (m: string) => $tip({ message: m, color: 'success' });
const err = (m: string) => $tip({ message: m, color: 'error' });

const agents = ref<Awaited<ReturnType<typeof agentService.list>>>([]);
const selectedAgentId = ref<number | null>(null);

const loading = ref(false);
const saving = ref(false);
const embedded = ref(false);

const form = ref<EmbedConfig>({
	allowedOrigins: [],
	welcomeMessage: '',
	primaryColor: '#2F84D6',
	widgetPosition: 'bottom-right',
	showSuggestedQuestions: true,
	defaultLocale: 'zh-CN',
	rateLimitPerMinute: 30,
	rateLimitPerDay: 10000,
});
const originsText = ref('');

const apiKeyMasked = ref<string>('');
const apiKeyEnabled = ref<number>(0);
const apiKeyPlain = ref<string>(''); // 仅生成/重置后临时持有明文（预览用）

const previewReady = ref(false);
const previewFrame = ref<HTMLIFrameElement | null>(null);

const positionOptions = [
	{ title: '右下', value: 'bottom-right' },
	{ title: '左下', value: 'bottom-left' },
	{ title: '右上', value: 'top-right' },
	{ title: '左上', value: 'top-left' },
];

const baseUrl = computed(() => (typeof window !== 'undefined' ? window.location.origin : ''));

/** 嵌入代码片段（不安全模式：含 apiKey，标注警告）。 */
const embedSnippet = computed(() => {
	if (!selectedAgentId.value || !apiKeyPlain.value) return '';
	return `<script src="${baseUrl.value}/dataagent-widget.js"><\/script>
<script>
  DataAgent.init({
    agentId: '${selectedAgentId.value}',
    token: '${apiKeyPlain.value}',  // ⚠️ 发布令牌会暴露在页面，生产建议改用 tokenEndpoint
    baseUrl: '${baseUrl.value}'
  });
<\/script>`;
});

function originsTextToList(text: string): string[] {
	return text
		.split('\n')
		.map((l) => l.trim())
		.filter(Boolean);
}

async function loadAgents() {
	agents.value = await agentService.list();
	const qid = Number(route.query.agentId);
	if (qid && agents.value.some((a) => a.id === qid)) {
		selectedAgentId.value = qid;
	} else if (agents.value.length) {
		selectedAgentId.value = (agents.value[0].id as number) ?? null;
	}
	if (selectedAgentId.value) await loadConfig();
}

async function loadConfig() {
	if (!selectedAgentId.value) return;
	loading.value = true;
	try {
		const [cfgRes, keyRes] = await Promise.all([
			embedService.getEmbedConfig(selectedAgentId.value),
			agentService.getApiKey(selectedAgentId.value),
		]);
		embedded.value = cfgRes.embedEnabled === 1;
		const c = cfgRes.embedConfig || ({} as EmbedConfig);
		form.value = {
			allowedOrigins: c.allowedOrigins || [],
			welcomeMessage: c.welcomeMessage || '',
			primaryColor: c.primaryColor || '#2F84D6',
			widgetPosition: c.widgetPosition || 'bottom-right',
			showSuggestedQuestions: c.showSuggestedQuestions ?? true,
			defaultLocale: c.defaultLocale || 'zh-CN',
			rateLimitPerMinute: c.rateLimitPerMinute ?? 30,
			rateLimitPerDay: c.rateLimitPerDay ?? 10000,
		};
		originsText.value = form.value.allowedOrigins.join('\n');
		apiKeyMasked.value = keyRes?.apiKey ?? '';
		apiKeyEnabled.value = (keyRes?.apiKeyEnabled as number) ?? 0;
	} catch (e) {
		err('加载嵌入配置失败');
	} finally {
		loading.value = false;
	}
}

async function save() {
	if (!selectedAgentId.value) return;
	const origins = originsTextToList(originsText.value);
	if (origins.length === 0) {
		err('请至少填写一个允许嵌入的宿主域');
		return;
	}
	if (origins.length === 1 && origins[0] === '*') {
		err('生产环境禁用通配符 *，请显式列出宿主域');
		return;
	}
	saving.value = true;
	try {
		await embedService.saveEmbedConfig(selectedAgentId.value, { ...form.value, allowedOrigins: origins });
		embedded.value = true;
		ok('嵌入配置已保存并启用');
	} catch (e) {
		err('保存失败');
	} finally {
		saving.value = false;
	}
}

async function disable() {
	if (!selectedAgentId.value) return;
	try {
		await embedService.disableEmbed(selectedAgentId.value);
		embedded.value = false;
		ok('已禁用嵌入');
	} catch (e) {
		err('禁用失败');
	}
}

async function generateApiKey() {
	if (!selectedAgentId.value) return;
	try {
		const res = await agentService.generateApiKey(selectedAgentId.value);
		apiKeyPlain.value = res.apiKey ?? '';
		apiKeyMasked.value = res.apiKey ?? '';
		apiKeyEnabled.value = res.apiKeyEnabled as number;
		ok('已生成发布令牌（仅本次显示明文）');
	} catch (e) {
		err('生成失败');
	}
}

async function resetApiKey() {
	if (!selectedAgentId.value) return;
	try {
		const res = await agentService.resetApiKey(selectedAgentId.value);
		apiKeyPlain.value = res.apiKey ?? '';
		apiKeyMasked.value = res.apiKey ?? '';
		apiKeyEnabled.value = res.apiKeyEnabled as number;
		ok('已重置发布令牌（仅本次显示明文）');
	} catch (e) {
		err('重置失败');
	}
}

async function copy(text: string) {
	if (!text) return;
	try {
		await navigator.clipboard.writeText(text);
		ok('已复制');
	} catch {
		err('复制失败');
	}
}

/** 预览：用明文发布令牌换会话令牌，postMessage 给 iframe。 */
async function onPreviewMessage(ev: MessageEvent) {
	const frame = previewFrame.value;
	if (!frame || ev.source !== frame.contentWindow) return;
	const d = ev.data || {};
	if (d.source !== 'dataagent-embed' || d.type !== 'ready') return;
	if (!apiKeyPlain.value || !selectedAgentId.value) return;
	try {
		const { sessionToken } = await embedService.exchange(selectedAgentId.value, apiKeyPlain.value);
		frame.contentWindow?.postMessage({ source: 'dataagent-host', type: 'token', token: sessionToken }, '*');
		previewReady.value = true;
	} catch {
		err('预览令牌交换失败');
	}
}

const previewSrc = computed(() => (selectedAgentId.value ? `/embed/${selectedAgentId.value}` : ''));

onMounted(async () => {
	window.addEventListener('message', onPreviewMessage);
	await loadAgents();
});
onBeforeUnmount(() => window.removeEventListener('message', onPreviewMessage));

watch(selectedAgentId, () => {
	apiKeyPlain.value = '';
	previewReady.value = false;
	loadConfig();
});
</script>

<template>
	<v-container fluid class="page-shell pa-5">
		<div class="d-flex align-center mb-3">
			<v-icon icon="mdi-web-box" color="primary" class="mr-2" />
			<h2 class="text-subtitle-1 font-weight-bold mb-0">网页嵌入（Embed）</h2>
			<v-chip v-if="embedded" size="small" color="success" class="ml-3">已启用</v-chip>
		</div>

		<v-row>
			<v-col cols="12" md="4">
				<v-card variant="outlined" class="mb-3">
					<v-card-title class="text-subtitle-1">选择智能体</v-card-title>
					<v-card-text>
						<v-select
							v-model="selectedAgentId"
							:items="agents"
							item-title="name"
							item-value="id"
							variant="outlined"
							density="compact"
							:loading="loading"
						/>
					</v-card-text>
				</v-card>

				<v-card variant="outlined" title="发布令牌（apiKey）">
					<v-card-text>
						<v-text-field
							:model-value="apiKeyMasked"
							label="发布令牌"
							variant="outlined"
							density="compact"
							readonly
							append-inner-icon="mdi-content-copy"
							@click:append-inner="copy(apiKeyPlain || apiKeyMasked)"
						/>
						<div class="d-flex gap-2 mt-2">
							<v-btn size="small" variant="outlined" @click="generateApiKey">
								{{ apiKeyEnabled ? '重新生成' : '生成令牌' }}
							</v-btn>
							<v-btn v-if="apiKeyEnabled" size="small" variant="outlined" color="warning" @click="resetApiKey">
								轮换
							</v-btn>
						</div>
						<v-alert v-if="apiKeyPlain" type="warning" density="compact" class="mt-3 text-caption">
							明文仅本次显示，刷新后丢失。生产请用 tokenEndpoint 安全模式。
						</v-alert>
					</v-card-text>
				</v-card>
			</v-col>

			<v-col cols="12" md="8">
				<v-card variant="outlined" class="mb-3">
					<v-card-title class="text-subtitle-1">嵌入配置</v-card-title>
					<v-card-text>
						<v-textarea
							v-model="originsText"
							label="允许嵌入的宿主域（每行一个，支持 https://*.example.com）"
							variant="outlined"
							density="compact"
							:placeholder="'https://www.example.com\nhttps://*.example.com'"
							rows="3"
						/>
						<v-text-field
							v-model="form.welcomeMessage"
							label="欢迎语"
							variant="outlined"
							density="compact"
							class="mt-2"
						/>
						<v-row dense>
							<v-col cols="6" md="3">
								<v-text-field v-model="form.primaryColor" label="主题色" variant="outlined" density="compact" type="color" />
							</v-col>
							<v-col cols="6" md="3">
								<v-select v-model="form.widgetPosition" :items="positionOptions" item-title="title" item-value="value" label="位置" variant="outlined" density="compact" />
							</v-col>
							<v-col cols="6" md="3">
								<v-text-field v-model.number="form.rateLimitPerMinute" label="每分钟上限" type="number" variant="outlined" density="compact" />
							</v-col>
							<v-col cols="6" md="3">
								<v-switch v-model="form.showSuggestedQuestions" label="建议问题" color="primary" hide-details density="compact" />
							</v-col>
						</v-row>
						<div class="d-flex gap-2 mt-4">
							<v-btn color="primary" :loading="saving" @click="save">保存并启用</v-btn>
							<v-btn v-if="embedded" variant="outlined" color="error" @click="disable">禁用</v-btn>
						</div>
					</v-card-text>
				</v-card>

				<v-card variant="outlined" title="嵌入代码" class="mb-3">
					<v-card-text>
						<v-alert v-if="!apiKeyPlain" type="info" density="compact" class="mb-2 text-caption">
							需先生成发布令牌以生成嵌入代码（生产环境建议改用 tokenEndpoint 安全模式）。
						</v-alert>
						<v-textarea :model-value="embedSnippet" readonly variant="outlined" rows="6" auto-grow />
						<v-btn size="small" variant="outlined" class="mt-2" :disabled="!embedSnippet" @click="copy(embedSnippet)">
							复制代码
						</v-btn>
					</v-card-text>
				</v-card>

				<v-card variant="outlined" title="实时预览">
					<v-card-text>
						<v-alert v-if="!apiKeyPlain" type="info" density="compact" class="mb-2 text-caption">
							生成发布令牌后可预览真实对话。
						</v-alert>
						<div class="preview-wrap">
							<iframe
								v-if="previewSrc"
								ref="previewFrame"
								:src="previewSrc"
								class="preview-frame"
								title="embed preview"
							/>
						</div>
					</v-card-text>
				</v-card>
			</v-col>
		</v-row>
	</v-container>
</template>

<style scoped>
.gap-2 {
	gap: 8px;
}
.preview-wrap {
	border: 1px solid var(--da-line-soft);
	border-radius: 12px;
	overflow: hidden;
	height: 560px;
	background: var(--da-surface-soft);
}
.preview-frame {
	width: 100%;
	height: 100%;
	border: none;
}
</style>
