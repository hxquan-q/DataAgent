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

import com.alibaba.cloud.ai.dataagent.enums.EmbeddingStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体知识 VO
 *
 * <p>
 * 用于前端展示智能体知识的视图对象，包含知识标题、类型、内容、召回标志、 向量化状态等信息。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledgeVO {

	/** 主键ID */
	private Integer id;

	/** 关联的智能体ID */
	private Integer agentId;

	/** 知识标题 */
	private String title;

	/** 知识类型：DOCUMENT-文档，QA-问答，FAQ-常见问题 */
	private String type;

	/** FAQ / QA 的提问内容 */
	private String question;

	/** 知识内容（当类型为 QA、FAQ 时有值） */
	private String content;

	/** 是否召回 */
	@JsonFormat(shape = JsonFormat.Shape.BOOLEAN)
	private Boolean isRecall;

	/** 向量化状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败 */
	private EmbeddingStatus embeddingStatus;

	/** 操作失败的错误信息 */
	private String errorMsg;

	/** 分块策略类型：token、recursive */
	private String splitterType;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

}
