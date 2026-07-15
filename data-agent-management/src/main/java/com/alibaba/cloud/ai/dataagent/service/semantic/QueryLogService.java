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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.entity.QueryLog;
import com.alibaba.cloud.ai.dataagent.mapper.QueryLogMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 查询日志服务（证据链写入入口）。
 * <p>
 * NL2Semantic2SQL 链路完成后，由本服务写入 {@code query_log} 表，记录「用户问题 → 语义对象快照 → 受控拼装 SQL → 执行结果 →
 * 口径版本 → Langfuse trace」，支撑审计与可回放（datafoundry 式 Trace）。
 * </p>
 * <p>
 * 写入依赖 {@link QueryLogMapper#insert(QueryLog)} 的 {@code useGeneratedKeys}，主键 ID 会回填到入参实体，
 * 由 {@link #log(QueryLog)} 返回给调用方。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
@AllArgsConstructor
public class QueryLogService {

	private final QueryLogMapper queryLogMapper;

	/**
	 * 写入一条查询日志（证据链）。
	 * <p>
	 * 依赖 Mapper 层 {@code useGeneratedKeys}，写入后主键 ID 会回填到 {@code queryLog.id}。
	 * </p>
	 * @param queryLog 日志实体（id 可为空，写入后回填）
	 * @return 回填后的日志主键 ID
	 */
	public Long log(QueryLog queryLog) {
		queryLogMapper.insert(queryLog);
		return queryLog.getId();
	}

	/**
	 * 便捷写入：按字段构建日志实体并落库，返回主键 ID。
	 * <p>
	 * 用于调用方仅需传业务字段、无需显式构造 {@link QueryLog} 的场景。{@code metricVersions} 与 {@code feedback}
	 * 等可选字段不在本方法参数内，如需设置请改用 {@link #log(QueryLog)}。
	 * </p>
	 * @param sessionId 会话ID
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @param userQuery 用户原始问题
	 * @param semanticObjectJson 语义对象快照（JSON，证据链）
	 * @param sql 受控拼装SQL
	 * @param execTimeMs 执行耗时(ms)
	 * @param rowCount 返回行数
	 * @param status 状态（SUCCESS/FAIL/CLARIFY）
	 * @param traceId Langfuse trace 关联ID
	 * @return 回填后的日志主键 ID
	 */
	public Long logQuery(String sessionId, Integer agentId, Integer datasourceId, String userQuery,
			String semanticObjectJson, String sql, Integer execTimeMs, Integer rowCount, String status,
			String traceId) {
		QueryLog queryLog = QueryLog.builder()
			.sessionId(sessionId)
			.agentId(agentId)
			.datasourceId(datasourceId)
			.userQuery(userQuery)
			.semanticObject(semanticObjectJson)
			.generatedSql(sql)
			.execTimeMs(execTimeMs)
			.rowCount(rowCount)
			.status(status)
			.traceId(traceId)
			.build();
		return log(queryLog);
	}

	/**
	 * 按会话ID查询证据链日志列表（可回放追溯，datafoundry 式 Trace）。
	 * <p>
	 * 直接委托 {@link QueryLogMapper#selectBySessionId(String)}，返回该会话下所有查询日志（按时间倒序）。
	 * </p>
	 * @param sessionId 会话ID
	 * @return 日志列表（可能为空集合，不会为 null）
	 */
	public List<QueryLog> getBySessionId(String sessionId) {
		return queryLogMapper.selectBySessionId(sessionId);
	}

	/**
	 * 按主键 ID 查询单条证据链日志详情。
	 * @param id 日志主键
	 * @return 日志实体；不存在时返回 null
	 */
	public QueryLog getById(Long id) {
		return queryLogMapper.selectById(id);
	}

	/**
	 * 记录用户对某条查询日志的反馈（证据链闭环反馈）。
	 * <p>
	 * 委托 {@link QueryLogMapper#updateFeedback(Long, Integer)} 写入 feedback 字段。 约定
	 * 0=未标记/中性，1=正面（正确），2=负面（错误），由调用方约束。
	 * </p>
	 * @param id 日志主键
	 * @param feedback 反馈值（0/1/2）
	 */
	public void recordFeedback(Long id, Integer feedback) {
		queryLogMapper.updateFeedback(id, feedback);
	}

}
