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
	<div class="welcome-wrap">
		<div class="agent-avatar-wrap da-reveal">
			<v-avatar
				:image="store.currentAgentAvatar || undefined"
				:color="store.currentAgentAvatar ? undefined : 'primary'"
				size="72"
				rounded="circle"
				class="agent-avatar"
			>
				<v-icon v-if="!store.currentAgentAvatar" size="32" color="white">
					mdi-robot-outline
				</v-icon>
			</v-avatar>
		</div>

		<p class="welcome-kicker da-reveal da-reveal-delay-1">Data Agent</p>
		<h2 class="welcome-title da-reveal da-reveal-delay-1">
			您好，我是
			<span class="agent-name">{{ store.currentAgentName || '数据助手' }}</span>
		</h2>

		<div class="welcome-line da-hairline da-reveal-delay-2" aria-hidden="true" />

		<p class="welcome-desc da-reveal da-reveal-delay-2">
			{{
				store.currentAgentDescription ||
				'分析表结构、生成 SQL、输出可视化报告——把问题丢给我即可。'
			}}
		</p>
	</div>
</template>

<script setup lang="ts">
import { useChatStore } from '~/stores/chat';
const store = useChatStore();
</script>

<style scoped>
.welcome-wrap {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: 48px 28px;
	text-align: center;
	max-width: 560px;
	margin: 0 auto;
}

.agent-avatar-wrap {
	margin-bottom: 20px;
}

.agent-avatar {
	box-shadow:
		0 1px 2px rgba(15, 23, 42, 0.06),
		0 12px 28px rgba(30, 64, 175, 0.14);
}

.welcome-kicker {
	margin: 0 0 8px;
	font-size: 12px;
	font-weight: 600;
	letter-spacing: 0.12em;
	text-transform: uppercase;
	color: var(--da-muted, #64748b);
}

.welcome-title {
	font-size: clamp(22px, 3.2vw, 28px);
	font-weight: 600;
	color: var(--da-ink, #0f172a);
	margin: 0 0 18px;
	letter-spacing: -0.02em;
	line-height: 1.25;
}

.agent-name {
	color: var(--da-primary, #1e40af);
	font-weight: 700;
}

.welcome-line {
	max-width: 220px;
	margin: 0 auto 18px;
	background: var(--da-line, #d9d9d9);
}

.welcome-desc {
	margin: 0;
	font-size: 14.5px;
	font-weight: 400;
	color: var(--da-muted, #64748b);
	max-width: 440px;
	line-height: 1.7;
	letter-spacing: -0.01em;
}
</style>
