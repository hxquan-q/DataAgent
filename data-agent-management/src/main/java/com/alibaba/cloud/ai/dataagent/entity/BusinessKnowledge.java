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
package com.alibaba.cloud.ai.dataagent.entity;

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 业务知识管理实体类
 *
 * <p>
 * 存储业务术语及其描述、同义词等知识信息，用于在 NL2SQL 场景中辅助大模型理解业务语义。 业务知识可关联到特定智能体，并支持向量化处理。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BusinessKnowledge {

	/** 主键ID */
	private Long id;

	/** 业务术语 */
	private String businessTerm;

	/** 业务术语描述 */
	private String description;

	/** 同义词，多个以逗号分隔 */
	private String synonyms;

	/** 是否召回（0-不召回，1-召回） */
	@Builder.Default
	private Integer isRecall = 1;

	/** 关联的智能体ID */
	private Long agentId;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	/** 向量化状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败 */
	private EmbeddingStatus embeddingStatus;

	/** 操作失败的错误信息 */
	private String errorMsg;

	/** 逻辑删除标志（0-未删除，1-已删除） */
	@Builder.Default
	private Integer isDeleted = 0;

}
