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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import java.util.Map;

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine.BuildResult;
import com.alibaba.cloud.ai.dataagent.service.semantic.EvidenceTraceService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticValidator;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticValidator.ValidationResult;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticVerificationService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticVerificationService.VerificationResult;
import com.alibaba.cloud.ai.dataagent.util.SqlGuard;
import com.alibaba.cloud.ai.dataagent.util.SqlGuard.GuardResult;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DATASOURCE_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;

/**
 * 受控 SQL 拼装节点（NL2Semantic2SQL 确定性构建，η₂）。
 * <p>
 * 消费 {@link SemanticParseNode#SEMANTIC_OBJECT}：先 {@link SemanticValidator}
 * 校验（不完整/口径歧义→反问）， 再 {@link SemanticVerificationService} 独立 LLM 验证（Maker/Checker 分离的
 * Checker 侧，η₅ 监督控制，异常 fail-open）， 再 {@link BuildSQLEngine} 受控拼装参数化 SQL，最后过
 * {@link SqlGuard#check} 二次只读校验（η₅ 纵深）。 产物 {@code CONTROLLED_SQL} + {@code SQL_PARAMS} 供
 * Data Gateway 执行。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
@Component
@AllArgsConstructor
public class BuildSQLNode implements NodeAction {

	/** 状态键：受控拼装 SQL */
	public static final String CONTROLLED_SQL = "CONTROLLED_SQL";

	/** 状态键：SQL 参数值列表 */
	public static final String SQL_PARAMS = "SQL_PARAMS";

	/** 状态键：需反问标记 */
	public static final String NEEDS_CLARIFICATION = "NEEDS_CLARIFICATION";

	/** 状态键：反问消息 */
	public static final String CLARIFICATION_MESSAGE = "CLARIFICATION_MESSAGE";

	private final SemanticValidator validator;

	private final BuildSQLEngine buildSQLEngine;

	/** Maker/Checker 独立 LLM 验证者（η₅ 监督控制，fail-open） */
	private final SemanticVerificationService verificationService;

	/** 证据链追踪（语义对象→SQL→状态 落库，可回放；fail-open 不阻断主链路） */
	private final EvidenceTraceService evidenceTraceService;

	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		SemanticObject so = StateUtil.getObjectValue(state, SemanticParseNode.SEMANTIC_OBJECT, SemanticObject.class);
		if (so == null) {
			log.warn("BuildSQL：语义对象缺失，触发反问");
			traceEvidence(state, null, null, "CLARIFY");
			return Map.of(NEEDS_CLARIFICATION, true, CLARIFICATION_MESSAGE, "语义解析结果缺失，请重新描述您想查询的指标");
		}

		// 1. 完整性 + 口径校验（歧义→反问）
		ValidationResult vr = validator.validate(so);
		if (vr.needsClarification()) {
			log.info("BuildSQL：需反问，{}", vr.message());
			traceEvidence(state, so, null, "CLARIFY");
			return Map.of(NEEDS_CLARIFICATION, true, CLARIFICATION_MESSAGE, vr.message());
		}

		// 1.5 Maker/Checker 独立 LLM 验证（η₅ 监督控制）。
		// Maker=SemanticParseNode 生成 vs Checker=本服务独立验证者。规则校验通过后，在受控拼装前
		// 再调一次 LLM 评估语义对象是否合理地回答了用户问题，捕捉 SemanticValidator 无法发现的语义层不合理
		// （如「查去年收入却选了今年的时间范围」）。异常 fail-open（不阻断主流程）。
		String userQuery = StateUtil.getCanonicalQuery(state);
		if (userQuery != null && !userQuery.isBlank()) {
			try {
				VerificationResult ar = verificationService.verify(so, userQuery);
				if (!ar.valid()) {
					String reason = (ar.reason() == null || ar.reason().isBlank()) ? "未提供原因" : ar.reason();
					log.info("BuildSQL：独立验证未通过，{}", reason);
					traceEvidence(state, so, null, "CLARIFY");
					return Map.of(NEEDS_CLARIFICATION, true, CLARIFICATION_MESSAGE, "独立验证未通过：" + reason);
				}
				log.debug("BuildSQL：独立验证通过，{}", ar.reason());
			}
			catch (Exception e) {
				// 监督控制 fail-open：验证器自身故障不应阻塞用户请求，降级为继续拼装
				log.warn("BuildSQL：独立验证异常，fail-open 继续 build，error={}", e.getMessage());
			}
		}

		// 2. 受控拼装参数化 SQL
		BuildResult br = buildSQLEngine.build(so);

		// 3. AST 只读护栏二次校验（η₅ 纵深，表名白名单留 Data Gateway 接线时传 recalledTables）
		GuardResult gr = SqlGuard.check(br.sql());
		if (!gr.allowed()) {
			log.warn("BuildSQL：SQL 护栏拦截，{}", gr.reason());
			traceEvidence(state, so, br.sql(), "FAIL");
			return Map.of(NEEDS_CLARIFICATION, true, CLARIFICATION_MESSAGE, "SQL 安全校验未通过：" + gr.reason());
		}

		log.info("BuildSQL：拼装完成，{}", br.sql());
		// 受控拼装成功：记录证据链（语义对象快照 + SQL + status=SUCCESS），可回放
		traceEvidence(state, so, br.sql(), "SUCCESS");
		return Map.of(CONTROLLED_SQL, br.sql(), SQL_PARAMS, br.params());
	}

	/**
	 * 记录证据链到 query_log（fail-open：异常只记日志，不阻断主链路）。
	 * <p>
	 * 从状态读取 sessionId(threadId)/agentId/datasourceId/userQuery/traceId，连同语义对象快照与拼装 SQL 委托
	 * {@link EvidenceTraceService#trace} 落库。执行耗时/行数在 SqlExecute 阶段才可知，此处留空。
	 * </p>
	 * @param state 工作流状态
	 * @param so 语义对象（可为 null）
	 * @param sql 受控拼装 SQL（可为 null）
	 * @param status SUCCESS / FAIL / CLARIFY
	 */
	private void traceEvidence(OverAllState state, SemanticObject so, String sql, String status) {
		try {
			String sessionId = state.value(TRACE_THREAD_ID, "");
			Integer agentId = parseIntegerOrNull(state.value(AGENT_ID, ""));
			Integer datasourceId = parseIntegerOrNull(state.value(DATASOURCE_ID, ""));
			String userQuery = state.value(INPUT_KEY, "");
			String traceId = state.value(TRACE_THREAD_ID, "");
			evidenceTraceService.trace(sessionId, agentId, datasourceId, userQuery, so, sql, null, null, status,
					traceId);
		}
		catch (Exception e) {
			log.warn("BuildSQL：证据链记录失败，fail-open 不阻断主链路，error={}", e.getMessage());
		}
	}

	/** 安全解析整型，失败返回 null（用于状态中字符串形态的 agentId/datasourceId）。 */
	private static Integer parseIntegerOrNull(String value) {
		if (value == null || value.isBlank()) {
			return null;
		}
		try {
			return Integer.valueOf(value.trim());
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
