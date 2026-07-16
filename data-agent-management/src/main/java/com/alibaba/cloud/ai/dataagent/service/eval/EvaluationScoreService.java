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

import com.alibaba.cloud.ai.dataagent.util.eval.Bleu;
import com.alibaba.cloud.ai.dataagent.util.eval.IrMetrics;
import com.alibaba.cloud.ai.dataagent.util.eval.Rouge;
import com.alibaba.cloud.ai.dataagent.util.eval.Tokenizers;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 评测打分服务：把 {@link IrMetrics}/{@link Bleu}/{@link Rouge} 组装成对外可用的检索/生成分数。
 * <p>
 * 对应 WeKnora 缺失的"评测可视化"——纯函数无运行时依赖，确定性可测。为前端 Eval Playground（
 * {@code /system/eval-playground}）提供后端能力。
 * </p>
 */
@Service
public class EvaluationScoreService {

	/** 检索质量分数（recall/precision/@k/MRR/MAP/NDCG）。 */
	public record RetrievalScores(double recall, double precision, double recallAtK, double precisionAtK, double mrr,
			double map, double ndcgAtK) {
	}

	/** 生成质量分数（BLEU-n + ROUGE-n + ROUGE-L）。 */
	public record GenerationScores(double bleu, double rougePrecision, double rougeRecall, double rougeFMeasure,
			double rougeLPrecision, double rougeLRecall, double rougeLFMeasure, int candidateTokens, int referenceTokens) {
	}

	/** 计算检索指标（retrieved 排序列表 + relevant 黄金集合 + top-k）。 */
	public RetrievalScores retrieval(List<String> retrieved, List<String> relevant, int k) {
		Set<String> rel = relevant == null ? Set.of() : Set.copyOf(relevant);
		List<String> ret = retrieved == null ? List.of() : retrieved;
		return new RetrievalScores(IrMetrics.recall(ret, rel), IrMetrics.precision(ret, rel),
				IrMetrics.recallAtK(ret, rel, k), IrMetrics.precisionAtK(ret, rel, k),
				IrMetrics.reciprocalRank(ret, rel), IrMetrics.averagePrecision(ret, rel),
				IrMetrics.ndcgAtK(ret, rel, k));
	}

	/**
	 * 计算生成指标（candidate/reference 文本）。
	 * @param tokenize 分词策略："sql"（标识符，默认）或其它（按空白）
	 * @param n BLEU/ROUGE 阶数（1-4，超出按 4 处理）
	 * @param smoothing BLEU 是否加 1 平滑
	 */
	public GenerationScores generation(String candidate, String reference, String tokenize, int n, boolean smoothing) {
		List<String> c = tokenize(candidate, tokenize);
		List<String> r = tokenize(reference, tokenize);
		double bleu = Bleu.compute(c, List.of(r), weightsForN(n), smoothing);
		Rouge.Result rougeN = Rouge.rougeN(c, r, Math.max(1, n));
		Rouge.Result rougeL = Rouge.rougeL(c, r);
		return new GenerationScores(bleu, rougeN.precision(), rougeN.recall(), rougeN.fMeasure(), rougeL.precision(),
				rougeL.recall(), rougeL.fMeasure(), c.size(), r.size());
	}

	private List<String> tokenize(String text, String strategy) {
		if (text == null || text.isBlank()) {
			return List.of();
		}
		return "sql".equalsIgnoreCase(strategy) ? Tokenizers.sql(text) : Tokenizers.words(text);
	}

	private double[] weightsForN(int n) {
		return switch (n) {
			case 1 -> Bleu.BLEU1;
			case 2 -> Bleu.BLEU2;
			case 3 -> Bleu.BLEU3;
			default -> Bleu.BLEU4;
		};
	}

}
