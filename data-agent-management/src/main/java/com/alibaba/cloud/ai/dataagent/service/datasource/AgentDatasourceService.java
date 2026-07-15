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
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import java.util.List;

/**
 * Agent 数据源关联服务接口，管理 Agent 与数据源的绑定关系，包括关联的增删改查、 数据源启用/禁用切换以及数据表选择的更新。
 */
public interface AgentDatasourceService {

	/**
	 * 使用指定数据源为 Agent 初始化数据库 Schema 向量数据。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param tables 待初始化的数据表列表
	 * @return 初始化是否成功
	 */
	Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables);

	/**
	 * 获取 Agent 关联的全部数据源列表。
	 * @param agentId Agent 主键 ID
	 * @return Agent 关联的数据源列表
	 */
	List<AgentDatasource> getAgentDatasource(Long agentId);

	/**
	 * 获取 Agent 当前激活的数据源。一个 Agent 同时只能有一个激活的数据源。
	 * @param agentId Agent 主键 ID
	 * @return 当前激活的数据源关联对象
	 * @throws IllegalStateException 当 Agent 没有激活的数据源时抛出
	 */
	default AgentDatasource getCurrentAgentDatasource(Long agentId) {
		return getAgentDatasource(agentId).stream()
			.filter(a -> a.getIsActive() != 0)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Agent " + agentId + " has no active datasource"));
	}

	/**
	 * 将数据源添加到 Agent，同时禁用该 Agent 的其他数据源（一个 Agent 只能有一个激活的数据源）。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @return 关联后的数据源对象
	 */
	AgentDatasource addDatasourceToAgent(Long agentId, Integer datasourceId);

	/**
	 * 从 Agent 移除数据源关联。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 */
	void removeDatasourceFromAgent(Long agentId, Integer datasourceId);

	/**
	 * 切换 Agent 数据源的启用/禁用状态。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param isActive 是否激活
	 * @return 更新后的关联记录
	 */
	AgentDatasource toggleDatasourceForAgent(Long agentId, Integer datasourceId, Boolean isActive);

	/**
	 * 更新 Agent 数据源选中的数据表列表。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param tables 选中的数据表列表
	 */
	void updateDatasourceTables(Long agentId, Integer datasourceId, List<String> tables);

}
