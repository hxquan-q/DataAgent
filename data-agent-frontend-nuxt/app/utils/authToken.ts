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

/** localStorage key for admin JWT */
export const AUTH_TOKEN_KEY = 'dataagent_admin_token';

export function getAccessToken(): string {
	if (import.meta.server) return '';
	try {
		return localStorage.getItem(AUTH_TOKEN_KEY) || '';
	}
	catch {
		return '';
	}
}

export function setAccessToken(token: string) {
	if (import.meta.server) return;
	if (token) localStorage.setItem(AUTH_TOKEN_KEY, token);
	else localStorage.removeItem(AUTH_TOKEN_KEY);
}

export function clearAccessToken() {
	setAccessToken('');
}

/** Append access_token for EventSource (cannot set Authorization header). */
export function withAccessToken(url: string): string {
	const token = getAccessToken();
	if (!token) return url;
	const sep = url.includes('?') ? '&' : '?';
	return `${url}${sep}access_token=${encodeURIComponent(token)}`;
}

export function authHeaders(extra?: Record<string, string>): Record<string, string> {
	const token = getAccessToken();
	const h: Record<string, string> = { ...(extra || {}) };
	if (token) h.Authorization = `Bearer ${token}`;
	return h;
}
