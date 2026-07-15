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

import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 用户提示词配置 Mapper，操作 {@code user_prompt_config} 表。
 * <p>
 * 管理按提示词类型（prompt_type）分类的系统提示词配置，支持按类型/智能体查询、启用/禁用切换、 互斥启用及增删改。
 * </p>
 */
@Mapper
public interface UserPromptConfigMapper {

	/**
	 * 根据提示词类型查询配置列表（可选按智能体过滤），按更新时间倒序返回。
	 * @param promptType 提示词类型
	 * @param agentId 智能体 ID（可为 {@code null}）
	 * @return 配置列表
	 */
	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY update_time DESC
			</script>
			""")
	List<UserPromptConfig> selectByPromptType(@Param("promptType") String promptType, @Param("agentId") Long agentId);

	/**
	 * 根据提示词类型查询已启用的配置（可选按智能体过滤），至多返回一条。
	 * @param promptType 提示词类型
	 * @param agentId 智能体 ID（可为 {@code null}）
	 * @return 已启用的配置；不存在返回 {@code null}
	 */
	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE prompt_type = #{promptType}
			  AND enabled = 1
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			LIMIT 1
			</script>
			""")
	UserPromptConfig selectActiveByPromptType(@Param("promptType") String promptType, @Param("agentId") Long agentId);

	/**
	 * 禁用指定类型（可选按智能体）下的全部配置。
	 * @param promptType 提示词类型
	 * @param agentId 智能体 ID（可为 {@code null}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE user_prompt_config
			SET enabled = 0
			WHERE prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			</script>
			""")
	int disableAllByPromptType(@Param("promptType") String promptType, @Param("agentId") Long agentId);

	/**
	 * 启用指定配置。
	 * @param id 配置 ID
	 * @return 受影响行数
	 */
	@Update("UPDATE user_prompt_config SET enabled = 1 WHERE id = #{id}")
	int enableById(@Param("id") String id);

	/**
	 * 禁用指定配置。
	 * @param id 配置 ID
	 * @return 受影响行数
	 */
	@Update("UPDATE user_prompt_config SET enabled = 0 WHERE id = #{id}")
	int disableById(@Param("id") String id);

	/**
	 * 根据主键查询配置。
	 * @param id 配置 ID
	 * @return 配置；不存在返回 {@code null}
	 */
	@Select("SELECT * FROM user_prompt_config WHERE id = #{id}")
	UserPromptConfig selectById(String id);

	/**
	 * 根据主键动态更新配置（仅更新非空字段），并刷新 {@code update_time}。
	 * @param config 配置实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE user_prompt_config
			<set>
			  <if test='name != null'>name = #{name},</if>
			  <if test='promptType != null'>prompt_type = #{promptType},</if>
			  <if test='agentId != null'>agent_id = #{agentId},</if>
			  <if test='systemPrompt != null'>system_prompt = #{systemPrompt},</if>
			  <if test='enabled != null'>enabled = #{enabled},</if>
			  <if test='description != null'>description = #{description},</if>
			  <if test='priority != null'>priority = #{priority},</if>
			  <if test='displayOrder != null'>display_order = #{displayOrder},</if>
			  update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(UserPromptConfig config);

	/**
	 * 新增配置记录。
	 * @param config 配置实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO user_prompt_config
			(id, name, prompt_type, agent_id, system_prompt, enabled, description, priority, display_order, create_time, update_time, creator)
			VALUES (#{id}, #{name}, #{promptType}, #{agentId}, #{systemPrompt}, #{enabled}, #{description}, #{priority}, #{displayOrder}, NOW(), NOW(), #{creator})
			""")
	int insert(UserPromptConfig config);

	/**
	 * 根据提示词类型查询已启用的配置列表（可选按智能体过滤），按优先级、展示顺序、更新时间排序。
	 * @param promptType 提示词类型
	 * @param agentId 智能体 ID（可为 {@code null}）
	 * @return 已启用的配置列表
	 */
	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE prompt_type = #{promptType}
			  AND enabled = true
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order, update_time DESC
			</script>
			""")
	List<UserPromptConfig> getActiveConfigsByType(@Param("promptType") String promptType,
			@Param("agentId") Long agentId);

	/**
	 * 根据提示词类型查询全部配置（可选按智能体过滤），按优先级、展示顺序、更新时间排序。
	 * @param promptType 提示词类型
	 * @param agentId 智能体 ID（可为 {@code null}）
	 * @return 配置列表
	 */
	@Select("""
			<script>
			SELECT * FROM user_prompt_config
			WHERE prompt_type = #{promptType}
			<if test='agentId != null'> AND agent_id = #{agentId}</if>
			ORDER BY priority DESC, display_order, update_time DESC
			</script>
			""")
	List<UserPromptConfig> getConfigsByType(@Param("promptType") String promptType, @Param("agentId") Long agentId);

	/**
	 * 查询全部配置，按优先级、展示顺序、更新时间排序。
	 * @return 配置列表
	 */
	@Select("SELECT * FROM user_prompt_config ORDER BY priority DESC, display_order, update_time DESC")
	List<UserPromptConfig> selectAll();

	/**
	 * 根据主键删除配置。
	 * @param id 配置 ID
	 * @return 受影响行数
	 */
	@Delete("DELETE FROM user_prompt_config WHERE id = #{id}")
	int deleteById(String id);

}
