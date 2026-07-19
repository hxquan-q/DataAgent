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
 * @description 语义别名管理服务，处理自然语言到规范化目标（表/列/指标等）的映射与解析
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

/**
 * @description 语义别名实体接口
 */
export interface SemanticAlias {
  /** 别名 ID */
  id?: number;
  /** 关联的智能体 ID */
  agentId: number;
  /** 别名文本（自然语言表述） */
  aliasText: string;
  /** 目标类型 (TABLE/COLUMN/METRIC 等) */
  targetType: string;
  /** 目标编码 */
  targetCode: string;
  /** 匹配类型 (EXACT/FUZZY 等) */
  matchType: string;
  /** 优先级（数值越大优先级越高） */
  priority: number;
  /** 状态 (0: 禁用, 1: 启用) */
  status: number;
  /** 创建时间 */
  createdTime?: string;
}

const API_BASE_URL = '/api/semantic-alias';

/**
 * @description 语义别名业务逻辑处理类
 */
class SemanticAliasService {
  /**
   * @description 根据智能体 ID 获取别名列表
   * @param {number} agentId - 智能体 ID
   * @returns {Promise<SemanticAlias[]>} 别名列表
   */
  async listByAgentId(agentId: number): Promise<SemanticAlias[]> {
    const response = await axios.get<ApiResponse<SemanticAlias[]>>(
      `${API_BASE_URL}/agent/${agentId}`,
    );
    return response.data.data || [];
  }

  /**
   * @description 解析自然语言文本，返回匹配的语义别名
   * @param {number} agentId - 智能体 ID
   * @param {string} text - 待解析的自然语言文本
   * @returns {Promise<SemanticAlias | null>} 匹配到的别名详情，未匹配返回 null
   */
  async resolve(agentId: number, text: string): Promise<SemanticAlias | null> {
    try {
      const params = {
        agentId: agentId.toString(),
        text,
      };
      const response = await axios.get<ApiResponse<SemanticAlias>>(
        `${API_BASE_URL}/resolve`,
        { params },
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
   * @description 创建新语义别名
   * @param {SemanticAlias} model - 别名信息
   * @returns {Promise<boolean>} 是否创建成功
   */
  async create(model: SemanticAlias): Promise<boolean> {
    const response = await axios.post<ApiResponse>(API_BASE_URL, model);
    return response.data.success;
  }

  /**
   * @description 更新语义别名信息
   * @param {SemanticAlias} model - 更新后的别名对象
   * @returns {Promise<boolean>} 是否更新成功
   */
  async update(model: SemanticAlias): Promise<boolean> {
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
   * @description 删除指定语义别名
   * @param {number} id - 别名 ID
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

export default new SemanticAliasService();
