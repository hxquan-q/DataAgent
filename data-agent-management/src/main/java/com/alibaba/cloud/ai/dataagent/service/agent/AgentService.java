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

import com.alibaba.cloud.ai.dataagent.entity.Agent;

import java.util.List;

/**
 * Agent 管理服务接口，提供 Agent 实体的增删改查及 API Key 管理能力。
 */
public interface AgentService {

	/**
	 * 查询全部 Agent。
	 * @return Agent 列表
	 */
	List<Agent> findAll();

	/**
	 * 根据主键 ID 查询单个 Agent。
	 * @param id Agent 主键 ID
	 * @return 对应的 Agent 对象，不存在时返回 null
	 */
	Agent findById(Long id);

	/**
	 * 根据状态查询 Agent 列表。
	 * @param status Agent 状态（如 published、draft 等）
	 * @return 符合状态的 Agent 列表
	 */
	List<Agent> findByStatus(String status);

	/**
	 * 根据关键字搜索 Agent。
	 * @param keyword 搜索关键字
	 * @return 匹配的 Agent 列表
	 */
	List<Agent> search(String keyword);

	/**
	 * 保存 Agent，根据是否存在 ID 判断新增或更新。
	 * @param agent 待保存的 Agent 对象
	 * @return 保存后的 Agent 对象（包含生成的 ID）
	 */
	Agent save(Agent agent);

	/**
	 * 根据主键 ID 删除 Agent，同时清理关联的向量数据和头像文件。
	 * @param id Agent 主键 ID
	 */
	void deleteById(Long id);

	/**
	 * 为指定 Agent 生成新的 API Key 并启用。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	Agent generateApiKey(Long id);

	/**
	 * 重置指定 Agent 的 API Key，等同于重新生成。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	Agent resetApiKey(Long id);

	/**
	 * 删除指定 Agent 的 API Key 并禁用。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	Agent deleteApiKey(Long id);

	/**
	 * 切换指定 Agent 的 API Key 启用状态。
	 * @param id Agent 主键 ID
	 * @param enabled 是否启用
	 * @return 更新后的 Agent 对象
	 */
	Agent toggleApiKey(Long id, boolean enabled);

	/**
	 * 获取指定 Agent 的 API Key 脱敏字符串。
	 * @param id Agent 主键 ID
	 * @return 脱敏后的 API Key 字符串，不存在时返回 null
	 */
	String getApiKeyMasked(Long id);

}
