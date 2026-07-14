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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 智能体 Mapper，操作 {@code agent} 表。
 * <p>
 * 提供智能体（Agent）的基础 CRUD、按状态/关键词查询、API Key 维护与删除等操作。
 * </p>
 */
@Mapper
public interface AgentMapper {

	/**
	 * 查询全部智能体，按创建时间倒序返回。
	 * @return 智能体列表
	 */
	@Select("""
			SELECT * FROM agent ORDER BY create_time DESC
			""")
	List<Agent> findAll();

	/**
	 * 根据主键查询智能体。
	 * @param id 智能体 ID
	 * @return 智能体；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM agent WHERE id = #{id}
			""")
	Agent findById(Long id);

	/**
	 * 根据状态查询智能体列表，按创建时间倒序返回。
	 * @param status 智能体状态
	 * @return 智能体列表
	 */
	@Select("""
			SELECT * FROM agent WHERE status = #{status} ORDER BY create_time DESC
			""")
	List<Agent> findByStatus(String status);

	/**
	 * 按关键词检索智能体（匹配名称、描述、标签），按创建时间倒序返回。
	 * @param keyword 关键词
	 * @return 匹配的智能体列表
	 */
	@Select("""
			SELECT * FROM agent
			WHERE (name LIKE CONCAT('%', #{keyword}, '%')
				   OR description LIKE CONCAT('%', #{keyword}, '%')
				   OR tags LIKE CONCAT('%', #{keyword}, '%'))
			ORDER BY create_time DESC
			""")
	List<Agent> searchByKeyword(@Param("keyword") String keyword);

	/**
	 * 按状态与关键词组合条件查询智能体（条件均可选），按创建时间倒序返回。
	 * @param status 智能体状态（可为 {@code null}）
	 * @param keyword 关键词（可为 {@code null}）
	 * @return 匹配的智能体列表
	 */
	@Select("""
			<script>
				SELECT * FROM agent
				<where>
					<if test='status != null and status != ""'>
						AND status = #{status}
					</if>
					<if test='keyword != null and keyword != ""'>
						AND (name LIKE CONCAT('%', #{keyword}, '%')
							 OR description LIKE CONCAT('%', #{keyword}, '%')
							 OR tags LIKE CONCAT('%', #{keyword}, '%'))
					</if>
				</where>
				ORDER BY create_time DESC
			</script>
			""")
	List<Agent> findByConditions(@Param("status") String status, @Param("keyword") String keyword);

	/**
	 * 新增智能体，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param agent 智能体实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO agent (name, description, avatar, status, api_key, api_key_enabled, prompt, category, admin_id, tags, create_time, update_time)
			VALUES (#{name}, #{description}, #{avatar}, #{status}, #{apiKey}, #{apiKeyEnabled}, #{prompt}, #{category}, #{adminId}, #{tags}, #{createTime}, #{updateTime})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Agent agent);

	/**
	 * 根据主键动态更新智能体（仅更新非空字段），并刷新 {@code update_time}。
	 * @param agent 智能体实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			          UPDATE agent
			          <trim prefix="SET" suffixOverrides=",">
			            <if test='name != null'>name = #{name},</if>
			            <if test='description != null'>description = #{description},</if>
			            <if test='avatar != null'>avatar = #{avatar},</if>
			            <if test='status != null'>status = #{status},</if>
			            <if test='apiKey != null'>api_key = #{apiKey},</if>
			            <if test='apiKeyEnabled != null'>api_key_enabled = #{apiKeyEnabled},</if>
			            <if test='prompt != null'>prompt = #{prompt},</if>
			            <if test='category != null'>category = #{category},</if>
			            <if test='adminId != null'>admin_id = #{adminId},</if>
			            <if test='tags != null'>tags = #{tags},</if>
			            update_time = NOW()
			          </trim>
			          WHERE id = #{id}
			</script>
			""")
	int updateById(Agent agent);

	/**
	 * 更新指定智能体的 API Key 及其启用状态。
	 * @param id 智能体 ID
	 * @param apiKey 新的 API Key
	 * @param apiKeyEnabled API Key 启用状态：1 启用、0 禁用
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE agent
			SET api_key = #{apiKey}, api_key_enabled = #{apiKeyEnabled}, update_time = NOW()
			WHERE id = #{id}
			""")
	int updateApiKey(@Param("id") Long id, @Param("apiKey") String apiKey,
			@Param("apiKeyEnabled") Integer apiKeyEnabled);

	/**
	 * 切换指定智能体的 API Key 启用状态。
	 * @param id 智能体 ID
	 * @param enabled 启用状态：1 启用、0 禁用
	 * @return 受影响行数
	 */
	@Update("""
			UPDATE agent
			SET api_key_enabled = #{enabled}, update_time = NOW()
			WHERE id = #{id}
			""")
	int toggleApiKey(@Param("id") Long id, @Param("enabled") Integer enabled);

	/**
	 * 根据主键物理删除智能体。
	 * @param id 智能体 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM agent WHERE id = #{id}
			""")
	int deleteById(Long id);

}
