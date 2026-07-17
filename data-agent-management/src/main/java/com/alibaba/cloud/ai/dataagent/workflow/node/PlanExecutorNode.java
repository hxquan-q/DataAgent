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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.plan.ConcurrentSqlStepExecutor;
import com.alibaba.cloud.ai.dataagent.util.DatabaseUtil;
import com.alibaba.cloud.ai.dataagent.util.PlanDependencyAnalyzer;
import com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 计划执行与校验节点，位于计划生成之后、具体工具节点之前。
 *
 * <p>
 * 该节点负责校验执行计划的结构和每一步的参数，并根据计划内容和当前步骤决定下一个执行节点。 若开启人工复核，则在校验通过后暂停执行，跳转到人工反馈节点。
 * </p>
 *
 * @author zhangshenghang
 * @see PlannerNode
 * @see HumanFeedbackNode
 */
@Slf4j
@Component
public class PlanExecutorNode implements NodeAction {

	// 支持的节点类型
	private static final Set<String> SUPPORTED_NODES = Set.of(SQL_GENERATE_NODE, PYTHON_GENERATE_NODE,
			REPORT_GENERATOR_NODE);

	// #10 并发执行（字段注入以保留无参构造，测试 new PlanExecutorNode() 时为 null，flag 关→不触发）
	@Autowired
	private ConcurrentSqlStepExecutor concurrentSqlStepExecutor;

	@Autowired
	private DataAgentProperties properties;

	@Autowired
	private DatabaseUtil databaseUtil;

	/**
	 * 执行计划校验与路由逻辑。
	 * <p>
	 * 先校验计划结构，再校验每个执行步骤的参数。校验通过后根据当前步骤决定下一个节点； 若开启人工复核则跳转到人工反馈节点。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含下一个执行节点和校验状态的 Map
	 * @throws Exception 解析计划时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// TODO 待优化，校验应该在生成计划之后而不是这里，这里导致每次运行一个计划都校验一次
		// 1. 校验计划结构
		Plan plan;
		try {
			plan = PlanProcessUtil.getPlan(state);
		}
		catch (Exception e) {
			log.error("计划校验失败，解析错误。", e);
			return buildValidationResult(state, false, "校验失败：计划不是有效的 JSON 结构。错误: " + e.getMessage());
		}

		// 校验执行计划结构
		if (!validateExecutionPlanStructure(plan)) {
			return buildValidationResult(state, false, "校验失败：生成的计划为空或没有执行步骤。");
		}

		// 校验每个执行步骤
		for (ExecutionStep step : plan.getExecutionPlan()) {
			String validationResult = validateExecutionStep(step);
			if (validationResult != null) {
				return buildValidationResult(state, false, validationResult);
			}
		}

		log.info("计划校验成功。");
		// 2. 若开启人工复核，则在执行前暂停，跳转到人工反馈节点
		Boolean humanReviewEnabled = state.value(HUMAN_REVIEW_ENABLED, false);
		if (Boolean.TRUE.equals(humanReviewEnabled)) {
			log.info("已开启人工复核：路由到人工反馈节点");
			return Map.of(PLAN_VALIDATION_STATUS, true, PLAN_NEXT_NODE, HUMAN_FEEDBACK_NODE);
		}

		// 获取当前步骤号和执行计划
		int currentStep = PlanProcessUtil.getCurrentStepNumber(state);
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();

		boolean isOnlyNl2Sql = state.value(IS_ONLY_NL2SQL, false);

		// 检查计划是否已完成
		if (currentStep > executionPlan.size()) {
			log.info("计划已完成，当前步骤: {}, 总步骤数: {}", currentStep, executionPlan.size());
			return Map.of(PLAN_CURRENT_STEP, 1, PLAN_NEXT_NODE, isOnlyNl2Sql ? StateGraph.END : REPORT_GENERATOR_NODE,
					PLAN_VALIDATION_STATUS, true);
		}

		// 获取当前步骤并确定下一个节点
		ExecutionStep executionStep = executionPlan.get(currentStep - 1);
		String toolToUse = executionStep.getToolToUse();

		// #10 并发执行（flag 默认关）：当前步骤起一个 >1 步的独立 SQL 波次时，并发执行整波后跳过
		if (properties != null && properties.isEnableConcurrentSteps() && concurrentSqlStepExecutor != null
				&& databaseUtil != null && SQL_GENERATE_NODE.equals(toolToUse)) {
			// R171: 显式日志便于确认 local concurrent 已生效
			log.info("尝试并发 SQL 波次执行（enableConcurrentSteps=true），currentStep={}", currentStep);
			Map<String, Object> concurrent = tryConcurrentWave(state, plan, currentStep, executionPlan);
			if (concurrent != null) {
				log.info("并发 SQL 波次已接管执行，跳过串行单步分发");
				return concurrent;
			}
		}

		return determineNextNode(toolToUse);
	}

	/**
	 * 根据要使用的工具确定下一个执行节点。
	 * @param toolToUse 要使用的工具名称
	 * @return 包含下一个执行节点和校验状态的 Map
	 */
	private Map<String, Object> determineNextNode(String toolToUse) {
		if (SUPPORTED_NODES.contains(toolToUse)) {
			// R221: 可观测 — step 路由日志
			log.info("计划步骤路由: currentStep={}, nextNode={}, concurrent={}", currentStep, toolToUse,
					properties != null && properties.isEnableConcurrentSteps());
			return Map.of(PLAN_NEXT_NODE, toolToUse, PLAN_VALIDATION_STATUS, true);
		}
		else if (HUMAN_FEEDBACK_NODE.equals(toolToUse)) {
			log.info("计划步骤路由(HumanFeedback): currentStep={}, nextNode={}", currentStep, toolToUse);
			return Map.of(PLAN_NEXT_NODE, toolToUse, PLAN_VALIDATION_STATUS, true);
		}
		else {
			// 正常情况下不会到达此处，因为前面已经做了校验
			return Map.of(PLAN_VALIDATION_STATUS, false, PLAN_VALIDATION_ERROR, "不支持的节点类型: " + toolToUse);
		}
	}

	/**
	 * 校验执行计划结构是否有效。
	 * @param plan 执行计划
	 * @return 若计划非空且包含执行步骤则返回 true
	 */
	private boolean validateExecutionPlanStructure(Plan plan) {
		return plan != null && plan.getExecutionPlan() != null && !plan.getExecutionPlan().isEmpty();
	}

	/**
	 * 校验单个执行步骤。
	 * @param step 执行步骤
	 * @return 校验失败时返回错误信息，校验通过返回 null
	 */
	/**
	 * 尝试并发执行当前步骤所在的独立 SQL 波次（#10）。fail-safe：任何异常回退串行。
	 * @param state 工作流状态
	 * @param plan 执行计划
	 * @param currentStep 当前步骤号
	 * @param executionPlan 执行步骤列表
	 * @return 并发执行后的路由结果；不适合并发时返回 null（走串行）
	 */
	private Map<String, Object> tryConcurrentWave(OverAllState state, Plan plan, int currentStep,
			List<ExecutionStep> executionPlan) {
		try {
			List<List<ExecutionStep>> waves = PlanDependencyAnalyzer.concurrentWaves(plan);
			for (List<ExecutionStep> wave : waves) {
				if (wave.stream().noneMatch(s -> s.getStep() == currentStep)) {
					continue;
				}
				// 仅当波次 >1 步且全部为 SQL 生成时并发
				if (wave.size() <= 1 || !wave.stream().allMatch(s -> SQL_GENERATE_NODE.equals(s.getToolToUse()))) {
					return null;
				}
				return runConcurrentWave(state, wave, executionPlan);
			}
		}
		catch (Exception e) {
			log.warn("并发波次识别失败，回退串行：{}", e.getMessage());
		}
		return null;
	}

	/**
	 * 并发执行一个独立 SQL 波次并产出路由结果（#10）。fail-safe：异常回退串行。
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> runConcurrentWave(OverAllState state, List<ExecutionStep> wave,
			List<ExecutionStep> executionPlan) {
		try {
			Long agentId = Long.valueOf(StateUtil.getStringValue(state, AGENT_ID));
			DbConfigBO dbConfig = databaseUtil.getAgentDbConfig(agentId);
			SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
			String evidence = StateUtil.getStringValue(state, EVIDENCE);
			String dialect = StateUtil.getStringValue(state, DB_DIALECT_TYPE);
			String canonicalQuery = StateUtil.getCanonicalQuery(state);
			Map<String, String> results = StateUtil.getObjectValue(state, SQL_EXECUTE_NODE_OUTPUT, Map.class,
					new HashMap<>());
			concurrentSqlStepExecutor.executeWave(wave, agentId, dbConfig, schemaDTO, evidence, dialect, canonicalQuery,
					results);
			int lastStep = wave.stream().mapToInt(ExecutionStep::getStep).max().getAsInt();
			int nextStep = lastStep + 1;
			boolean isOnlyNl2sql = state.value(IS_ONLY_NL2SQL, false);
			if (nextStep > executionPlan.size()) {
				return Map.of(SQL_EXECUTE_NODE_OUTPUT, results, PLAN_CURRENT_STEP, nextStep, PLAN_NEXT_NODE,
						isOnlyNl2sql ? StateGraph.END : REPORT_GENERATOR_NODE, PLAN_VALIDATION_STATUS, true);
			}
			String nextTool = executionPlan.get(nextStep - 1).getToolToUse();
			return Map.of(SQL_EXECUTE_NODE_OUTPUT, results, PLAN_CURRENT_STEP, nextStep, PLAN_NEXT_NODE, nextTool,
					PLAN_VALIDATION_STATUS, true);
		}
		catch (Exception e) {
			log.warn("并发波次执行失败，回退串行：{}", e.getMessage());
			return null;
		}
	}

	private String validateExecutionStep(ExecutionStep step) {
		// 校验工具名称
		if (step.getToolToUse() == null || !SUPPORTED_NODES.contains(step.getToolToUse())) {
			return "校验失败：步骤 " + step.getStep() + " 中包含无效的工具名称: '" + step.getToolToUse() + "'";
		}

		// 校验工具参数
		if (step.getToolParameters() == null) {
			return "校验失败：步骤 " + step.getStep() + " 缺少工具参数";
		}

		// 根据节点类型校验特定参数
		switch (step.getToolToUse()) {
			case SQL_GENERATE_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getInstruction())) {
					return "校验失败：步骤 " + step.getStep() + " 的 SQL 生成节点缺少描述信息";
				}
				break;

			case PYTHON_GENERATE_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getInstruction())) {
					return "校验失败：步骤 " + step.getStep() + " 的 Python 生成节点缺少指令信息";
				}
				break;

			case REPORT_GENERATOR_NODE:
				if (!StringUtils.hasText(step.getToolParameters().getSummaryAndRecommendations())) {
					return "校验失败：步骤 " + step.getStep() + " 的报告生成节点缺少总结和建议信息";
				}
				break;

			default:
				// 前面的校验已经排除了这种情况
				break;
		}

		return null; // 校验通过
	}

	/**
	 * 构建校验结果 Map。
	 * @param state 工作流全局状态
	 * @param isValid 校验是否通过
	 * @param errorMessage 错误信息
	 * @return 校验结果 Map
	 */
	private Map<String, Object> buildValidationResult(OverAllState state, boolean isValid, String errorMessage) {
		if (isValid) {
			return Map.of(PLAN_VALIDATION_STATUS, true);
		}
		else {
			// 校验失败时，递增修复次数
			int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
			return Map.of(PLAN_VALIDATION_STATUS, false, PLAN_VALIDATION_ERROR, errorMessage, PLAN_REPAIR_COUNT,
					repairCount + 1);
		}
	}

}
