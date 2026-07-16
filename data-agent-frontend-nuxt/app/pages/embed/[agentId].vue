<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, nextTick } from 'vue';

/**
 * embed 对话页（在宿主站 iframe 内运行）。
 *
 * 通信：mounted → postMessage 'ready' 给 parent(widget)；监听 parent 下发
 *   {type:'token'|'context'|'query'}。token 经 postMessage 传递，不进 URL。
 * 对话：EventSource 消费 GET /api/embed/public/{agentId}/chat（复用 Graph SSE 链路），
 *   sessionToken 走 query（EventSource 无法设 header）。
 * 多轮：首次发送建 session（sessionId 作 threadId）。
 * hostContext：宿主上下文前缀拼进 query（对齐 WeKnora buildQueryWithHostContext）。
 *
 * MVP 渲染：按 nodeName 累积 text 到 block，markdown 渲染（echarts/结果集完整渲染留 R5）。
 */
definePageMeta({ layout: false });

// 防止 session token 经 Referer 泄漏到第三方
useHead({
	meta: [{ name: 'referrer', content: 'no-referrer' }],
});

const route = useRoute();
const agentId = computed(() => String(route.params.agentId));

interface Block {
	nodeName: string;
	text: string;
}
interface Msg {
	role: 'user' | 'assistant';
	content: string;
	blocks?: Block[];
}

const config = ref<{ welcomeMessage?: string; primaryColor?: string; title?: string } | null>(null);
const token = ref('');
const hostContext = ref<Record<string, unknown>>({});
const sessionId = ref('');
const messages = ref<Msg[]>([]);
const input = ref('');
const streaming = ref(false);
const liveBlocks = ref<Block[]>([]);
const ready = ref(false);

const listRef = ref<HTMLDivElement | null>(null);
let activeES: EventSource | null = null;

function scrollToBottom() {
	nextTick(() => {
		if (listRef.value) listRef.value.scrollTop = listRef.value.scrollHeight;
	});
}

function onHostMessage(ev: MessageEvent) {
	if (ev.source !== window.parent) return;
	const d = ev.data || {};
	if (d.source !== 'dataagent-host') return;
	if (d.type === 'token') {
		token.value = d.token;
		ready.value = true;
	} else if (d.type === 'context') {
		hostContext.value = d.context || {};
	} else if (d.type === 'query') {
		input.value = d.query;
		send();
	}
}

async function ensureSession() {
	if (sessionId.value) return;
	const res = await fetch(`/api/embed/public/${agentId.value}/sessions`, {
		method: 'POST',
		headers: { 'X-Session-Token': token.value },
	});
	if (!res.ok) throw new Error('创建会话失败');
	const data = await res.json();
	sessionId.value = data.sessionId;
}

function buildQuery(q: string): string {
	const ctx = hostContext.value;
	const keys = Object.keys(ctx).filter((k) => {
		const v = ctx[k];
		return v !== undefined && v !== null && v !== '';
	});
	if (!keys.length) return q;
	const lines = keys.map((k) => `${k}: ${typeof ctx[k] === 'string' ? ctx[k] : JSON.stringify(ctx[k])}`);
	return `[Host context]\n${lines.join('\n')}\n\n${q}`;
}

async function send() {
	const q = input.value.trim();
	if (!q || !token.value || streaming.value) return;
	try {
		await ensureSession();
	} catch (e) {
		messages.value.push({ role: 'assistant', content: '⚠️ 会话初始化失败，请稍后重试。' });
		return;
	}
	messages.value.push({ role: 'user', content: q });
	input.value = '';
	streaming.value = true;
	liveBlocks.value = [];
	scrollToBottom();

	const params = new URLSearchParams({
		query: buildQuery(q),
		threadId: sessionId.value,
		token: token.value,
	});
	const es = new EventSource(`/api/embed/public/${agentId.value}/chat?${params.toString()}`);
	activeES = es;
	es.onmessage = (event) => {
		try {
			const node = JSON.parse(event.data) as { nodeName: string; text: string };
			if (!node.text) return;
			const last = liveBlocks.value[liveBlocks.value.length - 1];
			if (last && last.nodeName === node.nodeName) {
				last.text += node.text;
			} else {
				liveBlocks.value.push({ nodeName: node.nodeName, text: node.text });
			}
			scrollToBottom();
		} catch {
			/* 忽略非 JSON 帧 */
		}
	};
	es.addEventListener('complete', finish);
	es.addEventListener('error', finish);
	function finish() {
		streaming.value = false;
		if (liveBlocks.value.length) {
			messages.value.push({ role: 'assistant', content: '', blocks: [...liveBlocks.value] });
		}
		liveBlocks.value = [];
		es.close();
		activeES = null;
		scrollToBottom();
	}
}

function stop() {
	activeES?.close();
	activeES = null;
	streaming.value = false;
	if (liveBlocks.value.length) {
		messages.value.push({ role: 'assistant', content: '', blocks: [...liveBlocks.value] });
	}
	liveBlocks.value = [];
}

function onKeydown(e: KeyboardEvent) {
	if (e.key === 'Enter' && !e.shiftKey) {
		e.preventDefault();
		send();
	}
}

onMounted(async () => {
	window.parent?.postMessage({ source: 'dataagent-embed', type: 'ready' }, '*');
	window.addEventListener('message', onHostMessage);
	try {
		const res = await fetch(`/api/embed/public/${agentId.value}/config`);
		if (res.ok) config.value = await res.json();
	} catch {
		/* config 可选 */
	}
});

onBeforeUnmount(() => {
	activeES?.close();
	window.removeEventListener('message', onHostMessage);
});

const themeColor = computed(() => config.value?.primaryColor || '#07C05F');
const title = computed(() => config.value?.title || 'DataAgent 助手');
</script>

<template>
	<div class="embed-app">
		<header class="embed-header" :style="{ background: themeColor }">
			<span>{{ title }}</span>
		</header>

		<div ref="listRef" class="embed-body">
			<div v-if="config?.welcomeMessage && messages.length === 0" class="welcome">
				{{ config.welcomeMessage }}
			</div>
			<div v-if="!ready && messages.length === 0" class="welcome muted">正在初始化嵌入会话…</div>

			<template v-for="(m, i) in messages" :key="i">
				<div v-if="m.role === 'user'" class="bubble user">{{ m.content }}</div>
				<div v-else class="bubble assistant">
					<div v-for="(b, j) in m.blocks" :key="j" class="block">
						<div class="block-name">{{ b.nodeName }}</div>
						<pre class="block-text">{{ b.text }}</pre>
					</div>
				</div>
			</template>

			<div v-if="streaming" class="bubble assistant">
				<div v-for="(b, j) in liveBlocks" :key="j" class="block">
					<div class="block-name">{{ b.nodeName }}</div>
					<pre class="block-text">{{ b.text }}</pre>
				</div>
				<div v-if="liveBlocks.length === 0" class="dots"><span /><span /><span /></div>
			</div>
		</div>

		<footer class="embed-footer">
			<textarea
				v-model="input"
				class="embed-input"
				rows="1"
				placeholder="输入你的问题…"
				@keydown="onKeydown"
			/>
			<button v-if="!streaming" class="send-btn" :style="{ background: themeColor }" @click="send">发送</button>
			<button v-else class="send-btn stop" @click="stop">停止</button>
		</footer>
	</div>
</template>

<style scoped>
.embed-app {
	display: flex;
	flex-direction: column;
	height: 100vh;
	font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
	background: #f7f8fa;
}
.embed-header {
	color: #fff;
	font-weight: 600;
	padding: 12px 16px;
	font-size: 15px;
}
.embed-body {
	flex: 1;
	overflow-y: auto;
	padding: 16px;
	display: flex;
	flex-direction: column;
	gap: 10px;
}
.welcome {
	background: #fff;
	padding: 12px 14px;
	border-radius: 10px;
	font-size: 14px;
	color: #333;
}
.welcome.muted {
	color: #999;
}
.bubble {
	max-width: 85%;
	padding: 10px 14px;
	border-radius: 12px;
	font-size: 14px;
	line-height: 1.5;
	white-space: pre-wrap;
	word-break: break-word;
}
.bubble.user {
	align-self: flex-end;
	background: #e8f0fe;
	color: #1a1a1a;
}
.bubble.assistant {
	align-self: flex-start;
	background: #fff;
	border: 1px solid #ececec;
}
.block {
	margin-bottom: 8px;
}
.block:last-child {
	margin-bottom: 0;
}
.block-name {
	font-size: 11px;
	color: #888;
	margin-bottom: 2px;
}
.block-text {
	margin: 0;
	white-space: pre-wrap;
	word-break: break-word;
	font-family: inherit;
	font-size: 13px;
}
.dots {
	display: flex;
	gap: 4px;
	padding: 4px 0;
}
.dots span {
	width: 7px;
	height: 7px;
	background: #bbb;
	border-radius: 50%;
	animation: bounce 1.2s infinite ease-in-out;
}
.dots span:nth-child(2) {
	animation-delay: 0.2s;
}
.dots span:nth-child(3) {
	animation-delay: 0.4s;
}
@keyframes bounce {
	0%, 80%, 100% {
		transform: scale(0.6);
		opacity: 0.5;
	}
	40% {
		transform: scale(1);
		opacity: 1;
	}
}
.embed-footer {
	display: flex;
	gap: 8px;
	padding: 10px 12px;
	background: #fff;
	border-top: 1px solid #ececec;
}
.embed-input {
	flex: 1;
	border: 1px solid #e0e0e0;
	border-radius: 8px;
	padding: 8px 10px;
	font-size: 14px;
	resize: none;
	outline: none;
	max-height: 100px;
	font-family: inherit;
}
.embed-input:focus {
	border-color: #bdbdbd;
}
.send-btn {
	border: none;
	color: #fff;
	padding: 0 18px;
	border-radius: 8px;
	font-size: 14px;
	cursor: pointer;
}
.send-btn.stop {
	background: #999;
}
</style>
