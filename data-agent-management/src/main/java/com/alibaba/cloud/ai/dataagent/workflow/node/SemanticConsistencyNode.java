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

import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SemanticConsistencyDTO;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil.getCurrentExecutionStepInstruction;
import static com.alibaba.cloud.ai.dataagent.prompt.PromptHelper.buildMixMacSqlDbPrompt;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;

/**
 * 语义一致性校验节点，位于 SQL 生成之后、SQL 执行之前。
 *
 * <p>
 * 该节点负责校验生成的 SQL 查询在语义上是否与 Schema 和证据信息一致。 校验结果决定后续路由：
 * <ul>
 * <li>校验通过：进入 SQL 执行节点（{@code SqlExecuteNode}）</li>
 * <li>校验未通过：返回 SQL 生成节点重新生成</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 * @see SqlGenerateNode
 * @see SqlExecuteNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class SemanticConsistencyNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	/**
	 * 执行语义一致性校验逻辑。
	 * <p>
	 * 获取证据信息、Schema、SQL 语句和用户查询，调用 NL2SQL 服务进行语义一致性校验， 最终将校验结果写入状态。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含校验结果的 Map，key 为 {@value SEMANTIC_CONSISTENCY_NODE_OUTPUT}
	 * @throws Exception 校验过程中可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取必要的输入参数
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE);
		// 获取当前执行步骤和 SQL 查询
		String sql = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
		String userQuery = StateUtil.getCanonicalQuery(state);

		// 构建语义一致性校验参数对象
		// R36: 语义一致性入参有界（PromptHelper 出口 + 节点双保险）
		SemanticConsistencyDTO semanticConsistencyDTO = SemanticConsistencyDTO.builder()
			.dialect(dialect)
			.sql(PromptHelper.boundErrorSql(sql))
			.executionDescription(PromptHelper.boundQuery(getCurrentExecutionStepInstruction(state)))
			.schemaInfo(PromptHelper.boundKnowledge(buildMixMacSqlDbPrompt(schemaDTO, true)))
			.userQuery(PromptHelper.boundQuery(userQuery))
			.evidence(PromptHelper.boundEvidence(evidence))
			.build();
		log.info("开始语义一致性校验 - SQL: {}", sql);
		// 调用 NL2SQL 服务执行语义一致性校验
		Flux<ChatResponse> validationResultFlux = nl2SqlService.performSemanticConsistency(semanticConsistencyDTO);

		// 创建流式生成器，解析校验结果
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始语义一致性校验", "语义一致性校验完成", validationResult -> {
					// 校验结果以"不通过"开头表示未通过
					boolean isPassed = !validationResult.startsWith("不通过");
					Map<String, Object> result = buildValidationResult(isPassed, validationResult);
					log.info("[{}] 语义一致性校验结果: {}, 是否通过: {}", this.getClass().getSimpleName(), validationResult,
							isPassed);
					return result;
				}, validationResultFlux);

		return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, generator);
	}

	/**
	 * 构建语义一致性校验结果 Map。
	 * @param passed 校验是否通过
	 * @param validationResult 校验结果描述
	 * @return 校验结果 Map；通过时仅包含通过标志，未通过时还包含重试原因
	 */
	private Map<String, Object> buildValidationResult(boolean passed, String validationResult) {
		if (passed) {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, true);
		}
		else {
			return Map.of(SEMANTIC_CONSISTENCY_NODE_OUTPUT, false, SQL_REGENERATE_REASON,
					SqlRetryDto.semantic(validationResult));
		}
	}

}
