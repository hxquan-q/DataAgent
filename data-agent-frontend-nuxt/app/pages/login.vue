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
		<v-card width="400" max-width="92vw" class="pa-6" elevation="2">
			<div class="text-h6 font-weight-bold mb-1">DataAgent 管理登录</div>
			<div class="text-caption text-medium-emphasis mb-4">
				请使用管理员账号密码登录
			</div>

			<v-alert v-if="error" type="error" variant="tonal" density="compact" class="mb-3" :text="error" />

			<v-form @submit.prevent="onSubmit">
				<v-text-field
					v-model="username"
					label="用户名"
					autocomplete="username"
					density="comfortable"
					variant="outlined"
					class="mb-2"
					:disabled="loading"
				/>
				<v-text-field
					v-model="password"
					label="密码"
					type="password"
					autocomplete="current-password"
					density="comfortable"
					variant="outlined"
					class="mb-4"
					:disabled="loading"
				/>
				<v-btn
					type="submit"
					color="primary"
					block
					size="large"
					:loading="loading"
					:disabled="!username || !password"
				>
					登录
				</v-btn>
			</v-form>
		</v-card>
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
	background: linear-gradient(160deg, var(--da-ink) 0%, var(--da-ink) 50%, var(--da-ink) 100%);
}
</style>
