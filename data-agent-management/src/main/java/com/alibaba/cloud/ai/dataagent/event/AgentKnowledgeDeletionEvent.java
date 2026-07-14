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
 * 智能体知识删除事件。
 * <p>
 * 当智能体知识被删除时发布此事件，由事件监听器异步执行向量数据和物理文件的清理工作。
 */
@Getter
public class AgentKnowledgeDeletionEvent extends ApplicationEvent {

	/** 被删除的知识 ID */
	private final Integer knowledgeId;

	/**
	 * 构造知识删除事件。
	 * @param source 事件源（通常为发布事件的 Service 对象）
	 * @param knowledgeId 被删除的知识 ID
	 */
	public AgentKnowledgeDeletionEvent(Object source, Integer knowledgeId) {
		super(source, Clock.systemDefaultZone());
		this.knowledgeId = knowledgeId;
	}

}
