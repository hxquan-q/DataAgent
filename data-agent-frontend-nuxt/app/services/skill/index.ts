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
 * @description 技能（Skill）管理服务，对接 /api/skill，支持技能 CRUD、按 agent 绑定/解绑
 */

import { $fetch } from 'ofetch';

/** 技能作用域 */
export type SkillScope = 'report' | 'sql' | 'python';

/** @description 技能实体 */
export interface Skill {
  id?: number;
  name: string;
  description?: string;
  scope: SkillScope;
  /** 触发关键词，逗号分隔 */
  triggers?: string;
  /** 技能正文/指令 */
  content: string;
  /** 预留参数 JSON */
  paramsJson?: string | null;
  enabled?: boolean;
  /** 优先级，大的先注入 */
  priority?: number;
  displayOrder?: number;
  createTime?: string;
  updateTime?: string;
}

/** @description Agent-技能绑定实体 */
export interface AgentSkill {
  id?: number;
  agentId: number;
  skillId: number;
  enabled?: boolean;
  createTime?: string;
}

interface ApiResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
}

interface PageResponse<T> {
  success: boolean;
  message?: string;
  data?: T;
  total?: number;
  pageNum?: number;
  pageSize?: number;
}

interface PageQuery {
  scope?: SkillScope;
  keyword?: string;
  pageNum?: number;
  pageSize?: number;
}

const API_BASE_URL = '/api/skill';

/**
 * @description 技能管理业务逻辑处理类
 */
class SkillService {
  /** 分页查询技能列表 */
  async page(query: PageQuery = {}): Promise<{ list: Skill[]; total: number }> {
    const params = new URLSearchParams();
    if (query.scope) params.set('scope', query.scope);
    if (query.keyword) params.set('keyword', query.keyword);
    params.set('pageNum', String(query.pageNum ?? 1));
    params.set('pageSize', String(query.pageSize ?? 100));
    const response = await $fetch<PageResponse<Skill[]>>(`${API_BASE_URL}?${params.toString()}`);
    return { list: response.data || [], total: response.total ?? 0 };
  }

  /** 查询单个技能 */
  async get(id: number): Promise<Skill | null> {
    const response = await $fetch<ApiResponse<Skill>>(`${API_BASE_URL}/${id}`);
    return response.data ?? null;
  }

  /** 新建技能 */
  async create(skill: Skill): Promise<ApiResponse<Skill>> {
    return await $fetch<ApiResponse<Skill>>(API_BASE_URL, { method: 'POST', body: skill });
  }

  /** 更新技能 */
  async update(id: number, skill: Skill): Promise<ApiResponse<Skill>> {
    return await $fetch<ApiResponse<Skill>>(`${API_BASE_URL}/${id}`, { method: 'PUT', body: skill });
  }

  /** 删除技能 */
  async remove(id: number): Promise<ApiResponse<boolean>> {
    return await $fetch<ApiResponse<boolean>>(`${API_BASE_URL}/${id}`, { method: 'DELETE' });
  }

  /** 列出某 agent 已绑定的技能 */
  async listByAgent(agentId: number): Promise<Skill[]> {
    const response = await $fetch<ApiResponse<Skill[]>>(`${API_BASE_URL}/agent/${agentId}`);
    return response.data || [];
  }

  /** 绑定技能到 agent */
  async bind(skillId: number, agentId: number, enabled = true): Promise<ApiResponse<AgentSkill>> {
    return await $fetch<ApiResponse<AgentSkill>>(`${API_BASE_URL}/${skillId}/agents/${agentId}`, {
      method: 'POST',
      body: { enabled },
    });
  }

  /** 解绑 */
  async unbind(skillId: number, agentId: number): Promise<ApiResponse<boolean>> {
    return await $fetch<ApiResponse<boolean>>(`${API_BASE_URL}/${skillId}/agents/${agentId}`, {
      method: 'DELETE',
    });
  }
}

export const skillService = new SkillService();
