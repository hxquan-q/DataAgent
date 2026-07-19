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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.service.eval.EvaluationScoreService;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 评测打分 REST 接口，对外暴露检索/生成质量指标计算（供前端 Eval Playground 使用）。
 * <p>
 * 纯函数计算，无运行时依赖（不打 LLM/DB）。
 * </p>
 */
@RestController
@RequestMapping("/api/eval")
public class EvaluationController {

	private final EvaluationScoreService evaluationScoreService;

	public EvaluationController(EvaluationScoreService evaluationScoreService) {
		this.evaluationScoreService = evaluationScoreService;
	}

	/** 检索评测请求。 */
	public record RetrievalRequest(List<String> retrieved, List<String> relevant, Integer k) {
	}

	/** 生成评测请求。 */
	public record GenerationRequest(String candidate, String reference, String tokenize, Integer n, Boolean smoothing) {
	}

	/**
	 * 计算检索质量分数（recall/precision/MRR/MAP/NDCG）。
	 * @param request 检索评测请求（retrieved 排序列表 + relevant 黄金集合 + 可选 top-k，默认 10）
	 * @return 各检索指标分数
	 */
	@PostMapping("/retrieval")
	public ApiResponse<EvaluationScoreService.RetrievalScores> retrieval(@RequestBody RetrievalRequest request) {
		int k = request.k() == null ? 10 : request.k();
		return ApiResponse.success("ok",
				evaluationScoreService.retrieval(request.retrieved(), request.relevant(), k));
	}

	/**
	 * 计算生成质量分数（BLEU-n + ROUGE-n + ROUGE-L）。
	 * @param request 生成评测请求（candidate + reference 文本 + 可选 tokenize/n/smoothing）
	 * @return 各生成指标分数
	 */
	@PostMapping("/generation")
	public ApiResponse<EvaluationScoreService.GenerationScores> generation(@RequestBody GenerationRequest request) {
		String tokenize = request.tokenize() == null ? "sql" : request.tokenize();
		int n = request.n() == null ? 1 : request.n();
		boolean smoothing = request.smoothing() == null || request.smoothing();
		return ApiResponse.success("ok",
				evaluationScoreService.generation(request.candidate(), request.reference(), tokenize, n, smoothing));
	}

}
