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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 指标定义实体（NL2Semantic2SQL 指标层核心）。LLM 从候选指标中选择，后端按指标拼装 SQL。
 *
 * @author dataagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metric {

	/** 主键ID */
	private Long id;

	/** 指标编码，全局唯一 */
	private String metricCode;

	/** 指标中文名（候选列表展示） */
	private String metricName;

	/** 智能体ID */
	private Integer agentId;

	/** 数据源ID（多源路由） */
	private Integer datasourceId;

	/** 来源表（SQL拼装目标） */
	private String sourceTable;

	/** 聚合字段 */
	private String aggField;

	/** 聚合函数 SUM/COUNT/AVG/MAX/MIN */
	private String aggFunc;

	/** 默认时间字段 */
	private String defaultTimeField;

	/** SQL模板（复杂指标可选，覆盖默认拼装） */
	private String sqlTemplate;

	/** 指标说明 */
	private String description;

	/** 状态 0停用 1启用 */
	private Integer status;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

	/**
	 * 生成用于 SemanticParse prompt 的紧凑信息（候选列表展示）。
	 * @return 候选指标摘要
	 */
	public String getPromptInfo() {
		return String.format("指标: %s(%s) 表:%s %s(%s) 时间字段:%s", metricName, metricCode, sourceTable, aggFunc, aggField,
				defaultTimeField);
	}

}
