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
package com.alibaba.cloud.ai.dataagent.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.Clock;

/**
 * 智能体知识向量化嵌入事件。
 * <p>
 * 当智能体知识创建或更新后发布此事件，由事件监听器异步执行向量化（Embedding）处理，
 * 将文本内容写入向量库。
 */
@Getter
public class AgentKnowledgeEmbeddingEvent extends ApplicationEvent {

	/** 需要进行向量化的知识 ID */
	private final Integer knowledgeId;

	/** 文本分块策略类型 */
	private final String splitterType;

	/**
	 * 构造知识向量化嵌入事件。
	 * @param source 事件源（通常为发布事件的 Service 对象）
	 * @param knowledgeId 知识 ID
	 * @param splitterType 文本分块策略类型
	 */
	public AgentKnowledgeEmbeddingEvent(Object source, Integer knowledgeId, String splitterType) {
		super(source, Clock.systemDefaultZone());
		this.knowledgeId = knowledgeId;
		this.splitterType = splitterType;
	}

}
