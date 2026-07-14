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
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.entity.UserPromptConfig;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.prompt.UserPromptService;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.DataSummarizer;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 报告生成节点，位于所有执行步骤完成之后，负责生成最终的分析报告。
 *
 * <p>
 * 该节点的职责包括：
 * <ul>
 * <li>根据 SQL 执行结果生成详细的分析报告</li>
 * <li>总结数据洞察和发现</li>
 * <li>为用户查询提供全面的回答</li>
 * <li>创建结构化的最终输出</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
@Component
public class ReportGeneratorNode implements NodeAction {

	private final LlmService llmService;

	private final BeanOutputConverter<Plan> converter;

	private final UserPromptService promptConfigService;

	public ReportGeneratorNode(LlmService llmService, UserPromptService promptConfigService) {
		this.llmService = llmService;
		this.converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});
		this.promptConfigService = promptConfigService;
	}

	/**
	 * 执行报告生成逻辑。
	 * <p>
	 * 获取执行计划、用户输入、执行结果和当前步骤信息，构建报告生成提示词并调用大模型， 最终将报告内容写入状态。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含报告内容的 Map，key 为 {@value RESULT}
	 * @throws Exception 调用大模型时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取必要的输入参数
		String plannerNodeOutput = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT);
		String userInput = StateUtil.getCanonicalQuery(state);
		Integer currentStep = StateUtil.getObjectValue(state, PLAN_CURRENT_STEP, Integer.class, 1);
		@SuppressWarnings("unchecked")
		HashMap<String, String> executionResults = StateUtil.getObjectValue(state, SQL_EXECUTE_NODE_OUTPUT,
				HashMap.class, new HashMap<>());

		// 解析计划并获取当前步骤
		Plan plan = converter.convert(plannerNodeOutput);
		ExecutionStep executionStep = getCurrentExecutionStep(plan, currentStep);
		String summaryAndRecommendations = executionStep.getToolParameters().getSummaryAndRecommendations();

		// 从状态中获取智能体 ID
		String agentIdStr = StateUtil.getStringValue(state, AGENT_ID);
		Long agentId = null;
		try {
			if (agentIdStr != null) {
				agentId = Long.parseLong(agentIdStr);
			}
		}
		catch (NumberFormatException ignore) {
			// 忽略解析错误，视为全局配置
		}

		// 生成报告的流式输出
		Flux<ChatResponse> reportGenerationFlux = generateReport(userInput, plan, executionResults,
				summaryAndRecommendations, agentId);

		TextType reportTextType = TextType.MARK_DOWN;

		// 使用工具类创建流式生成器，收集报告内容
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, "开始生成报告...", "报告生成完成！", reportContent -> {
					log.info("生成的报告内容: {}", reportContent);
					Map<String, Object> result = new HashMap<>();
					result.put(RESULT, reportContent);
					result.put(SQL_EXECUTE_NODE_OUTPUT, null);
					result.put(PLAN_CURRENT_STEP, null);
					result.put(PLANNER_NODE_OUTPUT, null);
					return result;
				},
				Flux.concat(Flux.just(ChatResponseUtil.createPureResponse(reportTextType.getStartSign())),
						reportGenerationFlux,
						Flux.just(ChatResponseUtil.createPureResponse(reportTextType.getEndSign()))));

		return Map.of(RESULT, generator);
	}

	/**
	 * 从执行计划中获取当前执行步骤。
	 * @param plan 执行计划
	 * @param currentStep 当前步骤号
	 * @return 当前执行步骤
	 * @throws IllegalStateException 若执行计划为空或步骤索引越界
	 */
	private ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		if (executionPlan == null || executionPlan.isEmpty()) {
			throw new IllegalStateException("执行计划为空");
		}

		int stepIndex = currentStep - 1;
		if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
			throw new IllegalStateException("当前步骤索引越界: " + stepIndex);
		}

		return executionPlan.get(stepIndex);
	}

	/**
	 * 生成分析报告。
	 * <p>
	 * 构建用户需求和计划描述、分析步骤和数据结果描述，加载优化配置， 最终调用大模型生成报告。
	 * </p>
	 * @param userInput 用户输入
	 * @param plan 执行计划
	 * @param executionResults 执行结果
	 * @param summaryAndRecommendations 总结和建议
	 * @param agentId 智能体 ID
	 * @return 报告生成的流式响应
	 */
	private Flux<ChatResponse> generateReport(String userInput, Plan plan, HashMap<String, String> executionResults,
			String summaryAndRecommendations, Long agentId) {
		// 构建用户需求和计划描述
		String userRequirementsAndPlan = buildUserRequirementsAndPlan(userInput, plan);

		// 构建分析步骤和数据结果描述
		String analysisStepsAndData = buildAnalysisStepsAndData(plan, executionResults);

		// 获取优化配置（优先按智能体加载）
		List<UserPromptConfig> optimizationConfigs = promptConfigService.getOptimizationConfigs("report-generator",
				agentId);

		// 构建报告生成提示词
		String reportPrompt = PromptHelper.buildReportGeneratorPromptWithOptimization(userRequirementsAndPlan,
				analysisStepsAndData, summaryAndRecommendations, optimizationConfigs);
		log.debug("报告节点提示词: \n {} \n", reportPrompt);
		return llmService.callUser(reportPrompt);
	}

	/**
	 * 构建用户需求和计划描述。
	 * @param userInput 用户输入
	 * @param plan 执行计划
	 * @return 用户需求和计划描述字符串
	 */
	private String buildUserRequirementsAndPlan(String userInput, Plan plan) {
		StringBuilder sb = new StringBuilder();
		sb.append("## 用户原始需求\n");
		sb.append(userInput).append("\n\n");

		sb.append("## 执行计划概述\n");
		sb.append("**思考过程**: ").append(plan.getThoughtProcess()).append("\n\n");

		sb.append("## 详细执行步骤\n");
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		for (int i = 0; i < executionPlan.size(); i++) {
			ExecutionStep step = executionPlan.get(i);
			sb.append("### 步骤 ").append(i + 1).append(": 步骤编号 ").append(step.getStep()).append("\n");
			sb.append("**工具**: ").append(step.getToolToUse()).append("\n");
			if (step.getToolParameters() != null) {
				sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
			}
			sb.append("\n");
		}

		return sb.toString();
	}

	/**
	 * 构建分析步骤和数据结果描述。
	 * @param plan 执行计划
	 * @param executionResults 执行结果
	 * @return 分析步骤和数据结果描述字符串
	 */
	private String buildAnalysisStepsAndData(Plan plan, HashMap<String, String> executionResults) {
		StringBuilder sb = new StringBuilder();
		sb.append("## 数据执行结果\n");

		if (executionResults.isEmpty()) {
			sb.append("暂无执行结果数据\n");
		}
		else {
			List<ExecutionStep> executionPlan = plan.getExecutionPlan();
			for (int i = 0; i < executionPlan.size(); i++) {
				ExecutionStep step = executionPlan.get(i);
				String stepId = String.valueOf(i + 1);
				String stepKey = "step_" + stepId;
				String stepResult = executionResults.get(stepKey);
				String analysisResult = executionResults.get(stepKey + "_analysis");

				if ((stepResult == null || stepResult.trim().isEmpty())
						&& (analysisResult == null || analysisResult.trim().isEmpty())) {
					continue;
				}

				sb.append("### ").append(stepKey).append("\n");
				sb.append("**步骤编号**: ").append(step.getStep()).append("\n");
				sb.append("**使用工具**: ").append(step.getToolToUse()).append("\n");
				if (step.getToolParameters() != null) {
					sb.append("**参数描述**: ").append(step.getToolParameters().getInstruction()).append("\n");
					if (step.getToolParameters().getSqlQuery() != null) {
						sb.append("**执行SQL**: \n```sql\n")
							.append(step.getToolParameters().getSqlQuery())
							.append("\n```\n");
					}
				}

				if (stepResult != null && !stepResult.trim().isEmpty()) {
					sb.append(DataSummarizer.summarize(stepResult)).append("\n");
				}
				if (analysisResult != null && !analysisResult.trim().isEmpty()) {
					sb.append("**Python 分析结果**: ").append(analysisResult).append("\n\n");
				}
			}
		}

		return sb.toString();
	}

}
