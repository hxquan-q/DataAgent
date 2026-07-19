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

import java.util.List;
import java.util.Map;

/**
 * 数据库列信息 DTO
 *
 * <p>
 * 描述数据库表中单个字段的元数据，包括字段名、描述、枚举值、取值范围、数据类型及样例数据。 主要用于向大模型提供字段级别的上下文信息。
 * </p>
 */
@Data
@NoArgsConstructor
public class ColumnDTO {

	/** 字段名称 */
	private String name;

	/** 字段描述 */
	private String description;

	/** 是否为枚举字段（0-否，1-是） */
	private int enumeration;

	/** 取值范围 */
	private String range;

	/** 数据类型 */
	private String type;

	/** 样例数据列表 */
	private List<String> data;

	/** 枚举值与描述的映射关系 */
	private Map<String, String> mapping;

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
