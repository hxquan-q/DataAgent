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
package com.alibaba.cloud.ai.dataagent.service.eval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link EvaluationScoreService} 单测——确定性（复用 IrMetrics/Bleu/Rouge 手算向量）。
 */
class EvaluationScoreServiceTest {

	private final EvaluationScoreService service = new EvaluationScoreService();

	@Test
	void retrieval_assembles_all_metrics() {
		// 复用 IrMetricsTest 向量：retrieved=[b,a,c,d] relevant={a,c,e} k=4
		var scores = service.retrieval(List.of("b", "a", "c", "d"), List.of("a", "c", "e"), 4);
		assertThat(scores.recall()).isCloseTo(2.0 / 3.0, within(1e-9));
		assertThat(scores.precision()).isEqualTo(0.5);
		assertThat(scores.mrr()).isEqualTo(0.5);
		assertThat(scores.ndcgAtK()).isCloseTo(1.13092975 / 2.13092975, within(1e-6));
	}

	@Test
	void retrieval_null_inputs_safe() {
		var scores = service.retrieval(null, null, 10);
		assertThat(scores.recall()).isZero();
		assertThat(scores.mrr()).isZero();
	}

	@Test
	void generation_identical_sql_is_near_perfect() {
		String sql = "select order_id, amount from orders where status = 'completed'";
		var scores = service.generation(sql, sql, "sql", 4, true);
		assertThat(scores.bleu()).isGreaterThan(0.99);
		assertThat(scores.rougeFMeasure()).isGreaterThan(0.99);
		assertThat(scores.rougeLFMeasure()).isGreaterThan(0.99);
		assertThat(scores.candidateTokens()).isEqualTo(scores.referenceTokens());
	}

	@Test
	void generation_partial_sql_lowers_score() {
		String gold = "select order_id, amount from orders";
		String gen = "select order_id from orders";
		var scores = service.generation(gen, gold, "sql", 1, true);
		assertThat(scores.rougeRecall()).isLessThan(1.0);
		assertThat(scores.bleu()).isLessThan(1.0);
	}

}
