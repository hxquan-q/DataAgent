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
package com.alibaba.cloud.ai.dataagent.dto.search;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.ai.vectorstore.filter.Filter;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 混合检索请求 DTO
 *
 * <p>
 * 封装向量检索与关键词检索相结合的混合检索参数，支持权重配置、相似度阈值、重排序 及扩展参数。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HybridSearchRequest implements Serializable {

	// === 基础参数 ===
	/** 查询文本 */
	private String query;

	/** 返回结果数量上限 */
	private Integer topK;

	/** 相似度阈值（默认 0.0） */
	@Builder.Default
	private double similarityThreshold = 0.0;

	/** 过滤表达式 */
	private Filter.Expression filterExpression;

	/** 向量检索权重（默认 0.5） */
	@Builder.Default
	private Double vectorWeight = 0.5;

	/** 关键词检索权重（默认 0.5） */
	@Builder.Default
	private Double keywordWeight = 0.5;

	/** 是否开启重排序模型（默认关闭） */
	@Builder.Default
	private boolean useRerank = false;

	/** 扩展参数包，用于特定数据库的特有参数 */
	@Builder.Default
	private Map<String, Object> extraParams = new HashMap<>();

	public org.springframework.ai.vectorstore.SearchRequest toVectorSearchRequest() {
		return org.springframework.ai.vectorstore.SearchRequest.builder()
			.query(this.query)
			.topK(this.topK)
			.similarityThreshold(this.similarityThreshold)
			.filterExpression(this.filterExpression)
			.build();
	}

}
