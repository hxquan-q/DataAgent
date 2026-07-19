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

import axios from 'axios';
import { getAccessToken, clearAccessToken } from '~/utils/authToken';

/**
 * 全局注入 Bearer，并在 401 时清会话跳登录。
 * 覆盖默认 axios 实例（多数 service）。$fetch/fetch 另用 authHeaders()。
 */
export default defineNuxtPlugin(() => {
	axios.interceptors.request.use((config) => {
		const token = getAccessToken();
		if (token) {
			config.headers = config.headers || {};
			config.headers.Authorization = `Bearer ${token}`;
		}
		return config;
	});

	axios.interceptors.response.use(
		(res) => res,
		(error) => {
			const status = error?.response?.status;
			if (status === 401 && import.meta.client) {
				const path = window.location.pathname || '';
				if (!path.startsWith('/login') && !path.startsWith('/embed')) {
					clearAccessToken();
					const redirect = encodeURIComponent(path + window.location.search);
					window.location.href = `/login?redirect=${redirect}`;
				}
			}
			return Promise.reject(error);
		},
	);
});
