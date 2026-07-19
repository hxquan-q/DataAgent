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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLAN_CURRENT_STEP;

/**
 * 计划（Plan）执行节点工具类。
 * <p>
 * 为基于预定义计划执行的节点提供通用功能，包括：从全局状态解析计划、获取当前执行步骤、 获取当前步骤的工具参数说明，以及累积各步骤执行结果。
 * </p>
 *
 * @author zhangshenghang
 */
public final class PlanProcessUtil {

	/** 计划对象与 JSON 之间的转换器 */
	private static final BeanOutputConverter<Plan> converter;

	/** 步骤结果在 Map 中的键前缀 */
	private static final String STEP_PREFIX = "step_";

	static {
		converter = new BeanOutputConverter<>(new ParameterizedTypeReference<>() {
		});
	}

	private PlanProcessUtil() {

	}

	/**
	 * 从全局状态中获取当前执行步骤。
	 * @param state 全局状态
	 * @return 当前执行步骤
	 * @throws IllegalStateException 当计划输出为空、计划解析失败或步骤索引越界时抛出
	 */
	public static ExecutionStep getCurrentExecutionStep(OverAllState state) {
		Plan plan = getPlan(state);
		int currentStep = getCurrentStepNumber(state);
		return getCurrentExecutionStep(plan, currentStep);
	}

	/**
	 * 获取当前执行步骤的工具参数说明。
	 * @param state 全局状态
	 * @return 当前步骤的工具参数说明；若工具参数不存在则返回 "无"
	 */
	public static String getCurrentExecutionStepInstruction(OverAllState state) {
		String instruction;
		ExecutionStep.ToolParameters currentStepParams = PlanProcessUtil.getCurrentExecutionStep(state)
			.getToolParameters();
		// 工具参数缺失时使用占位文本
		instruction = currentStepParams != null ? currentStepParams.getInstruction() : "无";
		return instruction;
	}

	/**
	 * 根据计划对象与当前步骤序号获取对应的执行步骤。
	 * @param plan 计划对象
	 * @param currentStep 当前步骤序号（从 1 开始）
	 * @return 当前执行步骤
	 * @throws IllegalStateException 当执行计划为空或步骤索引越界时抛出
	 */
	public static ExecutionStep getCurrentExecutionStep(Plan plan, Integer currentStep) {
		List<ExecutionStep> executionPlan = plan.getExecutionPlan();
		if (executionPlan == null || executionPlan.isEmpty()) {
			throw new IllegalStateException("执行计划为空");
		}

		// 步骤序号从 1 开始，转换为列表下标
		int stepIndex = currentStep - 1;
		if (stepIndex < 0 || stepIndex >= executionPlan.size()) {
			throw new IllegalStateException("当前步骤索引超出范围: " + stepIndex);
		}

		return executionPlan.get(stepIndex);
	}

	/**
	 * 从全局状态中解析计划对象。
	 * @param state 全局状态
	 * @return 解析后的计划对象
	 * @throws IllegalStateException 当计划输出为空或解析失败时抛出
	 */
	public static Plan getPlan(OverAllState state) {
		String plannerNodeOutput = (String) state.value(PLANNER_NODE_OUTPUT)
			.orElseThrow(() -> new IllegalStateException("计划节点输出为空"));
		Plan plan = converter.convert(plannerNodeOutput);
		if (plan == null) {
			throw new IllegalStateException("计划解析失败");
		}
		return plan;
	}

	/**
	 * 从全局状态中获取当前步骤序号。
	 * @param state 全局状态
	 * @return 当前步骤序号，未设置时默认为 1
	 */
	public static int getCurrentStepNumber(OverAllState state) {
		return state.value(PLAN_CURRENT_STEP, 1);
	}

	/**
	 * 向结果集合中追加某一步骤的执行结果。
	 * @param existingResults 已有结果集合
	 * @param stepNumber 步骤序号
	 * @param result 该步骤的执行结果
	 * @return 包含新结果的不可变副本（基于拷贝）
	 */
	public static Map<String, String> addStepResult(Map<String, String> existingResults, Integer stepNumber,
			String result) {
		Map<String, String> updatedResults = new HashMap<>(existingResults);
		updatedResults.put(STEP_PREFIX + stepNumber, result);
		return updatedResults;
	}

}
