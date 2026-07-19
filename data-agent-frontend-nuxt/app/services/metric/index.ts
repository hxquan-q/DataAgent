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
 * @description 指标管理服务，处理指标定义、聚合配置及候选字段查询
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 指标实体接口
 */
export interface Metric {
  /** 指标 ID */
  id?: number;
  /** 指标编码（唯一） */
  metricCode: string;
  /** 指标名称 */
  metricName: string;
  /** 关联的智能体 ID */
  agentId: number;
  /** 关联的数据源 ID */
  datasourceId: number;
  /** 来源表名 */
  sourceTable: string;
  /** 聚合字段 */
  aggField?: string;
  /** 聚合函数 (SUM/AVG/COUNT/MAX/MIN 等) */
  aggFunc?: string;
  /** 默认时间字段 */
  defaultTimeField?: string;
  /** SQL 模板 */
  sqlTemplate?: string;
  /** 描述 */
  description?: string;
  /** 状态 (0: 禁用, 1: 启用) */
  status: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

/**
 * @description 创建指标的 DTO（不含 id 与时间戳，创建 payload）
 */
export type MetricDto = Omit<Metric, 'id' | 'createdTime' | 'updatedTime'>;

const API_BASE_URL = '/api/metric';

/**
 * @description 指标业务逻辑处理类
 */
class MetricService {
  /**
   * @description 获取指标列表
   * @returns {Promise<Metric[]>} 指标列表
   */
  async list(): Promise<Metric[]> {
    const response = await axios.get<ApiResponse<Metric[]>>(`${API_BASE_URL}/list`);
    return response.data.data || [];
  }

  /**
   * @description 根据 ID 获取指标详情
   * @param {number} id - 指标 ID
   * @returns {Promise<Metric | null>} 指标详情
   */
  async get(id: number): Promise<Metric | null> {
    try {
      const response = await axios.get<ApiResponse<Metric>>(`${API_BASE_URL}/${id}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * @description 根据编码获取指标详情
   * @param {string} code - 指标编码
   * @returns {Promise<Metric | null>} 指标详情
   */
  async getByCode(code: string): Promise<Metric | null> {
    try {
      const response = await axios.get<ApiResponse<Metric>>(`${API_BASE_URL}/code/${code}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * @description 创建新指标
   * @param {Metric} model - 指标信息
   * @returns {Promise<boolean>} 是否创建成功
   */
  async create(model: Metric): Promise<boolean> {
    const response = await axios.post<ApiResponse>(API_BASE_URL, model);
    return response.data.success;
  }

  /**
   * @description 更新指标信息
   * @param {Metric} model - 更新后的指标对象
   * @returns {Promise<boolean>} 是否更新成功
   */
  async update(model: Metric): Promise<boolean> {
    try {
      const response = await axios.put<ApiResponse>(API_BASE_URL, model);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * @description 删除指定指标
   * @param {number} id - 指标 ID
   * @returns {Promise<boolean>} 是否删除成功
   */
  async delete(id: number): Promise<boolean> {
    try {
      const response = await axios.delete<ApiResponse>(`${API_BASE_URL}/${id}`);
      return response.data.success;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return false;
      }
      throw error;
    }
  }

  /**
   * @description 查询指标候选字段（按智能体 + 数据源维度）
   * @param {number} agentId - 智能体 ID
   * @param {number} datasourceId - 数据源 ID
   * @returns {Promise<Metric[]>} 候选指标列表
   */
  async candidates(agentId: number, datasourceId: number): Promise<Metric[]> {
    const params = {
      agentId: agentId.toString(),
      datasourceId: datasourceId.toString(),
    };
    const response = await axios.get<ApiResponse<Metric[]>>(`${API_BASE_URL}/candidates`, {
      params,
    });
    return response.data.data || [];
  }
}

export default new MetricService();
