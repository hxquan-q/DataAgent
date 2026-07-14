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
package com.alibaba.cloud.ai.dataagent.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * JSON 工具类。
 * <p>
 * 持有全局共享的 {@link ObjectMapper} 实例，统一 Jackson 序列化/反序列化配置，避免重复创建。
 * </p>
 */
public class JsonUtil {

	/** 全局共享的 ObjectMapper 实例 */
	private static final ObjectMapper objectMapper = new ObjectMapper();

	/**
	 * 获取全局共享的 {@link ObjectMapper} 实例。
	 * @return ObjectMapper 实例
	 */
	public static ObjectMapper getObjectMapper() {
		return objectMapper;
	}

}
