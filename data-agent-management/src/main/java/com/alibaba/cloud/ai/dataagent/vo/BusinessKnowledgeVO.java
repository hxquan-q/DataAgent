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
package com.alibaba.cloud.ai.dataagent.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 业务知识 VO
 *
 * <p>
 * 用于前端展示业务术语知识的视图对象，包含业务术语、描述、同义词、召回标志、 向量化状态等信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessKnowledgeVO {

	/** 主键ID */
	private Long id;

	/** 业务术语 */
	private String businessTerm;

	/** 业务术语描述 */
	private String description;

	/** 同义词，多个以逗号分隔 */
	private String synonyms;

	/** 是否召回 */
	@JsonFormat(shape = JsonFormat.Shape.BOOLEAN)
	private Boolean isRecall;

	/** 关联的智能体ID */
	private Long agentId;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	/** 向量化状态 */
	private String embeddingStatus;

	/** 操作失败的错误信息 */
	private String errorMsg;

}
