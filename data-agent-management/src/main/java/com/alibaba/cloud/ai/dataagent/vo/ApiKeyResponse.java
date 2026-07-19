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
package com.alibaba.cloud.ai.dataagent.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API Key 响应 VO
 *
 * <p>
 * 返回智能体的 API Key 及其启用状态，用于前端展示和外部调用配置。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyResponse {

	/** API 密钥 */
	private String apiKey;

	/** 是否启用 API 访问（0-关闭，1-开启） */
	private Integer apiKeyEnabled;

}
