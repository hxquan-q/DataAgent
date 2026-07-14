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

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 智能体数据源可用表 Mapper，操作 {@code agent_datasource_tables} 表。
 * <p>
 * 维护某条智能体-数据源关联记录下被选中的数据库表清单，支持查询、批量插入、过期清理及整体替换。
 * </p>
 */
@Mapper
public interface AgentDatasourceTablesMapper {

	/**
	 * 查询当前智能体数据源已选择的所有表名。
	 * @param agentDatasourceId 智能体数据源关联记录 ID
	 * @return 表名列表
	 */
	@Select("select table_name from agent_datasource_tables where agent_datasource_id = #{agentDatasourceId}")
	List<String> getAgentDatasourceTables(@Param("agentDatasourceId") int agentDatasourceId);

	/**
	 * 删除当前列表中不存在的表（即清理过期表）。
	 * <p>SQL：删除不在传入 {@code tables} 集合中的记录；当集合为空时清空该关联下的全部表。</p>
	 * @param agentDatasourceId 智能体数据源关联记录 ID
	 * @param tables 保留的表名集合
	 * @return 受影响行数
	 */
	@Delete("<script>" + "DELETE FROM agent_datasource_tables WHERE agent_datasource_id = #{agentDatasourceId}"
			+ "<if test='tables != null and tables.size() > 0'>" + " AND table_name NOT IN ("
			+ "<foreach collection='tables' item='table' separator=','>#{table}</foreach>" + ")" + "</if>"
			+ "</script>")
	int removeExpireTables(@Param("agentDatasourceId") int agentDatasourceId, @Param("tables") List<String> tables);

	/**
	 * 删除当前智能体数据源下的全部表。
	 * @param agentDatasourceId 智能体数据源关联记录 ID
	 * @return 受影响行数
	 */
	@Delete("DELETE FROM agent_datasource_tables WHERE agent_datasource_id = #{agentDatasourceId}")
	int removeAllTables(@Param("agentDatasourceId") int agentDatasourceId);

	/**
	 * 批量插入用户选择的表（已存在的记录会被忽略）。
	 * <p>SQL：使用 {@code INSERT IGNORE} 避免唯一键冲突。</p>
	 * @param agentDatasourceId 智能体数据源关联记录 ID
	 * @param tables 待插入的表名集合
	 * @return 受影响行数
	 */
	@Insert("<script>" + "INSERT IGNORE INTO agent_datasource_tables (agent_datasource_id, table_name) VALUES "
			+ "<if test='tables != null and tables.size() > 0'>"
			+ "<foreach collection='tables' item='table' separator=','>" + "(#{agentDatasourceId}, #{table})"
			+ "</foreach>" + "</if>" + "</script>")
	int insertNewTables(@Param("agentDatasourceId") int agentDatasourceId, @Param("tables") List<String> tables);

	/**
	 * 更新用户的选择（先清理过期表，再插入新表）。
	 * <p>{@code tables} 不能为空，否则抛出 {@link IllegalArgumentException}。</p>
	 * @param agentDatasourceId 智能体数据源关联记录 ID
	 * @param tables 用户最终选择的表名集合
	 * @return 删除与插入的受影响行数之和
	 */
	default int updateAgentDatasourceTables(int agentDatasourceId, List<String> tables) {
		if (tables.isEmpty()) {
			throw new IllegalArgumentException("tables cannot be empty");
		}
		int deleteCount = removeExpireTables(agentDatasourceId, tables);
		int insertCount = insertNewTables(agentDatasourceId, tables);
		return deleteCount + insertCount;
	}

}
