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

import com.alibaba.cloud.ai.dataagent.entity.SemanticAlias;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 语义别名 Mapper，操作 {@code semantic_alias} 表（业务黑话→结构化 code）。
 *
 * @author dataagent
 */
@Mapper
public interface SemanticAliasMapper {

	@Select("SELECT * FROM semantic_alias WHERE id = #{id}")
	SemanticAlias selectById(@Param("id") Long id);

	/**
	 * 按智能体ID查询全部启用别名（SemanticLayerLoader 加载用）。
	 * @param agentId 智能体ID
	 * @return 别名列表
	 */
	@Select("SELECT * FROM semantic_alias WHERE agent_id = #{agentId} AND status = 1 ORDER BY priority DESC")
	List<SemanticAlias> selectByAgentId(@Param("agentId") Integer agentId);

	/**
	 * 按智能体ID与别名文本精确匹配（别名消歧用）。
	 * @param agentId 智能体ID
	 * @param aliasText 别名文本
	 * @return 匹配的别名列表（按优先级降序）
	 */
	@Select("""
			SELECT * FROM semantic_alias
			WHERE agent_id = #{agentId} AND alias_text = #{aliasText} AND status = 1
			ORDER BY priority DESC
			""")
	List<SemanticAlias> selectByAgentAndText(@Param("agentId") Integer agentId, @Param("aliasText") String aliasText);

	@Insert("""
			INSERT INTO semantic_alias (agent_id, alias_text, target_type, target_code, match_type,
			  priority, status, created_time)
			VALUES (#{agentId}, #{aliasText}, #{targetType}, #{targetCode}, #{matchType},
			  #{priority}, #{status}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SemanticAlias alias);

	@Update("""
			<script>
			UPDATE semantic_alias
			<set>
				<if test="aliasText != null">alias_text = #{aliasText},</if>
				<if test="targetType != null">target_type = #{targetType},</if>
				<if test="targetCode != null">target_code = #{targetCode},</if>
				<if test="matchType != null">match_type = #{matchType},</if>
				<if test="priority != null">priority = #{priority},</if>
				<if test="status != null">status = #{status},</if>
			</set>
			WHERE id = #{id}
			</script>
			""")
	int updateById(SemanticAlias alias);

	@Delete("DELETE FROM semantic_alias WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

}
