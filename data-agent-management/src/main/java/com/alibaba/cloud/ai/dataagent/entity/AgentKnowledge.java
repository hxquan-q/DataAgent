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
import com.alibaba.cloud.ai.dataagent.enums.KnowledgeType;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体知识实体类
 *
 * <p>
 * 存储与智能体关联的知识库内容，支持文档（DOCUMENT）、问答（QA）、常见问题（FAQ）三种类型。 知识内容会经历向量化处理，用于检索增强生成（RAG）场景。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentKnowledge {

	/** 主键ID */
	private Integer id;

	/** 关联的智能体ID */
	private Integer agentId;

	/** 知识标题 */
	private String title;

	/** 知识类型：DOCUMENT-文档，QA-问答，FAQ-常见问题 */
	private KnowledgeType type;

	/** FAQ / QA 的提问内容 */
	private String question;

	/** 知识内容（当类型为 QA、FAQ 时有值） */
	private String content;

	/** 是否召回（1-召回，0-不召回） */
	private Integer isRecall;

	/** 向量化状态：PENDING-待处理，PROCESSING-处理中，COMPLETED-已完成，FAILED-失败 */
	private EmbeddingStatus embeddingStatus;

	/** 操作失败的错误信息 */
	private String errorMsg;

	/** 源文件名 */
	private String sourceFilename;

	/** 文件路径 */
	private String filePath;

	/** 文件大小（字节） */
	private Long fileSize;

	/** 文件类型 */
	private String fileType;

	/** 分块策略类型：token、recursive（默认 token） */
	private String splitterType;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	/** 逻辑删除标志（0-未删除，1-已删除） */
	private Integer isDeleted;

	/** 物理资源清理标志（0-文件和向量未清理，1-已清理，默认 0） */
	private Integer isResourceCleaned;

}
