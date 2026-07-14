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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * SQL 生成 DTO
 *
 * <p>
 * 封装 NL2SQL 流程中用于 SQL 生成的上下文数据，包括用户查询、数据库结构、 先验知识、已生成 SQL 及其执行结果等信息。
 * </p>
 */
@AllArgsConstructor
@Builder
@Data
public class SqlGenerationDTO {

	/** 先验知识（业务术语提示等） */
	private String evidence;

	/** 用户自然语言查询 */
	private String query;

	/** 数据库结构信息 */
	private SchemaDTO schemaDTO;

	/** 已生成的 SQL 语句 */
	private String sql;

	/** 执行异常信息 */
	private String exceptionMessage;

	/** SQL 执行结果的描述 */
	private String executionDescription;

	/** 数据库方言类型 */
	private String dialect;

}
