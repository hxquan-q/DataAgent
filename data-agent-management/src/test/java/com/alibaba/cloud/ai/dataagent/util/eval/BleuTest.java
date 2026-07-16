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
 * {@link Bleu} 单测——确定性手算向量。
 */
class BleuTest {

	@Test
	void identical_candidate_yields_one() {
		List<String> c = List.of("a", "b", "c");
		assertThat(Bleu.computeSingle(c, c, Bleu.BLEU1, false)).isCloseTo(1.0, within(1e-9));
	}

	@Test
	void bleu1_two_thirds_overlap() {
		// candidate [a,b,c] vs ref [a,b,d]: p1 = clipped(2)/total(3) = 2/3, bp=1 (等长)
		List<String> c = List.of("a", "b", "c");
		List<String> r = List.of("a", "b", "d");
		assertThat(Bleu.computeSingle(c, r, Bleu.BLEU1, false)).isCloseTo(2.0 / 3.0, within(1e-9));
	}

	@Test
	void no_overlap_returns_zero() {
		List<String> c = List.of("x", "y");
		List<String> r = List.of("a", "b");
		assertThat(Bleu.computeSingle(c, r, Bleu.BLEU1, false)).isZero();
	}

	@Test
	void brevity_penalty_when_candidate_shorter() {
		// candidate [a] vs ref [a,b,c]: p1=1 (命中 a), bp=exp(1-3/1)=exp(-2)≈0.1353
		double score = Bleu.computeSingle(List.of("a"), List.of("a", "b", "c"), Bleu.BLEU1, false);
		assertThat(score).isCloseTo(Math.exp(-2.0), within(1e-9));
	}

	@Test
	void bleu4_on_real_sentence_via_tokenizer() {
		// 真实句：BLEU-4（加平滑）介于 0~1，且完全相同 > 部分相同
		List<String> ref = Tokenizers.words("select id name from users");
		List<String> exact = Tokenizers.words("select id name from users");
		List<String> partial = Tokenizers.words("select id email from users");
		double exactScore = Bleu.computeSingle(exact, ref, Bleu.BLEU4, true);
		double partialScore = Bleu.computeSingle(partial, ref, Bleu.BLEU4, true);
		assertThat(exactScore).isGreaterThan(0.99);
		assertThat(exactScore).isGreaterThan(partialScore);
	}

}
