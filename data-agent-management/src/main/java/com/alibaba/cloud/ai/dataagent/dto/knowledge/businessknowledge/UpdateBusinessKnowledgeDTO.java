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
package com.alibaba.cloud.ai.dataagent.dto.knowledge.businessknowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新业务知识 DTO
 *
 * <p>
 * 用于更新业务术语知识的请求数据传输对象，包含业务术语、描述、同义词 及关联的智能体ID。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateBusinessKnowledgeDTO {

	/** 业务术语 */
	@NotBlank(message = "Business term cannot be empty")
	private String businessTerm;

	/** 业务术语描述 */
	@NotBlank(message = "Description cannot be empty")
	private String description;

	/** 同义词，多个以逗号分隔 */
	private String synonyms;

	/** 关联的智能体ID */
	@NotNull(message = "Agent ID cannot be Null")
	private Long agentId;

}
