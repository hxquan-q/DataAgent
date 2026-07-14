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
package com.alibaba.cloud.ai.dataagent.service.llm;

import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * LLM 调用服务接口，提供与大语言模型交互的能力，支持系统提示词、用户提示词等不同调用方式，
 * 返回 Reactor Flux 流式的 {@link ChatResponse}。
 */
public interface LlmService {

	/**
	 * 同时传入系统提示词和用户提示词调用 LLM。
	 * @param system 系统提示词
	 * @param user 用户提示词
	 * @return 流式 ChatResponse
	 */
	Flux<ChatResponse> call(String system, String user);

	/**
	 * 仅传入系统提示词调用 LLM。
	 * @param system 系统提示词
	 * @return 流式 ChatResponse
	 */
	Flux<ChatResponse> callSystem(String system);

	/**
	 * 仅传入用户提示词调用 LLM。
	 * @param user 用户提示词
	 * @return 流式 ChatResponse
	 */
	Flux<ChatResponse> callUser(String user);

	/**
	 * 阻塞等待流式响应并拼接为完整字符串（已废弃，请使用 {@link #toStringFlux(Flux)}）。
	 * @param responseFlux 流式 ChatResponse
	 * @return 拼接后的完整字符串
	 */
	@Deprecated
	default String blockToString(Flux<ChatResponse> responseFlux) {
		return toStringFlux(responseFlux).collect(StringBuilder::new, StringBuilder::append)
			.map(StringBuilder::toString)
			.block();
	}

	/**
	 * 将流式 ChatResponse 转换为流式字符串。
	 * @param responseFlux 流式 ChatResponse
	 * @return 流式字符串
	 */
	default Flux<String> toStringFlux(Flux<ChatResponse> responseFlux) {
		return responseFlux.map(ChatResponseUtil::getText);
	}

}
