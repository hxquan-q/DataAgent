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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;

/**
 * 计划步骤依赖分析器（#10 并发执行的纯逻辑核心）。
 *
 * <p>
 * 根据 {@link ExecutionStep#getDependsOn()} 把计划步骤划分成若干"波次"（wave）：
 * 同一波次内的步骤互不依赖，可安全并发执行；下一波次依赖当前或更早波次。 这是并发执行的运行时无关核心——实际的并发调度（线程池/图 fork）建立在此分析之上。
 * </p>
 *
 * <p>
 * fail-safe：遇到循环依赖或指向不存在步骤的依赖时，把剩余步骤按原序作为一个串行波次收尾，不抛异常、不阻塞。
 * </p>
 *
 * @author xquan
 */
public final class PlanDependencyAnalyzer {

	private PlanDependencyAnalyzer() {
	}

	/**
	 * 把计划步骤按依赖划分成可并发的波次。
	 * @param plan 执行计划
	 * @return 波次列表（每波内步骤可并发；为空计划返回空列表）
	 */
	public static List<List<ExecutionStep>> concurrentWaves(Plan plan) {
		List<ExecutionStep> steps = (plan == null || plan.getExecutionPlan() == null) ? List.of()
				: plan.getExecutionPlan();
		if (steps.isEmpty()) {
			return List.of();
		}
		List<ExecutionStep> remaining = new ArrayList<>(steps);
		Set<Integer> scheduled = new HashSet<>();
		List<List<ExecutionStep>> waves = new ArrayList<>();
		while (!remaining.isEmpty()) {
			List<ExecutionStep> wave = new ArrayList<>();
			for (ExecutionStep step : remaining) {
				List<Integer> deps = step.getDependsOn() == null ? List.of() : step.getDependsOn();
				if (scheduled.containsAll(deps)) {
					wave.add(step);
				}
			}
			if (wave.isEmpty()) {
				// 循环依赖或依赖了不存在的前置步骤：剩余按原序串行收尾（fail-safe）
				waves.add(new ArrayList<>(remaining));
				break;
			}
			wave.sort(Comparator.comparingInt(ExecutionStep::getStep));
			waves.add(wave);
			for (ExecutionStep step : wave) {
				scheduled.add(step.getStep());
				remaining.remove(step);
			}
		}
		return waves;
	}

	/**
	 * 计划的最大可并发度（单波次内最大步骤数）。
	 * @param plan 执行计划
	 * @return 最大并发度；计划为空返回 0
	 */
	public static int maxConcurrency(Plan plan) {
		return concurrentWaves(plan).stream().mapToInt(List::size).max().orElse(0);
	}

}
