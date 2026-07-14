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
package com.alibaba.cloud.ai.dataagent.exception;

/**
 * 内部服务器异常。
 * <p>
 * 表示服务端在处理请求过程中发生的内部错误，对应 HTTP 500 状态码。
 */
public class InternalServerException extends RuntimeException {

	/**
	 * 构造内部服务器异常。
	 * @param message 异常描述信息
	 */
	public InternalServerException(String message) {
		super(message);
	}

}
