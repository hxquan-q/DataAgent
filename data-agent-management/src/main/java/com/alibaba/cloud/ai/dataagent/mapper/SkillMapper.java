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

import com.alibaba.cloud.ai.dataagent.entity.Skill;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 技能数据访问层。 */
@Mapper
public interface SkillMapper {

	/** 按主键查询技能。 */
	@Select("SELECT * FROM skill WHERE id = #{id}")
	Skill selectById(@Param("id") Long id);

	/** 分页查询技能。 */
	@Select("""
			<script>
			SELECT * FROM skill
			<where>
			  <if test="scope != null and scope != ''">scope = #{scope}</if>
			  <if test="keyword != null and keyword != ''">
			    AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))
			  </if>
			</where>
			ORDER BY priority DESC, display_order, id DESC
			LIMIT #{pageSize} OFFSET #{offset}
			</script>
			""")
	List<Skill> selectPage(@Param("scope") String scope, @Param("keyword") String keyword, @Param("offset") int offset,
			@Param("pageSize") int pageSize);

	/** 统计技能数量。 */
	@Select("""
			<script>
			SELECT COUNT(*) FROM skill
			<where>
			  <if test="scope != null and scope != ''">scope = #{scope}</if>
			  <if test="keyword != null and keyword != ''">
			    AND (name LIKE CONCAT('%', #{keyword}, '%') OR description LIKE CONCAT('%', #{keyword}, '%'))
			  </if>
			</where>
			</script>
			""")
	long count(@Param("scope") String scope, @Param("keyword") String keyword);

	/** 新增技能并回填主键。 */
	@Insert("""
			INSERT INTO skill
			(name, description, scope, triggers, content, params_json, enabled, priority, display_order, create_time, update_time)
			VALUES
			(#{name}, #{description}, #{scope}, #{triggers}, #{content}, #{paramsJson}, #{enabled}, #{priority}, #{displayOrder}, NOW(), NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(Skill skill);

	/** 按主键更新非空字段。 */
	@Update("""
			<script>
			UPDATE skill
			<set>
			  <if test="name != null">name = #{name},</if>
			  <if test="description != null">description = #{description},</if>
			  <if test="scope != null">scope = #{scope},</if>
			  <if test="triggers != null">triggers = #{triggers},</if>
			  <if test="content != null">content = #{content},</if>
			  <if test="paramsJson != null">params_json = #{paramsJson},</if>
			  <if test="enabled != null">enabled = #{enabled},</if>
			  <if test="priority != null">priority = #{priority},</if>
			  <if test="displayOrder != null">display_order = #{displayOrder},</if>
			  update_time = NOW()
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(Skill skill);

	/** 按主键删除技能。 */
	@Delete("DELETE FROM skill WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

	/** 查询智能体指定作用域下启用的技能，按优先级降序排列。 */
	@Select("""
			SELECT s.* FROM skill s
			JOIN agent_skill a ON a.skill_id = s.id
			WHERE a.agent_id = #{agentId}
			  AND a.enabled = 1
			  AND s.enabled = 1
			  AND s.scope = #{scope}
			ORDER BY s.priority DESC, s.display_order, s.id
			""")
	List<Skill> selectEnabledByScopeAndAgentId(@Param("scope") String scope, @Param("agentId") Long agentId);

	/** 查询智能体绑定的全部技能。 */
	@Select("""
			SELECT s.* FROM skill s
			JOIN agent_skill a ON a.skill_id = s.id
			WHERE a.agent_id = #{agentId}
			ORDER BY s.priority DESC, s.display_order, s.id
			""")
	List<Skill> selectByAgentId(@Param("agentId") Long agentId);

}
