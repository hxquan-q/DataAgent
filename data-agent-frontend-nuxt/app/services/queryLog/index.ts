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

/**
 * @description 查询证据链服务，回放「语义对象 → SQL → 结果 → 口径」证据链
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 指标口径版本（查询日志详情关联）
 */
export interface MetricVersion {
	/** 版本 ID */
	id?: number;
	/** 关联指标 ID */
	metricId?: number;
	/** 指标编码 */
	metricCode?: string;
	/** 指标名称 */
	metricName?: string;
	/** 版本号 */
	version?: number;
	/** SQL 模板 */
	sqlTemplate?: string;
	/** 业务口径描述 */
	description?: string;
	/** 创建时间 */
	createdTime?: string;
}

/**
 * @description 查询证据链实体接口
 */
export interface QueryLog {
	/** 日志 ID */
	id?: number;
	/** 会话 ID */
	sessionId?: string;
	/** 智能体 ID */
	agentId?: number;
	/** 数据源 ID */
	datasourceId?: number;
	/** 用户原始查询 */
	userQuery?: string;
	/** 语义对象（JSON 字符串，记录命中的语义模型 / 指标） */
	semanticObject?: string;
	/** 生成的 SQL */
	generatedSql?: string;
	/** 命中的指标口径版本列表 */
	metricVersions?: MetricVersion[];
	/** SQL 执行耗时（毫秒） */
	execTimeMs?: number;
	/** 结果行数 */
	rowCount?: number;
	/** 执行状态（SUCCESS / FAIL / CLARIFY） */
	status?: string;
	/** 用户反馈（0: 未标记, 1: 点赞 👍, 2: 点踩 👎） */
	feedback?: number;
	/** 链路追踪 ID */
	traceId?: string;
	/** 创建时间 */
	createdTime?: string;
}

/**
 * @description 分页结果（与后端 PageResult<T> 对齐）
 */
export interface PageResult<T> {
	/** 数据列表 */
	data: T[];
	/** 总记录数 */
	total: number;
	/** 当前页码 */
	pageNum: number;
	/** 每页大小 */
	pageSize: number;
	/** 总页数 */
	totalPages: number;
}

/** 查询日志列表过滤参数 */
export interface QueryLogFilter {
	agentId?: number | null;
	status?: string | null;
	feedback?: number | null;
	pageNum?: number;
	pageSize?: number;
}

const API_BASE = '/api/query-log';

/**
 * @description 查询证据链业务逻辑处理类
 */
class QueryLogService {
	/**
	 * @description 分页查询证据链日志（管理端全局列表，支持按智能体/状态/反馈过滤）
	 * @param {QueryLogFilter} filter - 过滤与分页参数
	 * @returns {Promise<PageResult<QueryLog>>} 分页结果
	 */
	async list(filter: QueryLogFilter = {}): Promise<PageResult<QueryLog>> {
		const response = await axios.get<ApiResponse<PageResult<QueryLog>>>(
			API_BASE,
			{
				params: {
					agentId: filter.agentId ?? undefined,
					status: filter.status ?? undefined,
					feedback: filter.feedback ?? undefined,
					pageNum: filter.pageNum ?? 1,
					pageSize: filter.pageSize ?? 20,
				},
			},
		);
		return (
			response.data.data ?? { data: [], total: 0, pageNum: 1, pageSize: 20, totalPages: 0 }
		);
	}

	/**
	 * @description 按会话获取查询证据链列表（倒序）
	 * @param {string} sessionId - 会话 ID
	 * @returns {Promise<QueryLog[]>} 证据链列表
	 */
	async getSessionLog(sessionId: string): Promise<QueryLog[]> {
		const response = await axios.get<ApiResponse<QueryLog[]>>(
			`${API_BASE}/session/${sessionId}`,
		);
		return response.data.data || [];
	}

	/**
	 * @description 根据 ID 获取查询证据链详情（含语义对象 / 指标口径版本）
	 * @param {number} id - 日志 ID
	 * @returns {Promise<QueryLog | null>} 证据链详情
	 */
	async get(id: number): Promise<QueryLog | null> {
		try {
			const response = await axios.get<ApiResponse<QueryLog>>(
				`${API_BASE}/${id}`,
			);
			return response.data.data || null;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return null;
			}
			throw error;
		}
	}

	/**
	 * @description 记录用户反馈（点赞 / 点踩）
	 * @param {number} id - 日志 ID
	 * @param {0 | 1 | 2} feedback - 0: 未标记, 1: 👍, 2: 👎
	 * @returns {Promise<boolean>} 是否记录成功
	 */
	async recordFeedback(
		id: number,
		feedback: 0 | 1 | 2,
	): Promise<boolean> {
		try {
			const response = await axios.put<ApiResponse>(
				`${API_BASE}/${id}/feedback`,
				null,
				{ params: { feedback } },
			);
			return response.data.success;
		} catch (error) {
			if (axios.isAxiosError(error) && error.response?.status === 404) {
				return false;
			}
			throw error;
		}
	}
}

export default new QueryLogService();
