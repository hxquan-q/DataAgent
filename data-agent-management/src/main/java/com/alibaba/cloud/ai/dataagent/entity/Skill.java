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
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 技能实体，对应 {@code skill} 表。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

	/** 主键 ID。 */
	private Long id;

	/** 技能名称。 */
	@NotBlank(message = "技能名称不能为空")
	private String name;

	/** 技能描述，用于说明适用场景。 */
	private String description;

	/** 注入作用域：report、sql 或 python。 */
	@NotBlank(message = "技能作用域不能为空")
	private String scope;

	/** 触发关键词，逗号分隔。 */
	private String triggers;

	/** 技能正文指令。 */
	@NotBlank(message = "技能内容不能为空")
	private String content;

	/** 可选参数 JSON。 */
	private String paramsJson;

	/** 是否启用。 */
	@Builder.Default
	private Boolean enabled = true;

	/** 注入优先级，数值越大越靠前。 */
	@Builder.Default
	private Integer priority = 0;

	/** 展示顺序。 */
	@Builder.Default
	private Integer displayOrder = 0;

	/** 创建时间。 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createTime;

	/** 更新时间。 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updateTime;

}
