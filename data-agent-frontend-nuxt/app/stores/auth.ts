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

import { defineStore } from 'pinia';
import authService from '~/services/auth/index';
import {
	clearAccessToken,
	getAccessToken,
	setAccessToken,
} from '~/utils/authToken';

export const useAuthStore = defineStore('auth', () => {
	const token = ref(getAccessToken());
	const username = ref('');
	const displayName = ref('');
	const adminId = ref<number | null>(null);
	const loaded = ref(false);

	const isAuthenticated = computed(() => Boolean(token.value));

	function applyLogin(result: {
		token: string;
		username: string;
		adminId: number;
		displayName?: string;
	}) {
		setAccessToken(result.token);
		token.value = result.token;
		username.value = result.username;
		displayName.value = result.displayName || result.username;
		adminId.value = result.adminId;
		loaded.value = true;
	}

	async function login(user: string, password: string) {
		const result = await authService.login(user, password);
		applyLogin(result);
		return result;
	}

	async function fetchMe() {
		if (!getAccessToken()) {
			clearSession();
			return null;
		}
		try {
			const me = await authService.me();
			username.value = me.username;
			displayName.value = me.displayName || me.username;
			adminId.value = me.adminId;
			token.value = getAccessToken();
			loaded.value = true;
			return me;
		}
		catch {
			clearSession();
			return null;
		}
	}

	async function logout() {
		try {
			await authService.logout();
		}
		finally {
			clearSession();
		}
	}

	function clearSession() {
		clearAccessToken();
		token.value = '';
		username.value = '';
		displayName.value = '';
		adminId.value = null;
		loaded.value = true;
	}

	async function changePassword(oldPassword: string, newPassword: string) {
		await authService.changePassword(oldPassword, newPassword);
		// 改密后强制重登（v1 无服务端 token 吊销）
		await logout();
	}

	return {
		token,
		username,
		displayName,
		adminId,
		loaded,
		isAuthenticated,
		login,
		fetchMe,
		logout,
		clearSession,
		changePassword,
	};
});
