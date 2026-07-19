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

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import org.junit.jupiter.api.Test;

/**
 * {@link PlanDependencyAnalyzer} 单测（#10 并发依赖分析核心）。
 *
 * @author xquan
 */
class PlanDependencyAnalyzerTest {

	private static ExecutionStep step(int n, List<Integer> deps) {
		ExecutionStep s = new ExecutionStep();
		s.setStep(n);
		s.setDependsOn(deps);
		return s;
	}

	private static Plan plan(ExecutionStep... steps) {
		Plan p = new Plan();
		p.setExecutionPlan(List.of(steps));
		return p;
	}

	private static List<Integer> stepNumbers(List<ExecutionStep> wave) {
		return wave.stream().map(ExecutionStep::getStep).toList();
	}

	@Test
	void allIndependent_singleWave() {
		Plan p = plan(step(1, null), step(2, null), step(3, null));
		List<List<ExecutionStep>> waves = PlanDependencyAnalyzer.concurrentWaves(p);
		assertThat(waves).hasSize(1);
		assertThat(stepNumbers(waves.get(0))).containsExactly(1, 2, 3);
		assertThat(PlanDependencyAnalyzer.maxConcurrency(p)).isEqualTo(3);
	}

	@Test
	void chain_serialWaves() {
		// 2 depends on 1; 3 depends on 2 -> 3 waves of 1
		Plan p = plan(step(1, null), step(2, List.of(1)), step(3, List.of(2)));
		List<List<ExecutionStep>> waves = PlanDependencyAnalyzer.concurrentWaves(p);
		assertThat(waves).hasSize(3);
		assertThat(stepNumbers(waves.get(0))).containsExactly(1);
		assertThat(stepNumbers(waves.get(1))).containsExactly(2);
		assertThat(stepNumbers(waves.get(2))).containsExactly(3);
	}

	@Test
	void diamond_parallelMiddleWave() {
		// 2,3 depend on 1; 4 depends on 2,3 -> [1],[2,3],[4]
		Plan p = plan(step(1, null), step(2, List.of(1)), step(3, List.of(1)), step(4, List.of(2, 3)));
		List<List<ExecutionStep>> waves = PlanDependencyAnalyzer.concurrentWaves(p);
		assertThat(waves).hasSize(3);
		assertThat(stepNumbers(waves.get(0))).containsExactly(1);
		assertThat(stepNumbers(waves.get(1))).containsExactly(2, 3);
		assertThat(stepNumbers(waves.get(2))).containsExactly(4);
	}

	@Test
	void circularDependency_failSafeSerialTail() {
		// 2 depends on 3; 3 depends on 2 (circular) -> fail-safe: 1 in wave1, then 2,3
		// serial tail
		Plan p = plan(step(1, null), step(2, List.of(3)), step(3, List.of(2)));
		List<List<ExecutionStep>> waves = PlanDependencyAnalyzer.concurrentWaves(p);
		// 第一波是无依赖的 step 1，剩余 2,3 因循环依赖串行收尾
		assertThat(stepNumbers(waves.get(0))).containsExactly(1);
		assertThat(waves).hasSizeGreaterThanOrEqualTo(2);
		// 所有步骤都应被调度到某波次
		int total = waves.stream().mapToInt(List::size).sum();
		assertThat(total).isEqualTo(3);
	}

	@Test
	void emptyPlan_returnsEmpty() {
		assertThat(PlanDependencyAnalyzer.concurrentWaves(new Plan())).isEmpty();
		assertThat(PlanDependencyAnalyzer.concurrentWaves(null)).isEmpty();
		assertThat(PlanDependencyAnalyzer.maxConcurrency(null)).isZero();
	}

}
