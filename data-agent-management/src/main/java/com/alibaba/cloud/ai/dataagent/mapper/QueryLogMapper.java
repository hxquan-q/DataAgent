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

import com.alibaba.cloud.ai.dataagent.entity.QueryLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 查询日志+证据链 Mapper，操作 {@code query_log} 表（可回放，datafoundry 式 Trace）。
 *
 * @author dataagent
 */
@Mapper
public interface QueryLogMapper {

	@Select("SELECT * FROM query_log WHERE id = #{id}")
	QueryLog selectById(@Param("id") Long id);

	/**
	 * 按会话ID查询日志（可回放追溯用）。
	 * @param sessionId 会话ID
	 * @return 日志列表（按时间倒序）
	 */
	@Select("SELECT * FROM query_log WHERE session_id = #{sessionId} ORDER BY created_time DESC")
	List<QueryLog> selectBySessionId(@Param("sessionId") String sessionId);

	/**
	 * 按可选条件分页查询日志（管理端全局列表，SQLBot ChatRecord 式分页）。
	 * <p>
	 * 所有条件均可选（为空则不过滤），结果按创建时间倒序。分页通过 LIMIT/OFFSET 实现。
	 * </p>
	 * @param agentId 智能体ID（可空）
	 * @param status 状态 SUCCESS/FAIL/CLARIFY（可空）
	 * @param feedback 反馈 0/1/2（可空）
	 * @param offset 偏移量
	 * @param pageSize 每页大小
	 * @return 日志列表
	 */
	@Select("""
			<script>
				SELECT * FROM query_log
				<where>
					<if test='agentId != null'>AND agent_id = #{agentId}</if>
					<if test='status != null and status != ""'>AND status = #{status}</if>
					<if test='feedback != null'>AND feedback = #{feedback}</if>
				</where>
				ORDER BY created_time DESC
				LIMIT #{pageSize} OFFSET #{offset}
			</script>
			""")
	List<QueryLog> selectByConditions(@Param("agentId") Integer agentId, @Param("status") String status,
			@Param("feedback") Integer feedback, @Param("offset") int offset, @Param("pageSize") int pageSize);

	/**
	 * 按可选条件统计日志总数（配合分页）。
	 * @param agentId 智能体ID（可空）
	 * @param status 状态（可空）
	 * @param feedback 反馈（可空）
	 * @return 总数
	 */
	@Select("""
			<script>
				SELECT COUNT(*) FROM query_log
				<where>
					<if test='agentId != null'>AND agent_id = #{agentId}</if>
					<if test='status != null and status != ""'>AND status = #{status}</if>
					<if test='feedback != null'>AND feedback = #{feedback}</if>
				</where>
			</script>
			""")
	long countByConditions(@Param("agentId") Integer agentId, @Param("status") String status,
			@Param("feedback") Integer feedback);

	/**
	 * 写入查询日志（证据链）。
	 * @param log 日志实体
	 * @return 受影响行数
	 */
	@Insert("""
			INSERT INTO query_log (session_id, agent_id, datasource_id, user_query, semantic_object,
			  generated_sql, metric_versions, exec_time_ms, row_count, status, feedback, trace_id, created_time)
			VALUES (#{sessionId}, #{agentId}, #{datasourceId}, #{userQuery}, #{semanticObject},
			  #{generatedSql}, #{metricVersions}, #{execTimeMs}, #{rowCount}, #{status}, #{feedback},
			  #{traceId}, NOW())
			""")
	@Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
	int insert(QueryLog log);

	@Update("UPDATE query_log SET feedback = #{feedback} WHERE id = #{id}")
	int updateFeedback(@Param("id") Long id, @Param("feedback") Integer feedback);

}
