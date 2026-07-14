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
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 智能体（Agent）实体类
 *
 * <p>
 * 对应数据库中智能体的核心配置信息，包含智能体名称、描述、状态、API 密钥、提示词等。 一个智能体实例代表一个可被用户调用的数据分析助手。
 * </p>
 */
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class Agent {

	/** 主键ID */
	private Long id;

	/** 智能体名称 */
	private String name;

	/** 智能体描述 */
	private String description;

	/** 头像URL */
	private String avatar;

	/** 状态：draft-待发布，published-已发布，offline-已下线 */
	// todo: 改为枚举
	private String status;

	/** 外部访问使用的 API Key，格式为 sk-xxx */
	@JsonIgnore
	private String apiKey;

	/** 是否启用 API 访问（0-关闭，1-开启） */
	@Builder.Default
	private Integer apiKeyEnabled = 0;

	/** 自定义提示词（Prompt）配置 */
	private String prompt;

	/** 分类 */
	private String category;

	/** 管理员ID */
	private Long adminId;

	/** 标签，多个标签以逗号分隔 */
	private String tags;

	/** 创建时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime createTime;

	/** 更新时间 */
	@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
	private LocalDateTime updateTime;

}
