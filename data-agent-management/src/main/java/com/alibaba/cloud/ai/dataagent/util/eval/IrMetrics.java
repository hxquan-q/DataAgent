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

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 检索质量指标（参考 Tencent/WeKnora {@code metric/{recall,precision,mrr,map,ndcg}.go}）。
 * <p>
 * 全部为<strong>单查询纯函数</strong>（retrieved 排序列表 + relevant 黄金集合 → 分数）。跨查询聚合（求均值）由
 * 调用方（评测服务）负责，对应 WeKnora 在 Metric* 中对多查询求 mean 的做法。
 * </p>
 * <p>
 * NDCG 与 WeKnora 公式一致：DCG=Σ(2^rel−1)/log2(i+2)，IDCG 取理想排序。averagePrecision 按标准 IR
 * 定义除以 relevant 总数（修正 WeKnora 除以命中数的轻微偏差）。
 * </p>
 */
public final class IrMetrics {

	private IrMetrics() {
	}

	/** log2，NDCG 折扣用。 */
	private static double log2(double x) {
		return Math.log(x) / Math.log(2);
	}

	/** Recall = |retrieved ∩ relevant| / |relevant|；relevant 为空返回 0。 */
	public static double recall(List<String> retrieved, Set<String> relevant) {
		if (relevant == null || relevant.isEmpty()) {
			return 0.0;
		}
		Set<String> hit = new HashSet<>(retrieved == null ? Set.of() : retrieved);
		hit.retainAll(relevant);
		return (double) hit.size() / relevant.size();
	}

	/** Recall@k = |topK(retrieved) ∩ relevant| / |relevant|。 */
	public static double recallAtK(List<String> retrieved, Set<String> relevant, int k) {
		return recall(topK(retrieved, k), relevant);
	}

	/** Precision = |retrieved ∩ relevant| / |distinct retrieved|；retrieved 为空返回 0。 */
	public static double precision(List<String> retrieved, Set<String> relevant) {
		if (retrieved == null || retrieved.isEmpty()) {
			return 0.0;
		}
		Set<String> rs = new HashSet<>(retrieved);
		if (rs.isEmpty()) {
			return 0.0;
		}
		rs.retainAll(relevant == null ? Set.of() : relevant);
		return (double) rs.size() / new HashSet<>(retrieved).size();
	}

	/** Precision@k = |topK(retrieved) ∩ relevant| / |distinct topK(retrieved)|。 */
	public static double precisionAtK(List<String> retrieved, Set<String> relevant, int k) {
		return precision(topK(retrieved, k), relevant);
	}

	/** MRR（单查询）= 1/(首个命中位次)；无命中返回 0。 */
	public static double reciprocalRank(List<String> retrieved, Set<String> relevant) {
		if (retrieved == null || relevant == null || relevant.isEmpty()) {
			return 0.0;
		}
		for (int i = 0; i < retrieved.size(); i++) {
			if (relevant.contains(retrieved.get(i))) {
				return 1.0 / (i + 1);
			}
		}
		return 0.0;
	}

	/** Average Precision（单查询）= Σ_{命中位次 k} precision@k / |relevant|。 */
	public static double averagePrecision(List<String> retrieved, Set<String> relevant) {
		if (retrieved == null || relevant == null || relevant.isEmpty()) {
			return 0.0;
		}
		int hits = 0;
		double sum = 0.0;
		for (int i = 0; i < retrieved.size(); i++) {
			if (relevant.contains(retrieved.get(i))) {
				hits++;
				sum += (double) hits / (i + 1);
			}
		}
		return sum / relevant.size();
	}

	/** NDCG@k（单查询）。DCG=Σ(2^rel−1)/log2(i+2)，IDCG 为理想排序，IDCG=0 返回 0。 */
	public static double ndcgAtK(List<String> retrieved, Set<String> relevant, int k) {
		if (retrieved == null || relevant == null || relevant.isEmpty() || k <= 0) {
			return 0.0;
		}
		int top = Math.min(k, retrieved.size());
		double dcg = 0.0;
		for (int i = 0; i < top; i++) {
			int rel = relevant.contains(retrieved.get(i)) ? 1 : 0;
			dcg += (Math.pow(2, rel) - 1) / log2(i + 2);
		}
		int idealLen = Math.min(relevant.size(), top);
		double idcg = 0.0;
		for (int i = 0; i < idealLen; i++) {
			idcg += 1.0 / log2(i + 2);
		}
		return idcg == 0.0 ? 0.0 : dcg / idcg;
	}

	private static List<String> topK(List<String> retrieved, int k) {
		if (retrieved == null || k <= 0) {
			return List.of();
		}
		return retrieved.subList(0, Math.min(k, retrieved.size()));
	}

}
