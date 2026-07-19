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
package com.alibaba.cloud.ai.dataagent.util.eval;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link IrMetrics} 单测——确定性手算向量。
 */
class IrMetricsTest {

	private static final List<String> RETRIEVED = List.of("b", "a", "c", "d");

	private static final Set<String> RELEVANT = Set.of("a", "c", "e");

	@Test
	void recall_and_precision() {
		// hit = {a,c} = 2; relevant=3 → recall=2/3; distinct retrieved=4 → precision=2/4
		assertThat(IrMetrics.recall(RETRIEVED, RELEVANT)).isCloseTo(2.0 / 3.0, within(1e-9));
		assertThat(IrMetrics.precision(RETRIEVED, RELEVANT)).isEqualTo(0.5);
	}

	@Test
	void recall_at_k() {
		// top2 = [b,a]; hit {a}=1 → recall@2 = 1/3
		assertThat(IrMetrics.recallAtK(RETRIEVED, RELEVANT, 2)).isCloseTo(1.0 / 3.0, within(1e-9));
	}

	@Test
	void mrr_first_hit_at_rank2() {
		// first hit 'a' at index 1 → 1/2
		assertThat(IrMetrics.reciprocalRank(RETRIEVED, RELEVANT)).isEqualTo(0.5);
	}

	@Test
	void mrr_no_hit_returns_zero() {
		assertThat(IrMetrics.reciprocalRank(List.of("x", "y"), RELEVANT)).isZero();
	}

	@Test
	void average_precision_standard() {
		// i1 'a': hits1, p=1/2=0.5 ; i2 'c': hits2, p=2/3 ; sum=1.1667 ; /relevant(3)=0.3889
		assertThat(IrMetrics.averagePrecision(RETRIEVED, RELEVANT)).isCloseTo((0.5 + 2.0 / 3.0) / 3.0, within(1e-9));
	}

	@Test
	void ndcg_at_k_matches_handcalc() {
		// DCG = 0/log2(2) + 1/log2(3) + 1/log2(4) + 0 = 0.6309 + 0.5 = 1.1309
		// idealLen=min(3,4)=3 → IDCG = 1/log2(2)+1/log2(3)+1/log2(4) = 2.1309 → NDCG≈0.5307
		assertThat(IrMetrics.ndcgAtK(RETRIEVED, RELEVANT, 4)).isCloseTo(1.13092975 / 2.13092975, within(1e-6));
	}

	@Test
	void ndcg_perfect_rank() {
		// 全部 relevant 排最前 → NDCG=1
		assertThat(IrMetrics.ndcgAtK(List.of("a", "c", "e"), Set.of("a", "c", "e"), 3)).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void empty_inputs_safe() {
		assertThat(IrMetrics.recall(List.of(), Set.of("a"))).isZero();
		assertThat(IrMetrics.recall(RETRIEVED, Set.of())).isZero();
		assertThat(IrMetrics.ndcgAtK(RETRIEVED, RELEVANT, 0)).isZero();
	}

}
