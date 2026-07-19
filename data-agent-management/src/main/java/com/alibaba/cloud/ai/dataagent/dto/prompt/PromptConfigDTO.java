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
package com.alibaba.cloud.ai.dataagent.dto.prompt;

/**
 * 提示词配置请求 DTO
 *
 * <p>
 * 用于创建和更新用户自定义提示词配置的请求数据传输对象。采用 record 形式定义不可变数据。
 * </p>
 *
 * @author Makoto
 */

public record PromptConfigDTO(
		/** 配置ID（更新时必填） */
		String id,
		/** 配置名称 */
		String name,
		/** 提示词类型 */
		String promptType,
		/** 关联的智能体ID，为 null 表示全局配置 */
		Long agentId,
		/** 用户自定义的系统提示词内容 */
		String optimizationPrompt,
		/** 是否启用该配置 */
		Boolean enabled,
		/** 配置描述 */
		String description,
		/** 创建者 */
		String creator,
		/** 配置优先级 */
		Integer priority,
		/** 展示排序值 */
		Integer displayOrder) {

}
