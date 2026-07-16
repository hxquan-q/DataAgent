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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * {@link Rouge} 单测——确定性手算向量。
 */
class RougeTest {

	@Test
	void rougeN_unigram_partial() {
		// cand [a,b,c,d] ref [a,b,e]: overlap=2, eval=4, ref=3 → p=0.5, r=0.6667, f≈0.5714
		Rouge.Result r = Rouge.rougeN(List.of("a", "b", "c", "d"), List.of("a", "b", "e"), 1);
		assertThat(r.precision()).isCloseTo(0.5, within(1e-9));
		assertThat(r.recall()).isCloseTo(2.0 / 3.0, within(1e-9));
		assertThat(r.fMeasure()).isCloseTo(0.5714, within(1e-3));
	}

	@Test
	void rougeN_identical_is_one() {
		Rouge.Result r = Rouge.rougeN(List.of("a", "b", "c"), List.of("a", "b", "c"), 1);
		assertThat(r.fMeasure()).isCloseTo(1.0, within(1e-6));
	}

	@Test
	void rougeL_lcs_overlap() {
		// cand [a,b,c,d] ref [a,c,d]: LCS="a c d" len3 → p=3/4, r=3/3, f≈0.857
		Rouge.Result r = Rouge.rougeL(List.of("a", "b", "c", "d"), List.of("a", "c", "d"));
		assertThat(r.precision()).isCloseTo(0.75, within(1e-9));
		assertThat(r.recall()).isCloseTo(1.0, within(1e-9));
		assertThat(r.fMeasure()).isCloseTo(0.857, within(1e-3));
	}

	@Test
	void rougeL_disjoint_is_zero() {
		Rouge.Result r = Rouge.rougeL(List.of("a", "b"), List.of("c", "d"));
		assertThat(r.fMeasure()).isZero();
	}

	@Test
	void sql_tokenizer_keeps_identifiers() {
		// order_count 保持单 token；操作符/分号丢弃
		List<String> t = Tokenizers.sql("SELECT order_count FROM `orders` WHERE id=1;");
		assertThat(t).contains("select", "order_count", "from", "orders", "where", "id", "1");
	}

	@Test
	void rougeN_on_sql_tokens() {
		// 生成 SQL 漏一列：recall 下降
		List<String> gold = Tokenizers.sql("select order_id amount from orders");
		List<String> gen = Tokenizers.sql("select order_id from orders");
		Rouge.Result r = Rouge.rougeN(gen, gold, 1);
		assertThat(r.recall()).isLessThan(1.0);
		assertThat(r.recall()).isGreaterThan(0.0);
	}

}
