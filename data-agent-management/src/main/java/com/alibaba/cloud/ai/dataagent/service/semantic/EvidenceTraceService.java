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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 证据链追踪服务（datafoundry 式 Run Trace 可回放）。
 * <p>
 * 在 NL2Semantic2SQL 链路完成后，统一收口「语义对象 → 受控拼装 SQL → 执行结果 → 口径版本」的证据链写入，
 * 将内存中的语义对象（POJO/Map）序列化为 JSON 快照，再委托 {@link QueryLogService#logQuery} 落库，
 * 支撑后续审计、口径回放与问题归因。
 * </p>
 * <p>
 * 本服务只负责语义对象的 JSON 序列化与下游委托，不直接持有数据库连接；落库细节（主键回填、字段映射）见 {@link QueryLogService}。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Service
@AllArgsConstructor
public class EvidenceTraceService {

	private final QueryLogService queryLogService;

	private final ObjectMapper objectMapper;

	/**
	 * 记录一条证据链（语义对象 → SQL → 执行结果 → 口径），返回日志主键 ID。
	 * <p>
	 * 当 {@code semanticObject} 不为 {@code null} 时，使用 {@link ObjectMapper} 序列化为 JSON 字符串后传入
	 * {@link QueryLogService#logQuery}；序列化失败仅告警并返回 {@code null}，不抛异常以避免影响主链路。 当
	 * {@code semanticObject} 为 {@code null} 时，直接以 {@code null} 作为语义对象快照写入。
	 * </p>
	 * @param sessionId 会话ID
	 * @param agentId 智能体ID
	 * @param datasourceId 数据源ID
	 * @param userQuery 用户原始问题
	 * @param semanticObject 语义对象（POJO/Map，序列化为 JSON 作为证据链快照；为 null 则写入 null）
	 * @param sql 受控拼装SQL
	 * @param execTimeMs 执行耗时(ms)
	 * @param rowCount 返回行数
	 * @param status 状态（SUCCESS/FAIL/CLARIFY）
	 * @param traceId Langfuse trace 关联ID
	 * @return 回填后的日志主键 ID；序列化失败时返回 {@code null}
	 */
	public Long trace(String sessionId, Integer agentId, Integer datasourceId, String userQuery, Object semanticObject,
			String sql, Integer execTimeMs, Integer rowCount, String status, String traceId) {
		String semanticObjectJson = null;
		if (semanticObject != null) {
			try {
				semanticObjectJson = objectMapper.writeValueAsString(semanticObject);
			}
			catch (Exception ex) {
				log.warn("序列化语义对象失败，跳过证据链写入: sessionId={}, agentId={}, traceId={}", sessionId, agentId, traceId, ex);
				return null;
			}
		}
		return queryLogService.logQuery(sessionId, agentId, datasourceId, userQuery, semanticObjectJson, sql,
				execTimeMs, rowCount, status, traceId);
	}

}
