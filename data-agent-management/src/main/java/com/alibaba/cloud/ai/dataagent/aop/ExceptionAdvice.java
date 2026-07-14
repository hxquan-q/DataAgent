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
package com.alibaba.cloud.ai.dataagent.aop;

import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理切面。
 * <p>
 * 基于 Spring 的 {@link RestControllerAdvice} 统一捕获所有 Controller 层抛出的未处理异常，
 * 返回标准化的错误响应。
 */
@Slf4j
@RestControllerAdvice
public class ExceptionAdvice {

	/**
	 * 处理所有未捕获的通用异常。
	 * @param e 捕获到的异常
	 * @return 包含错误信息的 HTTP 500 响应
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse> handleException(Exception e) {
		log.error("发生异常: ", e);
		return ResponseEntity.internalServerError().body(ApiResponse.error("发生异常: " + e.getMessage()));
	}

}
