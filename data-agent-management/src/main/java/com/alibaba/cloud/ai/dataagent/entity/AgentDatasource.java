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

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * 智能体-数据源关联实体类
 *
 * <p>
 * 维护智能体与数据源之间的多对多关联关系。记录某个智能体启用了哪些数据源， 以及当前数据源下选中的表。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AgentDatasource {

	/** 主键ID */
	private Integer id;

	/** 关联的智能体ID */
	private Long agentId;

	/** 关联的数据源ID */
	private Integer datasourceId;

	/** 是否启用（0-关闭，1-启用） */
	private Integer isActive;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime createTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	@DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
	private LocalDateTime updateTime;

	/** 关联的数据源对象（用于联表查询） */
	private Datasource datasource;

	/** 当前数据源选中的表列表 */
	private List<String> selectTables;

	public AgentDatasource(Long agentId, Integer datasourceId) {
		this.agentId = agentId;
		this.datasourceId = datasourceId;
		this.isActive = 1;
	}

}
