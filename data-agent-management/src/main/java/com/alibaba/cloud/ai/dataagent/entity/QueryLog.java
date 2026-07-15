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
 * 查询日志+证据链实体。记录语义对象→SQL→结果→口径，支撑审计与可回放（datafoundry 式 Trace）。
 *
 * @author dataagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryLog {

	/** 主键ID */
	private Long id;

	/** 会话ID */
	private String sessionId;

	/** 智能体ID */
	private Integer agentId;

	/** 数据源ID */
	private Integer datasourceId;

	/** 用户原始问题 */
	private String userQuery;

	/** 语义对象快照（JSON，证据链） */
	private String semanticObject;

	/** 受控拼装SQL */
	private String generatedSql;

	/** 使用的口径版本（JSON） */
	private String metricVersions;

	/** 执行耗时(ms) */
	private Integer execTimeMs;

	/** 返回行数 */
	private Integer rowCount;

	/** 状态 SUCCESS/FAIL/CLARIFY */
	private String status;

	/** 反馈 0无 1赞 2踩 */
	private Integer feedback;

	/** Langfuse trace关联 */
	private String traceId;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
