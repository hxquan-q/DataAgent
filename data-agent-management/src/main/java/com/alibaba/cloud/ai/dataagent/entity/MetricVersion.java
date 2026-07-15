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
 * 指标口径版本实体。同一指标多个口径（如金额含税/不含税），杜绝业务歧义。
 *
 * @author dataagent
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetricVersion {

	/** 主键ID */
	private Long id;

	/** 指标ID */
	private Long metricId;

	/** 口径版本编码 */
	private String verCode;

	/** 时间字段（覆盖指标默认） */
	private String timeField;

	/** 过滤条件（JSON片段） */
	private String filterCondition;

	/** 是否默认口径 0否1是 */
	private Integer isDefault;

	/** 口径说明（自然语言，反问展示用） */
	private String description;

	/** 状态 0停用 1启用 */
	private Integer status;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createdTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updatedTime;

}
