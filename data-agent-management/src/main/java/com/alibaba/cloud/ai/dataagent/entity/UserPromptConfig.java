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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 用户提示词配置实体类
 *
 * <p>
 * 存储用户自定义的提示词（Prompt）配置，可关联到特定智能体或作为全局配置使用。 支持按类型、优先级、展示顺序进行管理。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPromptConfig {

	/** 配置ID */
	private String id;

	/** 配置名称 */
	private String name;

	/** 提示词类型（例如 report-generator、planner 等） */
	private String promptType;

	/** 关联的智能体ID，为 null 表示全局配置 */
	private Long agentId;

	/** 用户自定义的系统提示词内容 */
	private String systemPrompt;

	/** 是否启用该配置 */
	@Builder.Default
	private Boolean enabled = true;

	/** 配置描述 */
	private String description;

	/** 配置优先级（数值越大优先级越高） */
	@Builder.Default
	private Integer priority = 0;

	/** 展示排序值 */
	@Builder.Default
	private Integer displayOrder = 0;

	/** 创建时间 */
	private LocalDateTime createTime;

	/** 更新时间 */
	private LocalDateTime updateTime;

	/** 创建者 */
	private String creator;

	public String getOptimizationPrompt() {
		return this.systemPrompt;
	}

	public void setOptimizationPrompt(String optimizationPrompt) {
		this.systemPrompt = optimizationPrompt;
	}

}
