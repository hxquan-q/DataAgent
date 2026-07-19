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

import com.alibaba.cloud.ai.dataagent.entity.SemanticModel;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 语义模型 Mapper，操作 {@code semantic_model} 表。
 * <p>
 * 管理数据源字段的语义层信息（业务名、同义词、业务描述、列注释、状态），支持按智能体/数据源查询、 关键词检索、启用/禁用及增删改。
 * </p>
 */
@Mapper
public interface SemanticModelMapper {

	/**
	 * 查询全部语义模型，按创建时间倒序返回。
	 * @return 语义模型列表
	 */
	@Select("SELECT * FROM semantic_model ORDER BY created_time DESC")
	List<SemanticModel> selectAll();

	/**
	 * 根据智能体 ID 查询语义模型列表，按创建时间倒序返回。
	 * @param agentId 智能体 ID
	 * @return 语义模型列表
	 */
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			ORDER BY created_time DESC
			""")
	List<SemanticModel> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * 根据主键查询语义模型。
	 * @param id 语义模型 ID
	 * @return 语义模型；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM semantic_model
			WHERE id = #{id}
			""")
	SemanticModel selectById(@Param("id") Long id);

	/**
	 * 按关键词检索语义模型（匹配列名、业务名、业务描述、同义词），按创建时间倒序返回。
	 * @param keyword 关键词
	 * @return 匹配的语义模型列表
	 */
	@Select("""
			SELECT * FROM semantic_model
			WHERE column_name LIKE CONCAT('%', #{keyword}, '%')
			   OR business_name LIKE CONCAT('%', #{keyword}, '%')
			   OR business_description LIKE CONCAT('%', #{keyword}, '%')
			   OR synonyms LIKE CONCAT('%', #{keyword}, '%')
			ORDER BY created_time DESC
			""")
	List<SemanticModel> searchByKeyword(@Param("keyword") String keyword);

	/**
	 * 启用指定语义模型字段（将 {@code status} 置为 1）。
	 * @param id 语义模型 ID
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE semantic_model
			SET status = 1
			WHERE id = #{id}
			""")
	int enableById(@Param("id") Long id);

	/**
	 * 禁用指定语义模型字段（将 {@code status} 置为 0）。
	 * @param id 语义模型 ID
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE semantic_model
			SET status = 0
			WHERE id = #{id}
			""")
	int disableById(@Param("id") Long id);

	/**
	 * 根据智能体 ID 查询已启用（{@code status != 0}）的语义模型列表，按创建时间倒序返回。
	 * @param agentId 智能体 ID
	 * @return 已启用的语义模型列表
	 */
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			  AND status != 0
			ORDER BY created_time DESC
			""")
	List<SemanticModel> selectEnabledByAgentId(@Param("agentId") Long agentId);

	/**
	 * 新增语义模型，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param model 语义模型实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO semantic_model
			(agent_id, datasource_id, table_name, column_name, business_name, synonyms, business_description, column_comment, data_type, created_time, updated_time, status)
			VALUES
			(#{agentId}, #{datasourceId}, #{tableName}, #{columnName}, #{businessName}, #{synonyms}, #{businessDescription}, #{columnComment}, #{dataType}, NOW(), NOW(), #{status})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticModel model);

	/**
	 * 根据主键动态更新语义模型（仅更新非空字段），并刷新 {@code updated_time}。
	 * @param model 语义模型实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE semantic_model
			<set>
			    <if test="agentId != null">agent_id = #{agentId},</if>
			    <if test="datasourceId != null">datasource_id = #{datasourceId},</if>
			    <if test="tableName != null">table_name = #{tableName},</if>
				<if test="columnName != null">column_name = #{columnName},</if>
				<if test="businessName != null">business_name = #{businessName},</if>
				<if test="synonyms != null">synonyms = #{synonyms},</if>
				<if test="businessDescription != null">business_description = #{businessDescription},</if>
				<if test="columnComment != null">column_comment = #{columnComment},</if>
				<if test="dataType != null">data_type = #{dataType},</if>
				<if test="status != null">status = #{status},</if>
				updated_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticModel model);

	/**
	 * 根据主键物理删除语义模型。
	 * @param id 语义模型 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM semantic_model
			WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * 根据数据源 ID、启用状态与表名集合查询语义模型，按创建时间倒序返回。
	 * @param datasourceId 数据源 ID
	 * @param tableNames 表名集合
	 * @return 匹配的语义模型列表
	 */
	@Select("""
			<script>
			SELECT * FROM semantic_model
			WHERE datasource_id = #{datasourceId}
			  AND status = 1
			  AND table_name IN
			  <foreach item='tableName' index='index' collection='tableNames' open='(' separator=',' close=')'>
			    #{tableName}
			  </foreach>
			ORDER BY created_time DESC
			</script>
			""")
	List<SemanticModel> selectByDatasourceIdAndTableNames(@Param("datasourceId") Integer datasourceId,
			@Param("tableNames") List<String> tableNames);

	/**
	 * 根据智能体 ID、表名与列名查询单条语义模型（至多一条）。
	 * @param agentId 智能体 ID
	 * @param tableName 表名
	 * @param columnName 列名
	 * @return 语义模型；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM semantic_model
			WHERE agent_id = #{agentId}
			  AND table_name = #{tableName}
			  AND column_name = #{columnName}
			LIMIT 1
			""")
	SemanticModel selectByAgentIdAndTableNameAndColumnName(@Param("agentId") Integer agentId,
			@Param("tableName") String tableName, @Param("columnName") String columnName);

}
