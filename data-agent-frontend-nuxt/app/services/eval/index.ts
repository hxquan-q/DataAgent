/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * @description 评测打分服务——检索/生成质量指标计算（NL2SQL eval：召回率/精确率/MRR/MAP/NDCG + BLEU/ROUGE）
 */

import axios from 'axios';
import type { ApiResponse } from '~/services/common/index';

const API_BASE_URL = '/api/eval';

/** 检索评测请求 */
export interface RetrievalRequest {
  /** 召回的有序 ID 列表 */
  retrieved: string[];
  /** 黄金相关 ID 列表 */
  relevant: string[];
  /** top-k，默认 10 */
  k?: number;
}

/** 检索评测结果（7 个指标） */
export interface RetrievalScores {
  recall: number;
  precision: number;
  recallAtK: number;
  precisionAtK: number;
  mrr: number;
  map: number;
  ndcgAtK: number;
}

/** 生成评测请求 */
export interface GenerationRequest {
  /** 候选文本（生成的 SQL/答案） */
  candidate: string;
  /** 参考文本（黄金 SQL/答案） */
  reference: string;
  /** 分词策略：sql（标识符，默认）/ words（空白） */
  tokenize?: string;
  /** n-gram 阶数 1-4，默认 1 */
  n?: number;
  /** BLEU 是否加 1 平滑，默认 true */
  smoothing?: boolean;
}

/** 生成评测结果 */
export interface GenerationScores {
  bleu: number;
  rougePrecision: number;
  rougeRecall: number;
  rougeFMeasure: number;
  rougeLPrecision: number;
  rougeLRecall: number;
  rougeLFMeasure: number;
  candidateTokens: number;
  referenceTokens: number;
}

/**
 * @description 评测打分业务类
 */
class EvalService {
  /**
   * @description 计算检索质量分数
   */
  async retrieval(req: RetrievalRequest): Promise<RetrievalScores> {
    const response = await axios.post<ApiResponse<RetrievalScores>>(`${API_BASE_URL}/retrieval`, req);
    return response.data.data;
  }

  /**
   * @description 计算生成质量分数
   */
  async generation(req: GenerationRequest): Promise<GenerationScores> {
    const response = await axios.post<ApiResponse<GenerationScores>>(`${API_BASE_URL}/generation`, req);
    return response.data.data;
  }
}

export default new EvalService();
