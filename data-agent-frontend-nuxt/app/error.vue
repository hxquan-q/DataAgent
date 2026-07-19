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

<script setup lang="ts">
import type { NuxtError } from '#app';

const props = defineProps({
	error: Object as () => NuxtError,
});

const statusCode = computed(() => props.error?.statusCode || 500);
const statusText = computed(() => {
	const map: Record<number, string> = {
		400: '请求参数错误',
		401: '身份认证失败',
		403: '没有访问权限',
		404: '页面不存在',
		500: '服务器内部错误',
		502: '网关错误',
		503: '服务暂时不可用',
	};
	return props.error?.statusMessage || map[statusCode.value] || '发生了未知错误';
});

const illustration = computed(() => {
	if (statusCode.value === 404) return 'mdi-map-marker-question-outline';
	if (statusCode.value === 403) return 'mdi-shield-lock-outline';
	if (statusCode.value === 401) return 'mdi-account-lock-outline';
	return 'mdi-alert-circle-outline';
});

const handleBack = () => clearError({ redirect: '/' });
</script>

<template>
	<div class="error-page">
		<div class="error-card">
			<div class="error-icon-wrap">
				<v-icon :icon="illustration" size="72" color="primary" />
			</div>

			<div class="error-code">{{ statusCode }}</div>
			<div class="error-text">{{ statusText }}</div>

			<p v-if="error?.message && error.message !== statusText" class="error-detail">
				{{ error.message }}
			</p>

			<div class="error-actions">
				<v-btn
					color="primary"
					variant="flat"
					size="large"
					prepend-icon="mdi-home-outline"
					class="action-btn"
					@click="handleBack"
				>
					返回首页
				</v-btn>
				<v-btn
					variant="outlined"
					size="large"
					prepend-icon="mdi-refresh"
					class="action-btn"
					@click="() => reloadNuxtApp()"
				>
					刷新页面
				</v-btn>
			</div>
		</div>
	</div>
</template>

<style scoped>
.error-page {
	min-height: 100vh;
	display: flex;
	align-items: center;
	justify-content: center;
	padding: 24px;
	background: var(--da-surface-soft);
}

.error-card {
	text-align: center;
	max-width: 460px;
	width: 100%;
	padding: 36px 28px;
	background: var(--da-surface);
	border: 1px solid var(--da-line-soft);
	border-radius: var(--da-radius-lg);
	box-shadow: var(--da-shadow-md);
}

.error-icon-wrap {
	margin-bottom: 16px;
	opacity: 0.85;
}

.error-code {
	font-family: var(--da-font-display);
	font-size: 84px;
	font-weight: 700;
	line-height: 1;
	color: color-mix(in srgb, var(--da-primary) 28%, var(--da-line));
	letter-spacing: -0.04em;
	margin-bottom: 12px;
}

.error-text {
	font-size: 20px;
	font-weight: 600;
	color: var(--da-ink);
	margin-bottom: 8px;
}

.error-detail {
	font-size: 14px;
	color: var(--da-muted);
	line-height: 1.6;
	margin-bottom: 28px;
	word-break: break-word;
}

.error-actions {
	display: flex;
	align-items: center;
	justify-content: center;
	gap: 12px;
	margin-top: 8px;
	flex-wrap: wrap;
}

.action-btn {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-weight: 600 !important;
	border-radius: 999px !important;
	padding: 0 22px !important;
	min-height: var(--da-control-height-md);
}

@media (max-width: 480px) {
	.error-card {
		padding: 28px 18px;
	}
	.action-btn {
		width: 100%;
	}
}
</style>
