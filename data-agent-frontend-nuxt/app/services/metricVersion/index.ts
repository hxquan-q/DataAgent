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
 * @description 指标版本管理服务，处理指标多版本定义（时间字段、过滤条件等）的 CRUD
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 指标版本实体接口
 */
export interface MetricVersion {
  /** 版本 ID */
  id?: number;
  /** 关联的指标 ID */
  metricId: number;
  /** 版本编码 */
  verCode: string;
  /** 时间字段 */
  timeField?: string;
  /** 过滤条件 (SQL 片段) */
  filterCondition?: string;
  /** 是否默认版本 (0: 否, 1: 是) */
  isDefault: number;
  /** 描述 */
  description?: string;
  /** 状态 (0: 禁用, 1: 启用) */
  status: number;
  /** 创建时间 */
  createdTime?: string;
  /** 更新时间 */
  updatedTime?: string;
}

const API_BASE_URL = '/api/metric-version';

/**
 * @description 指标版本业务逻辑处理类
 */
class MetricVersionService {
  /**
   * @description 根据指标 ID 获取版本列表
   * @param {number} metricId - 指标 ID
   * @returns {Promise<MetricVersion[]>} 版本列表
   */
  async listByMetricId(metricId: number): Promise<MetricVersion[]> {
    const response = await axios.get<ApiResponse<MetricVersion[]>>(
      `${API_BASE_URL}/metric/${metricId}`,
    );
    return response.data.data || [];
  }

  /**
   * @description 根据 ID 获取版本详情
   * @param {number} id - 版本 ID
   * @returns {Promise<MetricVersion | null>} 版本详情
   */
  async get(id: number): Promise<MetricVersion | null> {
    try {
      const response = await axios.get<ApiResponse<MetricVersion>>(`${API_BASE_URL}/${id}`);
      return response.data.data || null;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.status === 404) {
        return null;
      }
      throw error;
    }
  }

  /**
   * @description 创建新指标版本
   * @param {MetricVersion} model - 版本信息
   * @returns {Promise<boolean>} 是否创建成功
   */
  async create(model: MetricVersion): Promise<boolean> {
    const response = await axios.post<ApiResponse>(API_BASE_URL, model);
    return response.data.success;
  }

  /**
   * @description 更新指标版本信息
   * @param {MetricVersion} model - 更新后的版本对象
   * @returns {Promise<boolean>} 是否更新成功
   */
  async update(model: MetricVersion): Promise<boolean> {
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
   * @description 删除指定指标版本
   * @param {number} id - 版本 ID
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
}

export default new MetricVersionService();
