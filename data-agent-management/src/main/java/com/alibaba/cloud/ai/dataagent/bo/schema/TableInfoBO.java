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

import java.util.List;

/**
 * 数据库表信息业务对象。
 * <p>
 * 描述数据库表的完整元数据信息，包括所属 Schema、表名、表类型、
 * 外键信息、主键列表以及包含的列信息列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableInfoBO {

	/** 所属 Schema 名称 */
	private String schema;

	/** 表名称 */
	private String name;

	/** 表描述/注释 */
	private String description;

	/** 表类型（如 TABLE、VIEW 等） */
	private String type;

	/** 外键信息（JSON 字符串） */
	private String foreignKey;

	/** 主键列名列表 */
	private List<String> primaryKeys;

	/** 表包含的列信息列表 */
	private List<ColumnInfoBO> columns;

}
