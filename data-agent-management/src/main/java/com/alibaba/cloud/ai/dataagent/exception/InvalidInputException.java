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

import lombok.Getter;

/**
 * 无效输入异常。
 * <p>
 * 表示客户端传入的参数不合法或不符合业务规则，对应 HTTP 400 状态码。 可携带额外的上下文数据（{@link #data}）供调用方进行更细致的错误处理。
 */
public class InvalidInputException extends RuntimeException {

	/** 附加的上下文数据，供调用方使用 */
	@Getter
	private Object data;

	/**
	 * 构造无效输入异常。
	 * @param message 异常描述信息
	 */
	public InvalidInputException(String message) {
		super(message);
	}

	/**
	 * 构造无效输入异常，并携带附加上下文数据。
	 * @param message 异常描述信息
	 * @param data 附加的上下文数据
	 */
	public InvalidInputException(String message, Object data) {
		super(message);
		this.data = data;
	}

}
