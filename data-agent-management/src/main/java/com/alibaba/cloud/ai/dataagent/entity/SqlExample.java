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
 * SQL 样例训练库实体。成功 SQL 按 hash 去重反哺，作为 few-shot 召回（vanna 三库反哺，域Ⅴ§16 在线辨识）。
 *
 * @author dataagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SqlExample {

	/** 主键ID */
	private Long id;

	/** 智能体ID */
	private Integer agentId;

	/** 数据源ID */
	private Integer datasourceId;

	/** 问题 */
	private String question;

	/** 成功SQL */
	private String sqlText;

	/** 数据库方言 */
	private String dialect;

	/** 去重指纹 */
	private String sqlHash;

	/** 来源 AUTO自动反哺/MANUAL人工 */
	private String source;

	/** 是否人工审核 0否1是 */
	private Integer reviewed;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
