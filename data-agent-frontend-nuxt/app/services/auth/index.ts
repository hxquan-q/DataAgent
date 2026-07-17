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
import type { ApiResponse } from '~/services/common/index';

export interface LoginResult {
	token: string;
	expiresIn: number;
	username: string;
	adminId: number;
	displayName?: string;
}

export interface MeResult {
	adminId: number;
	username: string;
	displayName: string;
}

class AuthService {
	async login(username: string, password: string): Promise<LoginResult> {
		const res = await axios.post<ApiResponse<LoginResult>>('/api/auth/login', {
			username,
			password,
		});
		if (!res.data?.success || !res.data.data) {
			throw new Error(res.data?.message || '登录失败');
		}
		return res.data.data;
	}

	async me(): Promise<MeResult> {
		const res = await axios.get<ApiResponse<MeResult>>('/api/auth/me');
		if (!res.data?.success || !res.data.data) {
			throw new Error(res.data?.message || '未认证');
		}
		return res.data.data;
	}

	async logout(): Promise<void> {
		try {
			await axios.post('/api/auth/logout');
		}
		catch {
			// ignore network errors on logout
		}
	}

	async changePassword(oldPassword: string, newPassword: string): Promise<void> {
		const res = await axios.post<ApiResponse<void>>('/api/auth/change-password', {
			oldPassword,
			newPassword,
		});
		if (!res.data?.success) {
			throw new Error(res.data?.message || '改密失败');
		}
	}

	async bootstrapStatus(): Promise<boolean> {
		const res = await axios.get<ApiResponse<{ initialized: boolean }>>(
			'/api/auth/bootstrap-status',
		);
		return Boolean(res.data?.data?.initialized);
	}
}

export default new AuthService();
