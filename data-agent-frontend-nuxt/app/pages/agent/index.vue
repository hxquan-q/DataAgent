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
	<section class="page-shell agent-index-placeholder">
		<div class="agent-index-card">
			<p class="agent-index-kicker">Data Agent</p>
			<h1 class="agent-index-title">智能体工作台</h1>
			<p class="agent-index-desc">
				从这里进入智能体管理、新建智能体或数据问答。问数会话依赖激活的 CHAT 模型与绑定数据源。
			</p>
			<div class="agent-index-actions">
				<button type="button" class="agent-index-btn agent-index-btn--primary" @click="goAgents">
					智能体列表
				</button>
				<button type="button" class="agent-index-btn" @click="goNew">
					新建智能体
				</button>
				<button type="button" class="agent-index-btn" @click="goChat">
					数据问答
				</button>
				<button type="button" class="agent-index-btn" @click="goModels">
					模型服务
				</button>
			</div>
		</div>
	</section>
</template>

<script setup lang="ts">
function goAgents() {
	navigateTo('/system/agents');
}
function goNew() {
	navigateTo('/agent/new');
}
function goChat() {
	navigateTo('/chat');
}
function goModels() {
	navigateTo('/system/model-config');
}
</script>

<style scoped>
.agent-index-placeholder {
	max-width: 720px;
	display: flex;
	align-items: center;
	min-height: calc(100vh - 120px);
}

.agent-index-card {
	width: 100%;
	padding: 28px 24px;
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-lg);
	box-shadow: var(--da-shadow-md);
}

.agent-index-kicker {
	margin: 0 0 8px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.agent-index-title {
	margin: 0 0 12px;
	font-family: var(--da-font-display);
	font-size: 28px;
	font-weight: 500;
	letter-spacing: -0.02em;
	color: var(--da-ink);
}

.agent-index-desc {
	margin: 0 0 20px;
	font-size: 14.5px;
	line-height: 1.7;
	color: var(--da-muted);
}

.agent-index-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.agent-index-btn {
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
	transition:
		background var(--da-dur-fast) var(--da-ease-out),
		border-color var(--da-dur-fast) var(--da-ease-out),
		color var(--da-dur-fast) var(--da-ease-out);
}

.agent-index-btn:hover {
	border-color: color-mix(in srgb, var(--da-primary) 35%, transparent);
	background: var(--da-primary-soft);
	color: var(--da-primary);
}

.agent-index-btn:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

.agent-index-btn--primary {
	background: var(--da-primary);
	border-color: var(--da-primary);
	color: var(--da-on-primary);
	box-shadow: 0 8px 18px color-mix(in srgb, var(--da-primary) 28%, transparent);
}

.agent-index-btn--primary:hover {
	background: color-mix(in srgb, var(--da-primary) 88%, #000);
	border-color: transparent;
	color: var(--da-on-primary);
}

@media (max-width: 480px) {
	.agent-index-btn {
		width: 100%;
	}
}

@media (prefers-reduced-motion: reduce) {
	.agent-index-btn {
		transition: none;
	}
}
</style>
