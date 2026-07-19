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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ROUGE 生成质量指标（参考 Tencent/WeKnora {@code metric/rouge_score.go}，源自 Google rouge_score / pltrdy）。
 * <p>
 * 输入为<strong>已分词</strong>序列。ROUGE-N 用 n-gram 多重集（multiset）交叠；ROUGE-L 用最长公共子序列（LCS）。
 * F 值加 1e-8 平滑（与 WeKnora 一致）。
 * </p>
 */
public final class Rouge {

	private Rouge() {
	}

	/** ROUGE 结果三元组：precision / recall / fMeasure。 */
	public record Result(double precision, double recall, double fMeasure) {
	}

	/** ROUGE-N（n-gram 多重集交叠）。 */
	public static Result rougeN(List<String> candidate, List<String> reference, int n) {
		Map<String, Integer> cand = ngramCounts(candidate, n);
		Map<String, Integer> ref = ngramCounts(reference, n);
		int overlap = 0;
		for (var e : cand.entrySet()) {
			overlap += Math.min(e.getValue(), ref.getOrDefault(e.getKey(), 0));
		}
		int evalCount = cand.values().stream().mapToInt(Integer::intValue).sum();
		int refCount = ref.values().stream().mapToInt(Integer::intValue).sum();
		double p = evalCount == 0 ? 0.0 : (double) overlap / evalCount;
		double r = refCount == 0 ? 0.0 : (double) overlap / refCount;
		return new Result(p, r, fMeasure(p, r));
	}

	/** ROUGE-L（基于 LCS）。 */
	public static Result rougeL(List<String> candidate, List<String> reference) {
		int l = lcsLength(candidate, reference);
		int n = candidate.size();
		int m = reference.size();
		double p = n == 0 ? 0.0 : (double) l / n;
		double r = m == 0 ? 0.0 : (double) l / m;
		return new Result(p, r, fMeasure(p, r));
	}

	private static double fMeasure(double p, double r) {
		if (p + r == 0.0) {
			return 0.0;
		}
		return 2.0 * p * r / (p + r + 1e-8);
	}

	private static Map<String, Integer> ngramCounts(List<String> tokens, int n) {
		Map<String, Integer> m = new HashMap<>();
		for (int i = 0; i + n <= tokens.size(); i++) {
			m.merge(String.join(" ", tokens.subList(i, i + n)), 1, Integer::sum);
		}
		return m;
	}

	private static int lcsLength(List<String> x, List<String> y) {
		int n = x.size();
		int m = y.size();
		int[][] dp = new int[n + 1][m + 1];
		for (int i = 1; i <= n; i++) {
			for (int j = 1; j <= m; j++) {
				if (x.get(i - 1).equals(y.get(j - 1))) {
					dp[i][j] = dp[i - 1][j - 1] + 1;
				}
				else {
					dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
				}
			}
		}
		return dp[n][m];
	}

}
