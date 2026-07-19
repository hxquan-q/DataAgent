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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import java.util.List;

/**
 * 数据源管理服务接口，提供数据源的增删改查、连接测试、表与字段查询以及逻辑外键管理能力。
 */
public interface DatasourceService {

	/**
	 * 获取全部数据源列表。
	 * @return 数据源列表
	 */
	List<Datasource> getAllDatasource();

	/**
	 * 根据状态获取数据源列表。
	 * @param status 数据源状态
	 * @return 符合状态的数据源列表
	 */
	List<Datasource> getDatasourceByStatus(String status);

	/**
	 * 根据类型获取数据源列表。
	 * @param type 数据源类型
	 * @return 符合类型的数据源列表
	 */
	List<Datasource> getDatasourceByType(String type);

	/**
	 * 根据主键 ID 获取数据源详情。
	 * @param id 数据源主键 ID
	 * @return 数据源对象，不存在时返回 null
	 */
	Datasource getDatasourceById(Integer id);

	/**
	 * 创建数据源。
	 * @param datasource 待创建的数据源对象
	 * @return 创建后的数据源对象
	 */
	Datasource createDatasource(Datasource datasource);

	/**
	 * 更新数据源。
	 * @param id 数据源主键 ID
	 * @param datasource 待更新的数据源对象
	 * @return 更新后的数据源对象
	 */
	Datasource updateDatasource(Integer id, Datasource datasource);

	/**
	 * 删除数据源，同时清理与 Agent 的关联关系。
	 * @param id 数据源主键 ID
	 */
	void deleteDatasource(Integer id);

	/**
	 * 更新数据源的测试状态。
	 * @param id 数据源主键 ID
	 * @param testStatus 测试状态（success、failed、unknown）
	 */
	void updateTestStatus(Integer id, String testStatus);

	/**
	 * 测试数据源连接是否可用。
	 * @param id 数据源主键 ID
	 * @return 连接是否成功
	 */
	boolean testConnection(Integer id);

	/**
	 * 获取与 Agent 关联的数据源列表。
	 * @param agentId Agent 主键 ID
	 * @return Agent 关联的数据源列表
	 */
	// 应该使用 AgentDatasourceService 中的方法
	@Deprecated
	List<AgentDatasource> getAgentDatasource(Long agentId);

	/**
	 * 获取数据源的表列表。
	 * @param datasourceId 数据源主键 ID
	 * @return 表名列表
	 * @throws Exception 查询过程中发生异常时抛出
	 */
	List<String> getDatasourceTables(Integer datasourceId) throws Exception;

	/**
	 * 获取数据源表的字段列表。
	 * @param datasourceId 数据源主键 ID
	 * @param tableName 表名
	 * @return 字段名列表
	 * @throws Exception 查询过程中发生异常时抛出
	 */
	List<String> getTableColumns(Integer datasourceId, String tableName) throws Exception;

	/**
	 * 根据数据源实体构建数据库配置对象。
	 * @param datasource 数据源实体
	 * @return 数据库配置对象
	 */
	DbConfigBO getDbConfig(Datasource datasource);

	/**
	 * 获取数据源的逻辑外键列表。
	 * @param datasourceId 数据源主键 ID
	 * @return 逻辑外键列表
	 */
	List<LogicalRelation> getLogicalRelations(Integer datasourceId);

	/**
	 * 添加逻辑外键。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelation 待添加的逻辑外键对象
	 * @return 添加后的逻辑外键对象
	 */
	LogicalRelation addLogicalRelation(Integer datasourceId, LogicalRelation logicalRelation);

	/**
	 * 更新逻辑外键。
	 * @param datasourceId 数据源主键 ID
	 * @param relationId 逻辑外键主键 ID
	 * @param logicalRelation 待更新的逻辑外键对象
	 * @return 更新后的逻辑外键对象
	 */
	LogicalRelation updateLogicalRelation(Integer datasourceId, Integer relationId, LogicalRelation logicalRelation);

	/**
	 * 删除逻辑外键。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelationId 逻辑外键主键 ID
	 */
	void deleteLogicalRelation(Integer datasourceId, Integer logicalRelationId);

	/**
	 * 批量保存逻辑外键（替换现有的所有外键）。
	 * @param datasourceId 数据源主键 ID
	 * @param logicalRelations 待保存的逻辑外键列表
	 * @return 保存后的逻辑外键列表
	 */
	List<LogicalRelation> saveLogicalRelations(Integer datasourceId, List<LogicalRelation> logicalRelations);

}
