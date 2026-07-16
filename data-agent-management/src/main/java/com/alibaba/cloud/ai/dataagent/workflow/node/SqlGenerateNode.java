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

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.prompt.SqlGenerationDTO;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil.getCurrentExecutionStepInstruction;

/**
 * SQL 生成节点，负责根据当前执行步骤生成或重新生成 SQL 查询。
 *
 * <p>
 * 该节点支持多轮 SQL 优化与精炼，具备以下能力：
 * <ul>
 * <li>多轮 SQL 优化与改进</li>
 * <li>语法校验与安全分析</li>
 * <li>性能优化与智能缓存</li>
 * <li>处理执行异常与语义一致性校验失败的情况</li>
 * <li>基于 Schema 建议管理重试逻辑</li>
 * <li>在重新生成过程中提供流式反馈</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlGenerateNode implements NodeAction {

	private final Nl2SqlService nl2SqlService;

	private final DataAgentProperties properties;

	/**
	 * 执行 SQL 生成逻辑。
	 * <p>
	 * 根据当前执行步骤生成或重新生成 SQL 查询。若达到最大尝试次数则结束流程； 否则根据重试原因（SQL 执行失败或语义一致性校验未通过）决定是重新生成还是首次生成。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含 SQL 生成结果的 Map，key 为 {@value SQL_GENERATE_OUTPUT}
	 * @throws Exception 生成 SQL 时可能抛出的异常
	 */
	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 判断是否达到最大尝试次数
		int count = state.value(SQL_GENERATE_COUNT, 0);
		if (count >= properties.getMaxSqlRetryCount()) {
			ExecutionStep executionStep = PlanProcessUtil.getCurrentExecutionStep(state);
			String sqlGenerateOutput = String.format("步骤[%d]中，SQL次数生成超限，最大尝试次数：%d，已尝试次数:%d，该步骤内容: \n %s",
					executionStep.getStep(), properties.getMaxSqlRetryCount(), count,
					executionStep.getToolParameters().getInstruction());
			log.error("SQL 生成失败，原因: {}", sqlGenerateOutput);
			Flux<ChatResponse> preFlux = Flux.just(ChatResponseUtil.createResponse(sqlGenerateOutput));
			Flux<GraphResponse<StreamingOutput>> generator = FluxUtil
				.createStreamingGeneratorWithMessages(
						this.getClass(), state, "正在进行重试评估...", "重试评估完成！", retryOutput -> Map.of(SQL_GENERATE_OUTPUT,
								StateGraph.END, SQL_GENERATE_COUNT, 0, SQL_HEAL_ERRORS, Collections.emptyList()),
						preFlux);
			// 重置 SQL 生成计数
			return Map.of(SQL_GENERATE_OUTPUT, generator);
		}

		// 获取 planner 分配的当前执行步骤的 SQL 任务要求，每个步骤的 SQL 任务不同
		// 不要使用 user query 这个总体的大任务
		String promptForSql = getCurrentExecutionStepInstruction(state);

		// 准备生成 SQL
		String displayMessage;
		Flux<String> sqlFlux;
		SqlRetryDto retryDto = StateUtil.getObjectValue(state, SQL_REGENERATE_REASON, SqlRetryDto.class,
				SqlRetryDto.empty());
		boolean isRetry = retryDto.sqlExecuteFail() || retryDto.semanticFail();

		// 自愈错误累积（#13）：每次重试把当前错误追加进历史，让 LLM 看到全部失败原因，避免重蹈覆辙
		List<String> healErrors = StateUtil.getObjectValue(state, SQL_HEAL_ERRORS, List.class, Collections.emptyList());
		List<String> updatedHealErrors;
		String errorForPrompt;
		if (isRetry) {
			updatedHealErrors = new ArrayList<>(healErrors);
			updatedHealErrors.add(retryDto.reason());
			errorForPrompt = buildHealErrorMessage(updatedHealErrors);
		}
		else {
			// 首次生成，清空历史错误
			updatedHealErrors = Collections.emptyList();
			errorForPrompt = null;
		}

		// 根据重试原因决定生成策略
		if (retryDto.sqlExecuteFail()) {
			// SQL 执行失败，重新生成（注入累积错误历史）
			displayMessage = "检测到SQL执行异常，开始重新生成SQL...";
			sqlFlux = handleRetryGenerateSql(state, StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT, ""),
					errorForPrompt, promptForSql);
		}
		else if (retryDto.semanticFail()) {
			// 语义一致性校验未通过，重新生成（注入累积错误历史）
			displayMessage = "语义一致性校验未通过，开始重新生成SQL...";
			sqlFlux = handleRetryGenerateSql(state, StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT, ""),
					errorForPrompt, promptForSql);
		}
		else {
			// 首次生成 SQL
			displayMessage = "开始生成SQL...";
			sqlFlux = handleGenerateSql(state, promptForSql);
		}

		// 准备返回结果，同时清除一些状态数据；回写累积错误历史供下一轮自愈使用
		Map<String, Object> result = new HashMap<>(Map.of(SQL_GENERATE_OUTPUT, StateGraph.END, SQL_GENERATE_COUNT,
				count + 1, SQL_REGENERATE_REASON, SqlRetryDto.empty(), SQL_HEAL_ERRORS, updatedHealErrors));

		// 创建展示流，仅用于提升用户体验；同时收集生成的 SQL
		StringBuilder sqlCollector = new StringBuilder();
		Flux<ChatResponse> preFlux = Flux.just(ChatResponseUtil.createResponse(displayMessage),
				ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()));
		Flux<ChatResponse> displayFlux = preFlux
			.concatWith(sqlFlux.doOnNext(sqlCollector::append).map(ChatResponseUtil::createPureResponse))
			.concatWith(Flux.just(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()),
					ChatResponseUtil.createResponse("SQL生成完成，准备执行")));

		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> {
					// 收集完整的 SQL 并写入结果
					String sql = nl2SqlService.sqlTrim(sqlCollector.toString());
					result.put(SQL_GENERATE_OUTPUT, sql);
					return result;
				}, displayFlux);

		return Map.of(SQL_GENERATE_OUTPUT, generator);
	}

	/**
	 * 处理 SQL 重新生成（基于原有 SQL 和错误信息）。
	 * @param state 工作流全局状态
	 * @param originalSql 原 SQL 语句
	 * @param errorMsg 错误信息
	 * @param executionDescription 当前执行步骤的描述
	 * @return SQL 生成流
	 */
	private Flux<String> handleRetryGenerateSql(OverAllState state, String originalSql, String errorMsg,
			String executionDescription) {
		String evidence = StateUtil.getStringValue(state, EVIDENCE);
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String userQuery = StateUtil.getCanonicalQuery(state);
		String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE);

		// 构建 SQL 生成所需的参数对象
		SqlGenerationDTO sqlGenerationDTO = SqlGenerationDTO.builder()
			.evidence(PromptHelper.boundEvidence(evidence))
			.query(PromptHelper.boundQuery(userQuery))
			.schemaDTO(schemaDTO)
			.sql(PromptHelper.boundErrorSql(originalSql))
			.exceptionMessage(PromptHelper.boundErrorText(errorMsg))
			.executionDescription(PromptHelper.boundQuery(executionDescription))
			.dialect(dialect)
			.build();

		return nl2SqlService.generateSql(sqlGenerationDTO);
	}

	/**
	 * 处理 SQL 首次生成。
	 * @param state 工作流全局状态
	 * @param executionDescription 当前执行步骤的描述
	 * @return SQL 生成流
	 */
	private Flux<String> handleGenerateSql(OverAllState state, String executionDescription) {
		return handleRetryGenerateSql(state, null, null, executionDescription);
	}

	/**
	 * 构建自愈错误历史提示文本（#13）。
	 * <p>
	 * 将累积的历史错误按次序拼接，提示 LLM 避免重复同样的错误。
	 * </p>
	 * @param errors 累积的历史错误列表
	 * @return 拼接后的错误历史文本；列表为空时返回 null
	 */
	private String buildHealErrorMessage(List<String> errors) {
		if (errors == null || errors.isEmpty()) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < errors.size(); i++) {
			sb.append("第 ").append(i + 1).append(" 次尝试错误：").append(errors.get(i)).append("\n");
		}
		sb.append("以上为历史失败原因，请避免重复同样的错误。");
		// R37: 自愈错误历史有界
		return PromptHelper.boundErrorText(sb.toString());
	}

}
