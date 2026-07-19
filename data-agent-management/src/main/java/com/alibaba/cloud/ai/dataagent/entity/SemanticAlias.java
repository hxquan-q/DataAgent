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
 * 语义别名映射实体。业务黑话→结构化 code（METRIC/DIM/VER/FILTER），连接业务语言与系统定义。
 *
 * @author dataagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemanticAlias {

	/** 主键ID */
	private Long id;

	/** 智能体ID */
	private Integer agentId;

	/** 用户说法/业务黑话 */
	private String aliasText;

	/** 目标类型 METRIC/DIM/VER/FILTER */
	private String targetType;

	/** 映射目标code */
	private String targetCode;

	/** 匹配类型 EXACT/FUZZY */
	private String matchType;

	/** 优先级（高胜出，解决多匹配） */
	private Integer priority;

	/** 状态 0停用 1启用 */
	private Integer status;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

}
