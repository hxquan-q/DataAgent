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
package com.alibaba.cloud.ai.dataagent.dto.schema;

import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 数据库表信息 DTO
 *
 * <p>
 * 描述数据库中单张表的元数据，包括表名、描述、字段列表和主键列表。 主要用于向大模型提供表级别的上下文信息。
 * </p>
 */
@Data
@NoArgsConstructor
public class TableDTO {

	/** 表名 */
	private String name;

	/** 表描述 */
	private String description;

	/** 字段列表 */
	private List<ColumnDTO> column = new ArrayList<ColumnDTO>();

	/** 主键字段列表 */
	private List<String> primaryKeys;

	@Override
	public String toString() {
		ObjectMapper objectMapper = JsonUtil.getObjectMapper();
		try {
			return objectMapper.writeValueAsString(this);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to convert object to JSON string", e);
		}
	}

}
