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
package com.alibaba.cloud.ai.dataagent.dto.datasource;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 更新数据源表列表 DTO
 *
 * <p>
 * 用于更新指定数据源下选中的表列表。
 * </p>
 */
@Data
public class UpdateDatasourceTablesDTO {

	/** 数据源ID */
	@NotNull(message = "datasourceId cannot be null")
	private Integer datasourceId;

	/** 选中的表名列表 */
	private List<String> tables;

}
