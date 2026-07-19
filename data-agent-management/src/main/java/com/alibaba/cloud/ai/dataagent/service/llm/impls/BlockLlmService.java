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
import reactor.core.publisher.Mono;

/**
 * 阻塞式 LLM 调用服务实现类，使用 ChatClient 的阻塞调用接口获取完整响应后包装为 Flux 返回。
 */
@AllArgsConstructor
public class BlockLlmService implements LlmService {

	/** AI 模型注册中心 */
	private final AiModelRegistry registry;

	/**
	 * 同时传入系统提示词和用户提示词，阻塞式调用 LLM 后包装为 Flux 返回。
	 * @param system 系统提示词
	 * @param user 用户提示词
	 * @return 包含单次完整响应的 Flux
	 */
	@Override
	public Flux<ChatResponse> call(String system, String user) {
		return Mono
			.fromCallable(() -> registry.getChatClient().prompt().system(system).user(user).call().chatResponse())
			.flux();
	}

	/**
	 * 仅传入系统提示词，阻塞式调用 LLM 后包装为 Flux 返回。
	 * @param system 系统提示词
	 * @return 包含单次完整响应的 Flux
	 */
	@Override
	public Flux<ChatResponse> callSystem(String system) {
		return Mono.fromCallable(() -> registry.getChatClient().prompt().system(system).call().chatResponse()).flux();
	}

	/**
	 * 仅传入用户提示词，阻塞式调用 LLM 后包装为 Flux 返回。
	 * @param user 用户提示词
	 * @return 包含单次完整响应的 Flux
	 */
	@Override
	public Flux<ChatResponse> callUser(String user) {
		return Mono.fromCallable(() -> registry.getChatClient().prompt().user(user).call().chatResponse()).flux();
	}

}
