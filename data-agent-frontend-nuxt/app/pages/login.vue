<!--
  Copyright 2026 the original author or authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<template>
	<div class="login-page d-flex align-center justify-center">
		<div class="login-shell">
			<div class="login-brand" aria-hidden="true">
				<div class="login-mark">
					<v-icon icon="mdi-robot-outline" size="22" color="primary" />
				</div>
			</div>
			<div class="login-title">DataAgent</div>
			<div class="login-subtitle">管理登录</div>

			<v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-4" :text="error" />

			<v-form class="login-form" @submit.prevent="onSubmit">
				<v-text-field
					v-model="username"
					label="用户名"
					autocomplete="username"
					density="comfortable"
					variant="outlined"
					hide-details="auto"
					class="mb-3"
					:disabled="loading"
				/>
				<v-text-field
					v-model="password"
					label="密码"
					type="password"
					autocomplete="current-password"
					density="comfortable"
					variant="outlined"
					hide-details="auto"
					class="mb-5"
					:disabled="loading"
				/>
				<v-btn
					type="submit"
					color="primary"
					block
					size="large"
					class="text-none login-submit"
					:loading="loading"
					:disabled="!username || !password"
				>
					登录
				</v-btn>
			</v-form>
			<p class="login-hint">请使用管理员账号密码登录</p>
		</div>
	</div>
</template>

<script setup lang="ts">
import { useAuthStore } from '~/stores/auth';

definePageMeta({ layout: false });

const route = useRoute();
const auth = useAuthStore();
const username = ref('admin');
const password = ref('');
const loading = ref(false);
const error = ref('');

async function onSubmit() {
	loading.value = true;
	error.value = '';
	try {
		await auth.login(username.value.trim(), password.value);
		const redirect = (route.query.redirect as string) || '/agent/new';
		await navigateTo(redirect);
	}
	catch (e: any) {
		error.value = e?.response?.data?.message || e?.message || '登录失败';
	}
	finally {
		loading.value = false;
	}
}
</script>

<style scoped>
.login-page {
	min-height: 100vh;
	padding: 32px 16px;
	background: var(--da-surface-soft);
	color: var(--da-ink);
}

.login-shell {
	width: 100%;
	max-width: 380px;
	padding: 28px 28px 24px;
	background: var(--da-surface);
	border: 0.5px solid color-mix(in srgb, var(--da-line) 55%, transparent);
	border-radius: var(--da-radius-lg, 18px);
	box-shadow: var(--da-shadow-md);
}

.login-brand {
	display: flex;
	justify-content: center;
	margin-bottom: 18px;
}

.login-mark {
	width: 40px;
	height: 40px;
	display: inline-flex;
	align-items: center;
	justify-content: center;
	border-radius: 12px;
	background: var(--da-surface);
	border: 0.5px solid color-mix(in srgb, var(--da-line) 50%, transparent);
	box-shadow: var(--da-shadow-sm);
}

.login-title {
	font-family: var(--da-font-display);
	font-size: 1.5rem;
	font-weight: 500;
	letter-spacing: -0.03em;
	color: var(--da-ink);
	text-align: center;
	margin: 0 0 4px;
}

.login-subtitle {
	text-align: center;
	font-size: 13px;
	color: var(--da-muted);
	margin: 0 0 28px;
	letter-spacing: -0.01em;
}

.login-form :deep(.v-field) {
	border-radius: 12px !important;
	background: var(--da-surface) !important;
}

.login-form :deep(.v-field__outline) {
	--v-field-border-opacity: 0.55;
}

.login-submit {
	border-radius: 12px !important;
	font-weight: 600 !important;
	min-height: 44px;
	letter-spacing: 0 !important;
	box-shadow: none !important;
}

.login-hint {
	margin: 18px 0 0;
	text-align: center;
	font-size: 12px;
	color: color-mix(in srgb, var(--da-muted) 85%, transparent);
}

@media (max-width: 480px) {
	.login-page {
		padding: 20px 12px;
		padding-bottom: calc(20px + env(safe-area-inset-bottom, 0px));
	}
	.login-shell {
		padding: 22px 18px 18px;
		max-width: 100%;
	}
}
</style>
