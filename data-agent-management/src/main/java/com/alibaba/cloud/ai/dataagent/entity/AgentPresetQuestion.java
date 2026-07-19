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

import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 智能体预设问题实体类
 *
 * <p>
 * 存储智能体的预设推荐问题，用于在对话界面中向用户展示常用问题入口。 每个预设问题关联一个智能体，并按排序值排列。
 * </p>
 */
@Data
@NoArgsConstructor
public class AgentPresetQuestion {

	/** 主键ID */
	private Long id;

	/** 关联的智能体ID */
	private Long agentId;

	/** 预设问题内容 */
	private String question;

	/** 排序值（数值越小越靠前） */
	private Integer sortOrder;

	/** 是否启用 */
	private Boolean isActive;

	/** 创建时间 */
	private LocalDateTime createTime;

	/** 更新时间 */
	private LocalDateTime updateTime;

	public AgentPresetQuestion(Long agentId, String question, Integer sortOrder) {
		this.agentId = agentId;
		this.question = question;
		this.sortOrder = sortOrder;
		this.isActive = false;
	}

}
