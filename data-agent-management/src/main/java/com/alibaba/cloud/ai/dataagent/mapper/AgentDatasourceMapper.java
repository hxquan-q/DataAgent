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
package com.alibaba.cloud.ai.dataagent.mapper;

import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 智能体与数据源关联关系 Mapper，操作 {@code agent_datasource} 表。
 * <p>
 * 维护智能体（Agent）与数据源（Datasource）之间的多对多绑定关系，包括关联查询、启停、删除等操作。
 * </p>
 */
@Mapper
public interface AgentDatasourceMapper {

	/**
	 * 根据智能体 ID 查询其关联的数据源列表（联表查询，携带数据源明细字段）。
	 * <p>
	 * SQL：左联 {@code datasource} 表，返回每个关联记录及其对应的数据源连接信息。
	 * </p>
	 * @param agentId 智能体 ID
	 * @return 关联数据源列表，按创建时间倒序
	 */
	@Select("SELECT ad.*, d.name, d.type, d.host, d.port, d.database_name, "
			+ "d.connection_url, d.username, d.password, d.status, d.test_status, d.description "
			+ "FROM agent_datasource ad " + "LEFT JOIN datasource d ON ad.datasource_id = d.id "
			+ "WHERE ad.agent_id = #{agentId} " + "ORDER BY ad.create_time DESC")
	List<AgentDatasource> selectByAgentIdWithDatasource(@Param("agentId") Long agentId);

	/**
	 * 根据智能体 ID 查询其关联的数据源（仅关联表字段）。
	 * @param agentId 智能体 ID
	 * @return 关联记录列表，按创建时间倒序
	 */
	@Select("SELECT * FROM agent_datasource WHERE agent_id = #{agentId} ORDER BY create_time DESC")
	List<AgentDatasource> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * 根据智能体 ID 查询当前启用的数据源 ID。
	 * @param agentId 智能体 ID
	 * @return 启用状态的数据源 ID；若不存在返回 {@code null}
	 */
	@Select("SELECT datasource_id FROM agent_datasource WHERE agent_id = #{agentId} AND is_active = 1")
	Integer selectActiveDatasourceIdByAgentId(@Param("agentId") Long agentId);

	/**
	 * 根据智能体 ID 与数据源 ID 查询单条关联记录。
	 * @param agentId 智能体 ID
	 * @param datasourceId 数据源 ID
	 * @return 关联记录；不存在返回 {@code null}
	 */
	@Select("SELECT * FROM agent_datasource WHERE agent_id = #{agentId} AND datasource_id = #{datasourceId}")
	AgentDatasource selectByAgentIdAndDatasourceId(@Param("agentId") Long agentId,
			@Param("datasourceId") Integer datasourceId);

	/**
	 * 禁用某智能体下的全部数据源关联。
	 * <p>
	 * SQL：将 {@code is_active} 置为 0。
	 * </p>
	 * @param agentId 智能体 ID
	 * @return 受影响行数
	 */
	@Update("UPDATE agent_datasource SET is_active = 0 WHERE agent_id = #{agentId}")
	int disableAllByAgentId(@Param("agentId") Long agentId);

	/**
	 * 统计某智能体启用的数据源数量（排除指定数据源）。
	 * @param agentId 智能体 ID
	 * @param excludeDatasourceId 需要排除的数据源 ID
	 * @return 启用状态的数据源数量
	 */
	@Select("SELECT COUNT(*) FROM agent_datasource WHERE agent_id = #{agentId} AND is_active = 1 AND datasource_id != #{excludeDatasourceId}")
	int countActiveByAgentIdExcluding(@Param("agentId") Long agentId,
			@Param("excludeDatasourceId") Integer excludeDatasourceId);

	/**
	 * 根据数据源 ID 删除其与所有智能体的关联记录。
	 * @param datasourceId 数据源 ID
	 * @return 受影响行数
	 */
	@Delete("DELETE FROM agent_datasource WHERE datasource_id = #{datasourceId}")
	int deleteAllByDatasourceId(@Param("datasourceId") Integer datasourceId);

	/**
	 * 新建智能体与数据源的关联记录，并默认置为启用状态（{@code is_active = 1}）。
	 * @param agentId 智能体 ID
	 * @param datasourceId 数据源 ID
	 * @return 受影响行数
	 */
	@Insert("INSERT INTO agent_datasource (agent_id, datasource_id, is_active) VALUES (#{agentId}, #{datasourceId}, 1)")
	int createNewRelationEnabled(@Param("agentId") Long agentId, @Param("datasourceId") Integer datasourceId);

	/**
	 * 更新指定智能体与数据源关联的启用状态。
	 * @param agentId 智能体 ID
	 * @param datasourceId 数据源 ID
	 * @param isActive 启用状态：1 启用、0 禁用
	 * @return 受影响行数
	 */
	@Update("UPDATE agent_datasource SET is_active = #{isActive} WHERE agent_id = #{agentId} AND datasource_id = #{datasourceId}")
	int updateRelation(@Param("agentId") Long agentId, @Param("datasourceId") Integer datasourceId,
			@Param("isActive") Integer isActive);

	/**
	 * 启用指定智能体与数据源的关联（{@link #updateRelation} 的便捷方法）。
	 * @param agentId 智能体 ID
	 * @param datasourceId 数据源 ID
	 * @return 受影响行数
	 */
	default int enableRelation(Long agentId, Integer datasourceId) {
		return updateRelation(agentId, datasourceId, 1);
	}

	/**
	 * 删除指定智能体与数据源的关联记录。
	 * @param agentId 智能体 ID
	 * @param datasourceId 数据源 ID
	 * @return 受影响行数
	 */
	@Delete("DELETE FROM agent_datasource WHERE agent_id = #{agentId} AND datasource_id = #{datasourceId}")
	int removeRelation(@Param("agentId") Long agentId, @Param("datasourceId") Integer datasourceId);

}
