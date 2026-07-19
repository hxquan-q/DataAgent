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

import lombok.Builder;
import lombok.Data;

/**
 * 智能体知识检索请求 DTO
 *
 * <p>
 * 封装基于智能体的向量知识库检索参数，包括智能体ID、文档向量类型、 相似度阈值、查询内容和返回数量。
 * </p>
 */
@Data
@Builder
public class AgentSearchRequest implements java.io.Serializable {

	@java.io.Serial
	private static final long serialVersionUID = 1L;

	/** 智能体ID */
	private String agentId;

	/** 文档向量类型 */
	private String docVectorType;

	/** 相似度阈值（默认 0.2） */
	@Builder.Default
	private Double similarityThreshold = 0.2;

	/** 查询内容 */
	private String query;

	/** 返回结果数量上限 */
	private Integer topK;

}
