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
package com.alibaba.cloud.ai.dataagent.service.datasource.impl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SchemaInitRequest;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceMapper;
import com.alibaba.cloud.ai.dataagent.mapper.AgentDatasourceTablesMapper;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.datasource.DatasourceService;
import com.alibaba.cloud.ai.dataagent.service.schema.SchemaService;
import java.util.List;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

/**
 * Agent 数据源关联服务实现类，管理 Agent 与数据源的绑定关系，
 * 包括 Schema 初始化、关联查询、数据源启用/禁用切换和数据表选择更新。
 */
@Slf4j
@Service
@AllArgsConstructor
public class AgentDatasourceServiceImpl implements AgentDatasourceService {

	/** 数据源管理服务 */
	private final DatasourceService datasourceService;

	/** Schema 初始化服务 */
	private final SchemaService schemaService;

	/** Agent 数据源关联数据访问层 */
	private final AgentDatasourceMapper agentDatasourceMapper;

	/** Agent 数据源表选择数据访问层 */
	private final AgentDatasourceTablesMapper tablesMapper;

	/**
	 * 使用指定数据源为 Agent 初始化数据库 Schema 向量数据。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param tables 待初始化的数据表列表
	 * @return 初始化是否成功
	 */
	@Override
	public Boolean initializeSchemaForAgentWithDatasource(Long agentId, Integer datasourceId, List<String> tables) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		Assert.notNull(datasourceId, "Datasource ID cannot be null");
		Assert.notEmpty(tables, "Tables cannot be empty");
		try {
			String agentIdStr = String.valueOf(agentId);
			log.info("Initializing schema for agent: {} with datasource: {}, tables: {}", agentIdStr, datasourceId,
					tables);

			// 获取数据源信息
			Datasource datasource = datasourceService.getDatasourceById(datasourceId);
			if (datasource == null) {
				throw new RuntimeException("Datasource not found with id: " + datasourceId);
			}

			// 创建数据库配置
			DbConfigBO dbConfig = datasourceService.getDbConfig(datasource);

			// 创建 Schema 初始化请求
			SchemaInitRequest schemaInitRequest = new SchemaInitRequest();
			schemaInitRequest.setDbConfig(dbConfig);
			schemaInitRequest.setTables(tables);

			log.info("Created SchemaInitRequest for agent: {}, dbConfig: {}, tables: {}", agentIdStr, dbConfig, tables);

			// 调用 Schema 服务执行初始化
			return schemaService.schema(datasourceId, schemaInitRequest);

		}
		catch (Exception e) {
			log.error("Failed to initialize schema for agent: {} with datasource: {}", agentId, datasourceId, e);
			throw new RuntimeException("Failed to initialize schema for agent " + agentId + ": " + e.getMessage(), e);
		}
	}

	/**
	 * 获取 Agent 关联的全部数据源列表，包含数据源详情和选中的数据表。
	 * @param agentId Agent 主键 ID
	 * @return Agent 关联的数据源列表
	 */
	@Override
	public List<AgentDatasource> getAgentDatasource(Long agentId) {
		Assert.notNull(agentId, "Agent ID cannot be null");
		List<AgentDatasource> adentDatasources = agentDatasourceMapper.selectByAgentIdWithDatasource(agentId);

		// 手动填充数据源信息和选中的数据表（因为 MyBatis Plus 不直接支持复杂关联查询结果映射）
		for (AgentDatasource agentDatasource : adentDatasources) {
			if (agentDatasource.getDatasourceId() != null) {
				Datasource datasource = datasourceService.getDatasourceById(agentDatasource.getDatasourceId());
				agentDatasource.setDatasource(datasource);
			}
			// 获取选中的数据表
			int id = agentDatasource.getId();
			List<String> tables = tablesMapper.getAgentDatasourceTables(id);
			agentDatasource.setSelectTables(Optional.ofNullable(tables).orElse(List.of()));
		}

		return adentDatasources;
	}

	/**
	 * 将数据源添加到 Agent，同时禁用该 Agent 的其他数据源（一个 Agent 只能有一个激活的数据源）。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @return 关联后的数据源对象
	 */
	@Override
	@Transactional
	public AgentDatasource addDatasourceToAgent(Long agentId, Integer datasourceId) {
		// 先禁用该 Agent 的其他数据源（一个 Agent 只能有一个激活的数据源）
		agentDatasourceMapper.disableAllByAgentId(agentId);

		// 检查是否已存在关联关系
		AgentDatasource existing = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);

		AgentDatasource result;
		if (existing != null) {
			// 已存在则激活该关联
			agentDatasourceMapper.enableRelation(agentId, datasourceId);

			// 删除已有的表选择
			tablesMapper.removeAllTables(existing.getId());

			// 查询并返回更新后的关联
			result = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		}
		else {
			// 不存在则创建新的关联
			AgentDatasource agentDatasource = new AgentDatasource(agentId, datasourceId);
			agentDatasource.setIsActive(1);
			agentDatasourceMapper.createNewRelationEnabled(agentId, datasourceId);
			result = agentDatasource;
		}
		result.setSelectTables(List.of());
		return result;
	}

	/**
	 * 从 Agent 移除数据源关联。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 */
	@Override
	public void removeDatasourceFromAgent(Long agentId, Integer datasourceId) {
		agentDatasourceMapper.removeRelation(agentId, datasourceId);
	}

	/**
	 * 切换 Agent 数据源的启用/禁用状态，启用时检查是否已有其他激活的数据源。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param isActive 是否激活
	 * @return 更新后的关联记录
	 */
	@Override
	public AgentDatasource toggleDatasourceForAgent(Long agentId, Integer datasourceId, Boolean isActive) {
		// 启用数据源时，先检查是否已有其他激活的数据源
		if (isActive) {
			int activeCount = agentDatasourceMapper.countActiveByAgentIdExcluding(agentId, datasourceId);
			if (activeCount > 0) {
				throw new RuntimeException("同一智能体下只能启用一个数据源，请先禁用其他数据源后再启用此数据源");
			}
		}

		// 更新数据源状态
		int updated = agentDatasourceMapper.updateRelation(agentId, datasourceId, isActive ? 1 : 0);

		if (updated == 0) {
			throw new RuntimeException("未找到相关的数据源关联记录");
		}

		// 返回更新后的关联记录
		return agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
	}

	/**
	 * 更新 Agent 数据源选中的数据表列表。
	 * @param agentId Agent 主键 ID
	 * @param datasourceId 数据源主键 ID
	 * @param tables 选中的数据表列表
	 */
	@Override
	@Transactional
	public void updateDatasourceTables(Long agentId, Integer datasourceId, List<String> tables) {
		if (agentId == null || datasourceId == null || tables == null) {
			throw new IllegalArgumentException("参数不能为空");
		}
		AgentDatasource datasource = agentDatasourceMapper.selectByAgentIdAndDatasourceId(agentId, datasourceId);
		if (datasource == null) {
			throw new IllegalArgumentException("未找到对应的数据源关联记录");
		}
		// 表列表为空时移除全部表选择，否则更新为指定表列表
		if (tables.isEmpty()) {
			tablesMapper.removeAllTables(datasource.getId());
		}
		else {
			tablesMapper.updateAgentDatasourceTables(datasource.getId(), tables);
		}
	}

}
