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

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import com.alibaba.cloud.ai.dataagent.service.aimodelconfig.AiModelRegistry;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.BlockLlmService;
import com.alibaba.cloud.ai.dataagent.service.llm.impls.StreamLlmService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * LLM 服务工厂，实现 {@link FactoryBean} 根据配置创建对应的 LLM 服务实例。
 *
 * <p>
 * 根据配置中的 {@code llmServiceType} 决定创建 {@link BlockLlmService}（阻塞式） 还是
 * {@link StreamLlmService}（流式）。
 * </p>
 */
@Component
@AllArgsConstructor
public class LlmServiceFactory implements FactoryBean<LlmService> {

	/** DataAgent 配置属性 */
	private final DataAgentProperties properties;

	/** AI 模型注册中心 */
	private final AiModelRegistry aiModelRegistry;

	/**
	 * 根据配置创建对应的 LLM 服务实例。
	 * @return LLM 服务实例
	 */
	@Override
	public LlmService getObject() {
		if (LlmServiceEnum.BLOCK.equals(properties.getLlmServiceType())) {
			return new BlockLlmService(aiModelRegistry);
		}
		else {
			return new StreamLlmService(aiModelRegistry);
		}
	}

	/**
	 * 返回工厂生产的对象类型。
	 * @return LLM 服务接口类型
	 */
	@Override
	public Class<?> getObjectType() {
		return LlmService.class;
	}

}
