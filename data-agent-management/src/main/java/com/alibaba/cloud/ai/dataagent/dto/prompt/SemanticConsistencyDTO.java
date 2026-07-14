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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * 语义一致性校验 DTO
 *
 * <p>
 * 封装用于校验生成 SQL 与用户意图语义一致性的上下文数据，包括数据库方言、 SQL 语句、执行结果描述、结构信息、用户查询和先验知识。
 * </p>
 */
@AllArgsConstructor
@Builder
@Data
public class SemanticConsistencyDTO {

	/** 数据库方言类型 */
	private String dialect;

	/** 待校验的 SQL 语句 */
	private String sql;

	/** SQL 执行结果的描述 */
	private String executionDescription;

	/** 数据库结构信息 */
	private String schemaInfo;

	/** 用户自然语言查询 */
	private String userQuery;

	/** 先验知识（业务术语提示等） */
	private String evidence;

}
