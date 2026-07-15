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

import com.alibaba.cloud.ai.dataagent.entity.SqlExample;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * SQL 样例训练库 Mapper，操作 {@code sql_example} 表（vanna 三库反哺，域Ⅴ§16 在线辨识）。
 *
 * @author dataagent
 */
@Mapper
public interface SqlExampleMapper {

	@Select("SELECT * FROM sql_example WHERE id = #{id}")
	SqlExample selectById(@Param("id") Long id);

	/**
	 * 按智能体ID查询全部样例（few-shot 召回基础）。
	 * @param agentId 智能体ID
	 * @return 样例列表
	 */
	@Select("SELECT * FROM sql_example WHERE agent_id = #{agentId} AND reviewed = 1 ORDER BY created_time DESC")
	List<SqlExample> selectReviewedByAgentId(@Param("agentId") Integer agentId);

	/**
	 * 按 hash 查询是否已存在（反哺去重用）。
	 * @param agentId 智能体ID
	 * @param sqlHash SQL指纹
	 * @return 存在则返回记录，否则 {@code null}
	 */
	@Select("SELECT * FROM sql_example WHERE agent_id = #{agentId} AND sql_hash = #{sqlHash} LIMIT 1")
	SqlExample selectByHash(@Param("agentId") Integer agentId, @Param("sqlHash") String sqlHash);

	@Insert("""
			INSERT INTO sql_example (agent_id, datasource_id, question, sql_text, dialect, sql_hash,
			  source, reviewed, created_time)
			VALUES (#{agentId}, #{datasourceId}, #{question}, #{sqlText}, #{dialect}, #{sqlHash},
			  #{source}, #{reviewed}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(SqlExample example);

	@Delete("DELETE FROM sql_example WHERE id = #{id}")
	int deleteById(@Param("id") Long id);

}
