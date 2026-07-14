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

import com.alibaba.cloud.ai.dataagent.entity.AgentPresetQuestion;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 智能体预设问题 Mapper，操作 {@code agent_preset_question} 表。
 * <p>
 * 管理智能体对外展示的预设引导问题，支持按智能体查询、排序维护及增删改。
 * </p>
 */
@Mapper
public interface AgentPresetQuestionMapper {

	/**
	 * 根据智能体 ID 查询启用的预设问题，按排序值与 ID 升序返回。
	 * @param agentId 智能体 ID
	 * @return 启用的预设问题列表
	 */
	@Select("""
			SELECT * FROM agent_preset_question
			         WHERE agent_id = #{agentId} AND is_active = 1
			ORDER BY sort_order ASC, id ASC
			""")
	List<AgentPresetQuestion> selectByAgentId(@Param("agentId") Long agentId);

	/**
	 * 根据智能体 ID 查询全部预设问题（含禁用项），按排序值与 ID 升序返回。
	 * @param agentId 智能体 ID
	 * @return 全部预设问题列表
	 */
	@Select("""
			SELECT * FROM agent_preset_question
			         WHERE agent_id = #{agentId}
			ORDER BY sort_order ASC, id ASC
			""")
	List<AgentPresetQuestion> selectAllByAgentId(@Param("agentId") Long agentId);

	/**
	 * 根据主键查询预设问题。
	 * @param id 预设问题 ID
	 * @return 预设问题；不存在返回 {@code null}
	 */
	@Select("""
			SELECT * FROM agent_preset_question WHERE id = #{id}
			""")
	AgentPresetQuestion selectById(@Param("id") Long id);

	/**
	 * 新增预设问题，并将自增主键回填到入参对象的 {@code id} 字段。
	 * @param question 预设问题实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO agent_preset_question (agent_id, question, sort_order, is_active, create_time, update_time)
			VALUES (#{agentId}, #{question}, #{sortOrder}, #{isActive}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AgentPresetQuestion question);

	/**
	 * 根据主键动态更新预设问题（仅更新非空字段），并刷新 {@code update_time}。
	 * @param question 预设问题实体（需携带 {@code id}）
	 * @return 受影响行数
	 */
	@Update("""
			<script>
			UPDATE agent_preset_question
			<set>
				<if test="question != null">question = #{question},</if>
				<if test="sortOrder != null">sort_order = #{sortOrder},</if>
				<if test="isActive != null">is_active = #{isActive},</if>
				update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int update(AgentPresetQuestion question);

	/**
	 * 根据主键删除预设问题。
	 * @param id 预设问题 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM agent_preset_question WHERE id = #{id}
			""")
	int deleteById(@Param("id") Long id);

	/**
	 * 根据智能体 ID 删除其下全部预设问题。
	 * @param agentId 智能体 ID
	 * @return 受影响行数
	 */
	@Delete("""
			DELETE FROM agent_preset_question WHERE agent_id = #{agentId}
			""")
	int deleteByAgentId(@Param("agentId") Long agentId);

}
