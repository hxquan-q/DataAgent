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
	<section class="page-shell agent-detail-placeholder">
		<div class="agent-detail-card">
			<p class="agent-detail-kicker">Data Agent</p>
			<h1 class="agent-detail-title">智能体详情</h1>
			<p class="agent-detail-desc">
				智能体 #{{ agentId }} 的配置入口。可前往数据问答发起分析，或到系统页管理模型与数据源。
			</p>
			<div class="agent-detail-actions">
				<button type="button" class="agent-detail-btn agent-detail-btn--primary" @click="goChat">
					数据问答
				</button>
				<button type="button" class="agent-detail-btn" @click="goAgents">
					智能体列表
				</button>
				<button type="button" class="agent-detail-btn" @click="goDatasource">
					数据源
				</button>
			</div>
		</div>
	</section>
</template>

<script setup lang="ts">
const route = useRoute();
const agentId = computed(() => String(route.params.id || ''));

function goChat() {
	const id = agentId.value;
	if (id) navigateTo({ path: '/chat', query: { agentId: id } });
	else navigateTo('/chat');
}
function goAgents() {
	navigateTo('/system/agents');
}
function goDatasource() {
	const id = agentId.value;
	if (id) navigateTo({ path: '/system/data-sources', query: { agentId: id } });
	else navigateTo('/system/data-sources');
}
</script>

<style scoped>
.agent-detail-placeholder {
	max-width: 720px;
	display: flex;
	align-items: center;
	min-height: calc(100vh - 120px);
}

.agent-detail-card {
	width: 100%;
	padding: 28px 24px;
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-lg);
	box-shadow: var(--da-shadow-md);
}

.agent-detail-kicker {
	margin: 0 0 8px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.agent-detail-title {
	margin: 0 0 12px;
	font-family: var(--da-font-display);
	font-size: 28px;
	font-weight: 500;
	letter-spacing: -0.02em;
	color: var(--da-ink);
}

.agent-detail-desc {
	margin: 0 0 20px;
	font-size: 14.5px;
	line-height: 1.7;
	color: var(--da-muted);
}

.agent-detail-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.agent-detail-btn {
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
}

.agent-detail-btn:hover {
	border-color: color-mix(in srgb, var(--da-primary) 35%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
}

.agent-detail-btn:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.agent-detail-btn--primary {
	background: var(--da-primary);
	border-color: var(--da-primary);
	color: var(--da-on-primary);
	box-shadow: 0 8px 18px color-mix(in srgb, var(--da-primary) 28%, transparent);
}

.agent-detail-btn--primary:hover {
	background: color-mix(in srgb, var(--da-primary) 88%, #000);
	border-color: transparent;
	color: var(--da-on-primary);
}

@media (max-width: 480px) {
	.agent-detail-btn {
		width: 100%;
	}
}
</style>
