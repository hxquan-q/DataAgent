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
package com.alibaba.cloud.ai.dataagent.vo;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图节点响应 VO
 *
 * <p>
 * 表示工作流图中单个节点的执行响应，包含节点名称、文本类型、输出内容及状态标志。 用于流式推送每个节点的中间结果。
 * </p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GraphNodeResponse {

	/** 智能体ID */
	private String agentId;

	/** 会话ID（线程ID） */
	private String threadId;

	/** 当前节点名称（使用 Constant 常量定义） */
	private String nodeName;

	/** 文本类型 */
	private TextType textType;

	/** 节点输出文本 */
	private String text;

	/** 是否为错误响应（默认 false） */
	@Builder.Default
	private boolean error = false;

	/** 是否为完成标志（默认 false） */
	@Builder.Default
	private boolean complete = false;

	public static GraphNodeResponse error(String agentId, String threadId, String text) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.text(text)
			.error(true)
			.textType(TextType.TEXT)
			.build();
	}

	public static GraphNodeResponse complete(String agentId, String threadId) {
		return GraphNodeResponse.builder()
			.agentId(agentId)
			.threadId(threadId)
			.complete(true)
			.textType(TextType.TEXT)
			.build();
	}

}
