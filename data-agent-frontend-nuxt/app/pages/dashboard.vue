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
	<section class="page-shell dashboard-placeholder">
		<div class="dashboard-card">
			<p class="dashboard-kicker">Data Agent</p>
			<h1>数据看板</h1>
			<p class="dashboard-desc">
				业务可视化看板位。当前请从「数据问答」发起分析，报告与图表会直接出现在对话结果中。
			</p>
			<div class="dashboard-actions">
				<NuxtLink to="/chat" class="dashboard-link">前往数据问答</NuxtLink>
				<NuxtLink to="/system/agents" class="dashboard-link dashboard-link--ghost">智能体列表</NuxtLink>
			</div>
		</div>
	</section>
</template>

<style scoped>
.dashboard-placeholder {
	max-width: 720px;
	display: flex;
	align-items: center;
	min-height: calc(100vh - 140px);
}

.dashboard-card {
	width: 100%;
	padding: 28px 8px;
	background: transparent;
	border: none;
	border-radius: 0;
	box-shadow: none;
}

.dashboard-kicker {
	margin: 0 0 8px;
	font-size: 11px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.dashboard-placeholder h1 {
	margin: 0 0 12px;
	font-family: var(--da-font-display);
	font-size: clamp(1.6rem, 3vw, 1.9rem);
	font-weight: 500;
	letter-spacing: -0.03em;
	color: var(--da-ink);
}

.dashboard-desc {
	margin: 0 0 22px;
	font-size: 14.5px;
	line-height: 1.7;
	color: var(--da-muted);
	max-width: 48ch;
}

.dashboard-actions {
	display: flex;
	flex-wrap: wrap;
	gap: 10px;
}

.dashboard-link {
	display: inline-flex;
	align-items: center;
	min-height: 36px;
	padding: 0 14px;
	border-radius: 10px;
	background: var(--da-primary);
	color: var(--da-on-primary);
	font-size: 13.5px;
	font-weight: 600;
	text-decoration: none;
	box-shadow: none;
	transition: background var(--da-dur-fast) var(--da-ease-out);
}

.dashboard-link:hover {
	background: color-mix(in srgb, var(--da-primary) 88%, #000);
}

.dashboard-link--ghost {
	background: transparent;
	color: var(--da-ink);
	border: 0.5px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	font-weight: 500;
}

.dashboard-link--ghost:hover {
	background: var(--da-surface-soft);
	color: var(--da-primary);
	border-color: color-mix(in srgb, var(--da-primary) 30%, transparent);
}

.dashboard-link:focus-visible {
	outline: 2px solid var(--da-ring);
	outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
	.dashboard-link {
		transition: none;
	}
}
</style>
