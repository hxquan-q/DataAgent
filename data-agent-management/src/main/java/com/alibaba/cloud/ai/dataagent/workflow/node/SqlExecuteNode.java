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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_EXECUTE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_COUNT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_GENERATE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_REGENERATE_REASON;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_RESULT_LIST_MEMORY;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DisplayStyleBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.connector.accessor.Accessor;
import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.nl2sql.Nl2SqlService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * SQL 执行节点，负责在数据库上执行前序节点生成的 SQL 查询。
 *
 * <p>
 * 该节点的职责包括：
 * <ul>
 * <li>执行前序节点生成的 SQL 查询</li>
 * <li>处理查询结果和执行异常</li>
 * <li>在执行过程中向用户提供流式反馈</li>
 * <li>逐步累积执行结果</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
@AllArgsConstructor
public class SqlExecuteNode implements NodeAction {

	private final DatabaseUtil databaseUtil;

	private final Nl2SqlService nl2SqlService;

	private final LlmService llmService;

	private final DataAgentProperties properties;

	private final JsonParseUtil jsonParseUtil;

	private static final int SAMPLE_DATA_NUMBER = 20;

	/**
	 * 执行 SQL 查询逻辑。
	 * <p>
	 * 获取当前执行步骤、SQL 语句和智能体的数据库配置，然后调用实际查询方法。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含 SQL 执行结果的 Map，key 为 {@value SQL_EXECUTE_NODE_OUTPUT}
	 * @throws Exception 执行 SQL 时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取当前执行步骤号
		Integer currentStep = PlanProcessUtil.getCurrentStepNumber(state);

		// 获取并裁剪 SQL 语句
		String sqlQuery = StateUtil.getStringValue(state, SQL_GENERATE_OUTPUT);
		sqlQuery = nl2SqlService.sqlTrim(sqlQuery);

		log.info("执行 SQL 查询: {}", sqlQuery);

		// 从状态中获取智能体 ID
		String agentIdStr = StateUtil.getStringValue(state, Constant.AGENT_ID);
		if (StringUtils.isBlank(agentIdStr)) {
			throw new IllegalStateException("智能体 ID 不能为空。");
		}

		Long agentId = Long.valueOf(agentIdStr);

		// 动态获取智能体的数据源配置
		DbConfigBO dbConfig = databaseUtil.getAgentDbConfig(agentId);

		return executeSqlQuery(state, currentStep, sqlQuery, dbConfig, agentId);
	}

	/**
	 * 在数据库上执行 SQL 查询并处理结果。
	 * <p>
	 * 该方法遵循"业务逻辑优先"模式：1. 立即执行实际的 SQL 查询；2. 处理并存储结果；3. 创建流式输出（仅用于用户体验）。
	 * </p>
	 * @param state 工作流全局状态，包含执行上下文
	 * @param currentStep 当前执行步骤号
	 * @param sqlQuery 要执行的 SQL 查询语句
	 * @param dbConfig 数据库配置
	 * @param agentId 智能体 ID
	 * @return 包含流式输出生成器的 Map
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> executeSqlQuery(OverAllState state, Integer currentStep, String sqlQuery,
			DbConfigBO dbConfig, Long agentId) {
		// 先执行业务逻辑 - 实际的 SQL 执行
		DbQueryParameter dbQueryParameter = new DbQueryParameter();
		dbQueryParameter.setSql(sqlQuery);
		dbQueryParameter.setSchema(dbConfig.getSchema());

		Accessor dbAccessor = databaseUtil.getAgentAccessor(agentId);
		final Map<String, Object> result = new HashMap<>();

		// 先返回流式数据，再执行数据库查询
		Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
			emitter.next(ChatResponseUtil.createResponse("开始执行SQL..."));
			emitter.next(ChatResponseUtil.createResponse("执行SQL查询："));
			emitter.next(ChatResponseUtil.createPureResponse(TextType.SQL.getStartSign()));
			emitter.next(ChatResponseUtil.createResponse(sqlQuery));
			emitter.next(ChatResponseUtil.createPureResponse(TextType.SQL.getEndSign()));
			ResultBO resultBO = ResultBO.builder().build();

			try {
				// 执行 SQL 查询并立即获取结果
				ResultSetBO resultSetBO = dbAccessor.executeSqlAndReturnObject(dbConfig, dbQueryParameter);
				// 调用大模型获取图表配置信息并填充到 ResultSetBO 中
				DisplayStyleBO displayStyleBO = enrichResultSetWithChartConfig(state, resultSetBO);
				resultBO.setResultSet(resultSetBO);
				resultBO.setDisplayStyle(displayStyleBO);

				String strResultSetJson = JsonUtil.getObjectMapper().writeValueAsString(resultSetBO);
				String strResultJson = JsonUtil.getObjectMapper().writeValueAsString(resultBO);

				// 数据执行成功
				emitter.next(ChatResponseUtil.createResponse("执行SQL完成"));
				emitter.next(ChatResponseUtil.createResponse("SQL查询结果："));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getStartSign()));
				emitter.next(ChatResponseUtil.createPureResponse(strResultJson));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.RESULT_SET.getEndSign()));

				// 使用查询输出更新步骤结果
				Map<String, String> existingResults = StateUtil.getObjectValue(state, SQL_EXECUTE_NODE_OUTPUT,
						Map.class, new HashMap<>());
				Map<String, String> updatedResults = PlanProcessUtil.addStepResult(existingResults, currentStep,
						strResultSetJson);

				log.info("SQL 执行成功，结果行数: {}", resultSetBO.getData() != null ? resultSetBO.getData().size() : 0);

				// 回写最终执行的 SQL，报告节点需要使用
				ExecutionStep.ToolParameters currentStepParams = PlanProcessUtil.getCurrentExecutionStep(state)
					.getToolParameters();
				currentStepParams.setSqlQuery(sqlQuery);

				// 准备最终结果对象
				// 存储 SQL 查询结果列表，供代码执行节点使用
				// SQL 执行成功时重置 SQL 生成重试次数
				result.putAll(Map.of(SQL_EXECUTE_NODE_OUTPUT, updatedResults, SQL_REGENERATE_REASON,
						SqlRetryDto.empty(), SQL_RESULT_LIST_MEMORY, resultSetBO.getData(), PLAN_CURRENT_STEP,
						currentStep + 1, SQL_GENERATE_COUNT, 0));
			}
			catch (Exception e) {
				String errorMessage = e.getMessage();
				log.error("SQL 执行失败 - SQL 如下: \n {} \n ", sqlQuery, e);
				result.put(SQL_REGENERATE_REASON, SqlRetryDto.sqlExecute(errorMessage));
				emitter.next(ChatResponseUtil.createResponse("SQL执行失败: " + errorMessage));
			}
			finally {
				emitter.complete();
			}
		});

		// 使用工具类创建生成器，返回预先计算好的业务结果
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> result, displayFlux);
		return Map.of(SQL_EXECUTE_NODE_OUTPUT, generator);
	}

	/**
	 * 调用大模型获取图表配置信息并填充到 ResultSetBO 中。
	 * @param state 整体状态
	 * @param resultSetBO SQL 执行结果
	 * @return 图表展示样式配置；若未启用图表或获取失败则返回默认配置
	 */
	private DisplayStyleBO enrichResultSetWithChartConfig(OverAllState state, ResultSetBO resultSetBO) {
		// 创建图表展示样式对象
		DisplayStyleBO displayStyle = new DisplayStyleBO();
		if (!this.properties.isEnableSqlResultChart()) {
			log.debug("SQL 结果图表未启用，默认使用表格展示");
			displayStyle.setType("table");
			return displayStyle;
		}

		try {
			// 获取用户查询
			String userQuery = StateUtil.getCanonicalQuery(state);

			// 将 SQL 结果转换为 JSON 字符串，限制数据量以避免提示词过长
			String sqlResultJson = JsonUtil.getObjectMapper()
				.writeValueAsString(resultSetBO.getData() != null
						? resultSetBO.getData().stream().limit(SAMPLE_DATA_NUMBER).toList() : null);

			// 构建用户提示词，包含 SQL 结果数据
			String userPrompt = String.format("""
					# 正式任务

					<最新>用户输入: %s
					范例数据: %s

					# 输出
					""", userQuery != null ? userQuery : "数据可视化", sqlResultJson);

			// 加载数据可视化分析提示词模板（系统提示词）
			String fullPrompt = PromptHelper.buildDataViewAnalysisPrompt();
			// 分割系统提示词和用户提示词模板
			String[] parts = fullPrompt.split("=== 用户输入 ===", 2);
			// 渲染系统提示词（当前没有变量，直接使用模板内容）
			String systemPrompt = parts[0].trim();

			log.debug("构建的图表配置系统提示词如下 \n {} \n", systemPrompt);
			log.debug("构建的图表配置用户提示词如下 \n {} \n", userPrompt);

			// 调用大模型生成图表配置（使用系统提示词和用户提示词）
			String chartConfigJson = llmService.toStringFlux(llmService.call(systemPrompt, userPrompt))
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block(Duration.ofMillis(properties.getEnrichSqlResultTimeout()));
			if (chartConfigJson != null && !chartConfigJson.trim().isEmpty()) {
				String content = MarkdownParserUtil.extractText(chartConfigJson.trim());
				displayStyle = jsonParseUtil.tryConvertToObject(content, DisplayStyleBO.class);
				log.debug("成功为 ResultSetBO 补充图表配置: type={}, title={}, x={}, y={}", displayStyle.getType(),
						displayStyle.getTitle(), displayStyle.getX(), displayStyle.getY());
				return displayStyle;
			}
			else {
				log.warn("大模型返回空图表配置，使用默认设置");
			}
		}
		catch (Exception e) {
			log.error("为 ResultSetBO 补充图表配置失败", e);
			// 不抛出异常，允许流程继续执行
		}
		return null;
	}

}
