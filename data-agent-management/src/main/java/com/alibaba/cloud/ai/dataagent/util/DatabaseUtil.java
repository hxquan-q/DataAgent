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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AccessorFactory;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 数据库处理工具类。
 * <p>
 * 基于当前 Agent 启用的数据源，构建数据库配置（{@link DbConfigBO}）并获取对应的数据库访问器（ {@link Accessor}），供下游节点统一使用。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class DatabaseUtil {

	private final AccessorFactory accessorFactory;

	private final AgentDatasourceService agentDatasourceService;

	private final DatasourceService datasourceService;

	/**
	 * 获取指定 Agent 当前启用的数据源对应的数据库配置。
	 * @param agentId Agent 主键
	 * @return 数据库配置信息
	 */
	public DbConfigBO getAgentDbConfig(Long agentId) {
		log.info("Getting datasource config for agent: {}", agentId);

		// 获取该 Agent 当前启用的数据源
		AgentDatasource activeDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);
		// 将数据源实体转换为数据库配置
		DbConfigBO dbConfig = datasourceService.getDbConfig(activeDatasource.getDatasource());
		log.info("Successfully created DbConfig for agent {}: url={}, schema={}, type={}", agentId, dbConfig.getUrl(),
				dbConfig.getSchema(), dbConfig.getDialectType());

		return dbConfig;
	}

	/**
	 * 获取指定 Agent 当前启用数据源对应的数据库访问器。
	 * @param agentId Agent 主键
	 * @return 数据库访问器实例
	 */
	public Accessor getAgentAccessor(Long agentId) {
		DbConfigBO dbConfig = getAgentDbConfig(agentId);
		return accessorFactory.getAccessorByDbConfig(dbConfig);
	}

}
