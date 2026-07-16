/*
 * Copyright 2024-2026 the original author or authors.
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

/**
 * @description 网页嵌入（embed）服务——管理端配置 + 公开端令牌交换。
 * 管理端：GET/PUT/DELETE /api/agent/{id}/embed-config（后台）。
 * 公开端：/api/embed/public/{agentId}/{config|exchange|sessions}（widget / embed 页用）。
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/** embed 配置（与后端 vo.EmbedConfig 对齐）。 */
export interface EmbedConfig {
	allowedOrigins: string[];
	welcomeMessage?: string;
	primaryColor?: string;
	widgetPosition?: string;
	showSuggestedQuestions?: boolean;
	defaultLocale?: string;
	rateLimitPerMinute?: number;
	rateLimitPerDay?: number;
}

/** 管理端读取响应。 */
export interface EmbedConfigResponse {
	embedEnabled: number;
	embedConfig: EmbedConfig;
}

/** 发布令牌换会话令牌结果。 */
export interface ExchangeResult {
	sessionToken: string;
	expiresIn: number;
}

const MANAGE_BASE = '/api/agent';
const PUBLIC_BASE = '/api/embed/public';

class EmbedService {
	/** 读某 Agent 的 embed 配置（管理端）。 */
	async getEmbedConfig(agentId: number): Promise<EmbedConfigResponse> {
		const res = await axios.get<EmbedConfigResponse>(`${MANAGE_BASE}/${agentId}/embed-config`);
		return res.data;
	}

	/** 保存并启用 embed 配置（管理端）。 */
	async saveEmbedConfig(agentId: number, config: EmbedConfig): Promise<ApiResponse<EmbedConfig>> {
		const res = await axios.put<ApiResponse<EmbedConfig>>(`${MANAGE_BASE}/${agentId}/embed-config`, config);
		return res.data;
	}

	/** 禁用 embed（管理端，保留配置）。 */
	async disableEmbed(agentId: number): Promise<ApiResponse<void>> {
		const res = await axios.delete<ApiResponse<void>>(`${MANAGE_BASE}/${agentId}/embed-config`);
		return res.data;
	}

	/** 公开端：读 UI 配置（不含令牌，widget 初始化用）。 */
	async publicConfig(agentId: number | string): Promise<EmbedConfig> {
		const res = await axios.get<EmbedConfig>(`${PUBLIC_BASE}/${agentId}/config`);
		return res.data;
	}

	/** 公开端：发布令牌（apiKey）换短期会话令牌。 */
	async exchange(agentId: number | string, publishToken: string): Promise<ExchangeResult> {
		const res = await axios.post<ExchangeResult>(`${PUBLIC_BASE}/${agentId}/exchange`, null, {
			headers: { 'X-Publish-Token': publishToken },
		});
		return res.data;
	}

	/** 公开端：会话令牌建会话，返回 sessionId（兼作 Graph threadId）。 */
	async createSession(agentId: number | string, sessionToken: string): Promise<{ sessionId: string }> {
		const res = await axios.post<{ sessionId: string }>(`${PUBLIC_BASE}/${agentId}/sessions`, null, {
			headers: { 'X-Session-Token': sessionToken },
		});
		return res.data;
	}
}

export default new EmbedService();
