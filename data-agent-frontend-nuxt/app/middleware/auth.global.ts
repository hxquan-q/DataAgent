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

import { getAccessToken } from '~/utils/authToken';

/**
 * 管理端全局鉴权：除 /login、/embed/** 外需 JWT。
 */
export default defineNuxtRouteMiddleware((to) => {
	if (import.meta.server) return;

	const path = to.path || '';
	if (path === '/login' || path.startsWith('/embed')) {
		// 已登录访问 login → 进首页
		if (path === '/login' && getAccessToken()) {
			return navigateTo((to.query.redirect as string) || '/agent/new');
		}
		return;
	}

	if (!getAccessToken()) {
		return navigateTo({
			path: '/login',
			query: { redirect: to.fullPath },
		});
	}
});
