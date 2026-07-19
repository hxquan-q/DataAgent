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
package com.alibaba.cloud.ai.dataagent.service.llm.impls;

import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.AllArgsConstructor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

/**
 * 流式 LLM 调用服务实现类，使用 ChatClient 的流式接口逐步返回响应内容。
 */
@AllArgsConstructor
public class StreamLlmService implements LlmService {

	/** AI 模型注册中心 */
	private final AiModelRegistry registry;

	/**
	 * 同时传入系统提示词和用户提示词，以流式方式调用 LLM。
	 * @param system 系统提示词
	 * @param user 用户提示词
	 * @return 流式 ChatResponse
	 */
	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return registry.getChatClient().prompt().system(system).user(user).stream().chatResponse();
	}

	/**
	 * 仅传入系统提示词，以流式方式调用 LLM。
	 * @param system 系统提示词
	 * @return 流式 ChatResponse
	 */
	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return registry.getChatClient().prompt().system(system).stream().chatResponse();
	}

	/**
	 * 仅传入用户提示词，以流式方式调用 LLM。
	 * @param user 用户提示词
	 * @return 流式 ChatResponse
	 */
	@Override
	public Flux<ChatResponse> callUser(String user) {
		return registry.getChatClient().prompt().user(user).stream().chatResponse();
	}

}
