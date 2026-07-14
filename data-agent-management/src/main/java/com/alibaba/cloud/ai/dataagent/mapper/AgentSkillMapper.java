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

import com.alibaba.cloud.ai.dataagent.entity.AgentSkill;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 智能体技能绑定数据访问层。 */
@Mapper
public interface AgentSkillMapper {

	/** 查询单个绑定。 */
	@Select("SELECT * FROM agent_skill WHERE agent_id = #{agentId} AND skill_id = #{skillId}")
	AgentSkill select(@Param("agentId") Long agentId, @Param("skillId") Long skillId);

	/** 查询智能体的全部绑定。 */
	@Select("SELECT * FROM agent_skill WHERE agent_id = #{agentId} ORDER BY id")
	List<AgentSkill> selectByAgentId(@Param("agentId") Long agentId);

	/** 新增绑定并回填主键。 */
	@Insert("""
			INSERT INTO agent_skill (agent_id, skill_id, enabled)
			VALUES (#{agentId}, #{skillId}, #{enabled})
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(AgentSkill agentSkill);

	/** 更新绑定启用状态。 */
	@Update("""
			UPDATE agent_skill SET enabled = #{enabled}
			WHERE agent_id = #{agentId} AND skill_id = #{skillId}
			""")
	int updateEnabled(AgentSkill agentSkill);

	/** 解除单个绑定。 */
	@Delete("DELETE FROM agent_skill WHERE agent_id = #{agentId} AND skill_id = #{skillId}")
	int delete(@Param("agentId") Long agentId, @Param("skillId") Long skillId);

	/** 删除技能的全部绑定。 */
	@Delete("DELETE FROM agent_skill WHERE skill_id = #{skillId}")
	int deleteBySkillId(@Param("skillId") Long skillId);

}
