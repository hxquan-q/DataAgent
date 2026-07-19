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
package com.alibaba.cloud.ai.dataagent.dto.datasource;

/**
 * SQL 重试 DTO
 *
 * <p>
 * 表示 SQL 执行失败后的重试上下文，区分语义校验失败和执行失败两种场景。 采用 record 形式定义不可变数据，并提供工厂方法快速构造。
 * </p>
 *
 * @param reason 失败原因描述
 * @param semanticFail 是否为语义校验失败
 * @param sqlExecuteFail 是否为 SQL 执行失败
 */
public record SqlRetryDto(String reason, boolean semanticFail, boolean sqlExecuteFail) {

	public static SqlRetryDto semantic(String reason) {
		return new SqlRetryDto(reason, true, false);
	}

	public static SqlRetryDto sqlExecute(String reason) {
		return new SqlRetryDto(reason, false, true);
	}

	public static SqlRetryDto empty() {
		return new SqlRetryDto("", false, false);
	}

}
