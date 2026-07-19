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

import static com.alibaba.cloud.ai.dataagent.workflow.node.BuildSQLNode.CLARIFICATION_MESSAGE;
import static com.alibaba.cloud.ai.dataagent.workflow.node.BuildSQLNode.CONTROLLED_SQL;
import static com.alibaba.cloud.ai.dataagent.workflow.node.BuildSQLNode.NEEDS_CLARIFICATION;
import static com.alibaba.cloud.ai.dataagent.workflow.node.BuildSQLNode.SQL_PARAMS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine;
import com.alibaba.cloud.ai.dataagent.service.semantic.BuildSQLEngine.BuildResult;
import com.alibaba.cloud.ai.dataagent.service.semantic.EvidenceTraceService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticValidator;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticValidator.ValidationResult;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticVerificationService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticVerificationService.VerificationResult;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.AGENT_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.DATASOURCE_ID;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INPUT_KEY;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TRACE_THREAD_ID;

/**
 * {@link BuildSQLNode} 受控拼装节点接线测试（v0.2 语义层，η₂ 确定性构建 + η₅ 监督）。
 * <p>
 * 验证 nl2sqlSemanticGraph 中 BuildSQL 节点的编排逻辑： 校验通过 → 受控拼装 SQL；校验失败/歧义 → 反问。 覆盖
 * Maker/Checker 双验证与 fail-open 降级路径。
 * </p>
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BuildSQLNodeTest {

	@Mock
	private SemanticValidator validator;

	@Mock
	private BuildSQLEngine buildSQLEngine;

	@Mock
	private SemanticVerificationService verificationService;

	@Mock
	private EvidenceTraceService evidenceTraceService;

	private BuildSQLNode buildSQLNode;

	@BeforeEach
	void setUp() {
		buildSQLNode = new BuildSQLNode(validator, buildSQLEngine, verificationService, evidenceTraceService);
	}

	@Test
	void apply_validSemanticObject_producesControlledSql() throws Exception {
		// given：完整语义对象 + 校验通过 + 独立验证通过
		OverAllState state = stateWith(validSemanticObject(), "查询上月订单金额");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.ok());
		when(verificationService.verify(any(SemanticObject.class), any(String.class)))
			.thenReturn(VerificationResult.ok());
		when(buildSQLEngine.build(any(SemanticObject.class)))
			.thenReturn(new BuildResult("SELECT SUM(total_amount) AS order_amount FROM orders LIMIT ?", List.of(1000)));

		// when
		Map<String, Object> result = buildSQLNode.apply(state);

		// then：产出受控 SQL + 参数，未触发反问
		assertThat(result).containsKey(CONTROLLED_SQL);
		assertThat(result.get(CONTROLLED_SQL)).asString().contains("SELECT SUM(total_amount)");
		assertThat(result).containsKey(SQL_PARAMS);
		assertThat(result).doesNotContainKey(NEEDS_CLARIFICATION);
	}

	@Test
	void apply_validatorNeedsClarification_returnsClarification() throws Exception {
		// given：校验器判定需反问（如未选指标）
		OverAllState state = stateWith(validSemanticObject(), "查询订单");
		when(validator.validate(any(SemanticObject.class)))
			.thenReturn(ValidationResult.clarification("未选择任何指标，请说明您想查询的指标"));

		// when
		Map<String, Object> result = buildSQLNode.apply(state);

		// then：触发反问，不拼装 SQL
		assertThat(result.get(NEEDS_CLARIFICATION)).isEqualTo(true);
		assertThat(result.get(CLARIFICATION_MESSAGE)).asString().contains("未选择任何指标");
		assertThat(result).doesNotContainKey(CONTROLLED_SQL);
	}

	@Test
	void apply_independentVerificationFails_returnsClarification() throws Exception {
		// given：规则校验通过，但独立 LLM 验证（Checker）未通过 → 反问
		OverAllState state = stateWith(validSemanticObject(), "查去年收入");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.ok());
		when(verificationService.verify(any(SemanticObject.class), any(String.class)))
			.thenReturn(VerificationResult.fail("时间范围与查询意图不符"));

		// when
		Map<String, Object> result = buildSQLNode.apply(state);

		// then：反问，原因来自 Checker
		assertThat(result.get(NEEDS_CLARIFICATION)).isEqualTo(true);
		assertThat(result.get(CLARIFICATION_MESSAGE)).asString().contains("时间范围与查询意图不符");
	}

	@Test
	void apply_verificationServiceThrows_failOpenAndContinuesBuild() throws Exception {
		// given：监督控制 fail-open —— Checker 异常不应阻塞，降级继续拼装（η₅）
		OverAllState state = stateWith(validSemanticObject(), "查询订单金额");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.ok());
		when(verificationService.verify(any(SemanticObject.class), any(String.class)))
			.thenThrow(new RuntimeException("LLM 超时"));
		when(buildSQLEngine.build(any(SemanticObject.class)))
			.thenReturn(new BuildResult("SELECT SUM(total_amount) AS order_amount FROM orders LIMIT ?", List.of(1000)));

		// when
		Map<String, Object> result = buildSQLNode.apply(state);

		// then：异常被吞，仍产出受控 SQL（fail-open 不阻断主流程）
		assertThat(result).containsKey(CONTROLLED_SQL);
		assertThat(result).doesNotContainKey(NEEDS_CLARIFICATION);
	}

	@Test
	void apply_emptyMetrics_triggersValidatorClarification() throws Exception {
		// given：语义对象无指标（LLM 未匹配候选）→ 校验器反问
		SemanticObject empty = new SemanticObject();
		OverAllState state = stateWith(empty, "查点东西");
		when(validator.validate(any(SemanticObject.class)))
			.thenReturn(ValidationResult.clarification("未选择任何指标，请说明您想查询的指标"));

		Map<String, Object> result = buildSQLNode.apply(state);

		assertThat(result.get(NEEDS_CLARIFICATION)).isEqualTo(true);
	}

	@Test
	void apply_success_tracesEvidenceChain() throws Exception {
		// given：成功拼装 → 应记录证据链（status=SUCCESS）
		OverAllState state = stateWith(validSemanticObject(), "查询上月订单金额");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.ok());
		when(verificationService.verify(any(SemanticObject.class), any(String.class)))
			.thenReturn(VerificationResult.ok());
		when(buildSQLEngine.build(any(SemanticObject.class)))
			.thenReturn(new BuildResult("SELECT SUM(total_amount) FROM orders LIMIT ?", List.of(1000)));

		buildSQLNode.apply(state);

		// then：证据链被记录，status=SUCCESS
		verify(evidenceTraceService).trace(eq("thread-1"), eq(1), eq(10), eq("查询上月订单金额"), any(SemanticObject.class),
				eq("SELECT SUM(total_amount) FROM orders LIMIT ?"), isNull(), isNull(), eq("SUCCESS"), eq("thread-1"));
	}

	@Test
	void apply_clarification_tracesEvidenceChainWithClarifyStatus() throws Exception {
		// given：需反问 → 应记录证据链（status=CLARIFY）
		OverAllState state = stateWith(validSemanticObject(), "查询订单");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.clarification("未选择任何指标"));

		buildSQLNode.apply(state);

		verify(evidenceTraceService).trace(eq("thread-1"), eq(1), eq(10), eq("查询订单"), any(SemanticObject.class),
				isNull(), isNull(), isNull(), eq("CLARIFY"), eq("thread-1"));
	}

	@Test
	void apply_traceFailure_doesNotBlockMainFlow() throws Exception {
		// given：证据链记录抛异常 → fail-open，主链路不阻断，仍产出受控 SQL
		OverAllState state = stateWith(validSemanticObject(), "查询订单金额");
		when(validator.validate(any(SemanticObject.class))).thenReturn(ValidationResult.ok());
		when(verificationService.verify(any(SemanticObject.class), any(String.class)))
			.thenReturn(VerificationResult.ok());
		when(buildSQLEngine.build(any(SemanticObject.class)))
			.thenReturn(new BuildResult("SELECT SUM(total_amount) FROM orders LIMIT ?", List.of(1000)));
		// 证据链记录抛异常
		org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
			.when(evidenceTraceService)
			.trace(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
					org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
					org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
					org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.isNull(),
					org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());

		Map<String, Object> result = buildSQLNode.apply(state);

		// then：异常被吞，仍产出受控 SQL（fail-open 不阻断）
		assertThat(result).containsKey(CONTROLLED_SQL);
	}

	/**
	 * 构造带语义对象 + canonical 查询的 OverAllState（注册 BuildSQLNode 读取的全部键）。
	 */
	private OverAllState stateWith(SemanticObject so, String canonicalQuery) {
		OverAllState state = new OverAllState();
		state.registerKeyAndStrategy(SemanticParseNode.SEMANTIC_OBJECT, new ReplaceStrategy());
		state.registerKeyAndStrategy(QUERY_ENHANCE_NODE_OUTPUT, new ReplaceStrategy());
		// 证据链 trace 读取的键
		state.registerKeyAndStrategy(INPUT_KEY, new ReplaceStrategy());
		state.registerKeyAndStrategy(AGENT_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(DATASOURCE_ID, new ReplaceStrategy());
		state.registerKeyAndStrategy(TRACE_THREAD_ID, new ReplaceStrategy());
		QueryEnhanceOutputDTO enhanceDTO = new QueryEnhanceOutputDTO();
		enhanceDTO.setCanonicalQuery(canonicalQuery);
		state.updateState(Map.of(SemanticParseNode.SEMANTIC_OBJECT, so, QUERY_ENHANCE_NODE_OUTPUT, enhanceDTO,
				INPUT_KEY, canonicalQuery, AGENT_ID, "1", DATASOURCE_ID, "10", TRACE_THREAD_ID, "thread-1"));
		return state;
	}

	private SemanticObject validSemanticObject() {
		SemanticObject so = new SemanticObject();
		MetricRef ref = new MetricRef();
		ref.setMetricCode("order_amount");
		so.setMetrics(List.of(ref));
		so.setLimit(1000);
		return so;
	}

}
