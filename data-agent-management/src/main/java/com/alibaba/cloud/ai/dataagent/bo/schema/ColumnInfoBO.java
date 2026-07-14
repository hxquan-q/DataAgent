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
package com.alibaba.cloud.ai.dataagent.bo.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 数据库列信息业务对象。
 * <p>
 * 描述数据库表中单列的元数据信息，包括列名、所属表名、数据类型、
 * 是否为主键、是否非空，以及采样数据等。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnInfoBO {

	/** 列名称 */
	private String name;

	/** 所属表名 */
	private String tableName;

	/** 列描述/注释 */
	private String description;

	/** 列数据类型 */
	private String type;

	/** 是否为主键 */
	private boolean primary;

	/** 是否非空 */
	private boolean notnull;

	/** 采样数据（JSON 字符串） */
	private String samples;

}
