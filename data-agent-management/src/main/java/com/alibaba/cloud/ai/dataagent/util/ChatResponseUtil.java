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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;

/**
 * ChatResponse 工具类，提供 {@link ChatResponse} 的构造与文本提取等辅助方法。
 *
 * @author zhangshenghang
 */
public class ChatResponseUtil {

	/**
	 * 构造一个携带状态消息的 {@link ChatResponse}，并在消息末尾追加换行符。
	 * @param statusMessage 状态消息文本
	 * @return 包含该消息的 ChatResponse 对象
	 */
	public static ChatResponse createResponse(String statusMessage) {
		return createPureResponse(statusMessage + "\n");
	}

	/**
	 * 构造一个携带纯文本消息的 {@link ChatResponse}。
	 * @param message 消息文本
	 * @return 包含该消息的 ChatResponse 对象
	 */
	public static ChatResponse createPureResponse(String message) {
		AssistantMessage assistantMessage = new AssistantMessage(message);
		Generation generation = new Generation(assistantMessage);
		return new ChatResponse(List.of(generation));
	}

	/**
	 * 构造一个去除指定文本类型标记后的 {@link ChatResponse}。
	 * <p>
	 * 当前实现无法达到预期效果，已弃用。若确需该逻辑，请重新定义。
	 * </p>
	 * @param message 原始消息文本
	 * @param textType 文本类型，用于指定需要移除的起止标记
	 * @return 移除指定标记后的 ChatResponse 对象
	 */
	@Deprecated
	public static ChatResponse createTrimResponse(String message, TextType textType) {
		return createPureResponse(message.replace(textType.getStartSign(), "").replace(textType.getEndSign(), ""));
	}

	/**
	 * 从 {@link ChatResponse} 中提取纯文本内容。
	 * <p>
	 * 当响应、生成结果或输出消息为空时，返回空字符串，避免 NPE。
	 * </p>
	 * @param chatResponse 聊天响应对象，可为 null
	 * @return 响应中的文本内容；若任何中间环节为空则返回空字符串
	 */
	public static String getText(ChatResponse chatResponse) {
		Generation result = chatResponse.getResult();
		if (result == null) {
			return "";
		}
		AssistantMessage output = result.getOutput();
		if (output == null) {
			return "";
		}
		return output.getText() == null ? "" : output.getText();
	}

}
