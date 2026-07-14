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
package com.alibaba.cloud.ai.dataagent.dto;

import com.alibaba.cloud.ai.dataagent.annotation.InEnum;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模型配置 DTO
 *
 * <p>
 * 用于创建和更新大语言模型（LLM）配置的请求数据传输对象。包含厂商标识、API 地址、 模型名称、类型、温度、代理等配置信息。
 * </p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelConfigDTO {

	/** 主键ID（更新时必填） */
	private Integer id;

	/** 厂商标识（例如 "openai"、"deepseek"） */
	@NotBlank(message = "provider must not be empty")
	private String provider;

	/** API 密钥 */
	private String apiKey;

	/** API 基础地址（例如 "https://api.openai.com"） */
	@NotBlank(message = "baseUrl must not be empty")
	private String baseUrl;

	/** 模型名称 */
	@NotBlank(message = "modelName must not be empty")
	private String modelName;

	/** 模型类型：CHAT-对话模型，EMBEDDING-向量模型 */
	@NotBlank(message = "modelType must not be empty")
	@InEnum(value = ModelType.class, message = "CHAT/EMBEDDING 之一")
	private String modelType;

	/** 对话补全路径（仅当厂商路径非标准时填写，例如 "/custom/chat"） */
	private String completionsPath;

	/** 向量化路径（仅当厂商路径非标准时填写） */
	private String embeddingsPath;

	/** 温度参数（控制生成随机性，默认 0.0） */
	@Builder.Default
	private Double temperature = 0.0;

	/** 最大生成 token 数（默认 2000） */
	@Builder.Default
	private Integer maxTokens = 2000;

	/** 是否启用该模型配置 */
	@Builder.Default
	private Boolean isActive = true;

	/** 是否启用模型代理（默认关闭，使用直连） */
	@Builder.Default
	private Boolean proxyEnabled = false;

	/** 代理主机地址 */
	private String proxyHost;

	/** 代理端口 */
	private Integer proxyPort;

	/** 代理用户名 */
	private String proxyUsername;

	/** 代理密码 */
	private String proxyPassword;

}
