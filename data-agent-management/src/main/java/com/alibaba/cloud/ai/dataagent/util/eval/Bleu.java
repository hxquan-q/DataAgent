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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BLEU 生成质量指标（参考 Tencent/WeKnora {@code metric/bleu.go}，源自 Python NLTK）。
 * <p>
 * 输入为<strong>已分词</strong>序列（candidate + references）。modifiedPrecision 做 n-gram 裁剪计数，
 * 可选 smoothing（分子分母各 +1，避免零命中直接归零）。brevityPenalty 按"最接近 candidate 长度的 reference"。
 * </p>
 */
public final class Bleu {

	public static final double[] BLEU1 = { 1.0, 0.0, 0.0, 0.0 };

	public static final double[] BLEU2 = { 0.5, 0.5, 0.0, 0.0 };

	public static final double[] BLEU3 = { 0.33, 0.33, 0.33, 0.0 };

	public static final double[] BLEU4 = { 0.25, 0.25, 0.25, 0.25 };

	private Bleu() {
	}

	/**
	 * 计算 BLEU。weights 长度即最大 n-gram 阶数（通常 4）。
	 * @param candidate 候选 token 序列
	 * @param references 参考 token 序列集合
	 * @param weights 各阶 n-gram 权重（如 {@link #BLEU4}）
	 * @param smoothing 是否加 1 平滑
	 * @return BLEU 分数 [0,1]
	 */
	public static double compute(List<String> candidate, List<List<String>> references, double[] weights,
			boolean smoothing) {
		double[] ps = new double[weights.length];
		for (int i = 0; i < weights.length; i++) {
			ps[i] = modifiedPrecision(candidate, references, i + 1, smoothing);
		}
		double s = 0.0;
		int overlap = 0;
		for (int i = 0; i < weights.length; i++) {
			if (ps[i] > 0.0) {
				overlap++;
				s += weights[i] * Math.log(ps[i]);
			}
		}
		if (overlap == 0) {
			return 0.0;
		}
		return brevityPenalty(candidate, references) * Math.exp(s);
	}

	/** 单参考句便捷重载（references 包装为单元素列表）。 */
	public static double computeSingle(List<String> candidate, List<String> reference, double[] weights,
			boolean smoothing) {
		return compute(candidate, List.of(reference), weights, smoothing);
	}

	static double modifiedPrecision(List<String> candidate, List<List<String>> references, int n, boolean smoothing) {
		List<String> candNgrams = ngrams(candidate, n);
		if (candNgrams.isEmpty()) {
			return 0.0;
		}
		Map<String, Integer> counts = count(candNgrams);
		if (counts.isEmpty()) {
			return 0.0;
		}
		// 每个候选 n-gram 在各 reference 中的最大计数
		Map<String, Integer> maxCounts = new HashMap<>();
		for (List<String> ref : references) {
			Map<String, Integer> refCounts = count(ngrams(ref, n));
			for (String g : counts.keySet()) {
				maxCounts.merge(g, refCounts.getOrDefault(g, 0), Math::max);
			}
		}
		int clipped = 0;
		int total = 0;
		for (var e : counts.entrySet()) {
			clipped += Math.min(e.getValue(), maxCounts.getOrDefault(e.getKey(), 0));
			total += e.getValue();
		}
		double sf = smoothing ? 1.0 : 0.0;
		return (clipped + sf) / (total + sf);
	}

	static double brevityPenalty(List<String> candidate, List<List<String>> references) {
		int c = candidate.size();
		if (c == 0) {
			return 0.0;
		}
		int bestLen = 0;
		int bestDiff = -1;
		for (List<String> ref : references) {
			int diff = Math.abs(ref.size() - c);
			if (bestDiff == -1 || diff < bestDiff) {
				bestDiff = diff;
				bestLen = ref.size();
			}
		}
		int r = bestLen;
		if (c > r) {
			return 1.0;
		}
		return Math.exp(1.0 - (double) r / c);
	}

	static List<String> ngrams(List<String> tokens, int n) {
		List<String> out = new ArrayList<>();
		for (int i = 0; i + n <= tokens.size(); i++) {
			out.add(String.join(" ", tokens.subList(i, i + n)));
		}
		return out;
	}

	private static Map<String, Integer> count(List<String> grams) {
		Map<String, Integer> m = new HashMap<>();
		for (String g : grams) {
			m.merge(g, 1, Integer::sum);
		}
		return m;
	}

}
