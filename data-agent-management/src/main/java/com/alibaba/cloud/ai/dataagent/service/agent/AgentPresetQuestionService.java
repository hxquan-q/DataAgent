/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;

import java.util.List;

/**
 * Agent 预设问题服务接口，提供 Agent 预设问题的增删改查及批量管理能力。
 */
public interface AgentPresetQuestionService {

	/**
	 * 根据 Agent ID 获取预设问题列表（仅返回激活的问题，按 sort_order 和 id 排序）。
	 * @param agentId Agent 主键 ID
	 * @return 激活的预设问题列表
	 */
	List<AgentPresetQuestion> findByAgentId(Long agentId);

	/**
	 * 根据 Agent ID 获取全部预设问题（包含未激活的，按 sort_order 和 id 排序）。
	 * @param agentId Agent 主键 ID
	 * @return 全部预设问题列表
	 */
	List<AgentPresetQuestion> findAllByAgentId(Long agentId);

	/**
	 * 创建新的预设问题。
	 * @param question 待创建的预设问题对象
	 * @return 创建后的预设问题对象（包含生成的 ID）
	 */
	AgentPresetQuestion create(AgentPresetQuestion question);

	/**
	 * 更新已存在的预设问题。
	 * @param id 预设问题主键 ID
	 * @param question 待更新的预设问题对象
	 */
	void update(Long id, AgentPresetQuestion question);

	/**
	 * 根据主键 ID 删除预设问题。
	 * @param id 预设问题主键 ID
	 */
	void deleteById(Long id);

	/**
	 * 删除指定 Agent 的全部预设问题。
	 * @param agentId Agent 主键 ID
	 */
	void deleteByAgentId(Long agentId);

	/**
	 * 批量保存预设问题：先删除该 Agent 已有的全部预设问题，再插入新的列表。
	 * @param agentId Agent 主键 ID
	 * @param questions 待批量保存的预设问题列表
	 */
	void batchSave(Long agentId, List<AgentPresetQuestion> questions);

}
