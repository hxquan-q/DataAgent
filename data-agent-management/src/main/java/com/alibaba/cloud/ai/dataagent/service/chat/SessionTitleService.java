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
package com.alibaba.cloud.ai.dataagent.service.chat;

import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/**
 * 会话标题生成服务，通过 LLM 异步生成会话标题并推送给前端。
 *
 * <p>
 * 在用户首次发送消息后，异步调用 LLM 根据用户输入生成简短的会话标题， 生成成功后持久化并通过 SSE 推送标题更新事件。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionTitleService {

	/** 默认会话标题 */
	private static final String DEFAULT_TITLE = "新会话";

	/** 聊天会话服务 */
	private final ChatSessionService chatSessionService;

	/** 会话事件发布器 */
	private final SessionEventPublisher sessionEventPublisher;

	/** LLM 调用服务 */
	private final LlmService llmService;

	/** 数据库操作线程池 */
	@Qualifier("dbOperationExecutor")
	private final ExecutorService executorService;

	/** 正在运行标题生成任务的会话 ID 集合，防止重复生成 */
	private final Set<String> runningTasks = ConcurrentHashMap.newKeySet();

	/**
	 * 异步调度会话标题生成任务。
	 * @param sessionId 会话唯一标识
	 * @param userMessage 用户的首条消息
	 */
	public void scheduleTitleGeneration(String sessionId, String userMessage) {
		if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userMessage)) {
			return;
		}
		// 同一会话同一时间只允许一个生成任务
		if (!runningTasks.add(sessionId)) {
			return;
		}
		CompletableFuture.runAsync(() -> generateAndPersist(sessionId, userMessage), executorService)
			.whenComplete((unused, throwable) -> runningTasks.remove(sessionId));
	}

	/**
	 * 生成标题并持久化，生成成功后通过 SSE 推送更新事件。
	 * @param sessionId 会话唯一标识
	 * @param userMessage 用户的首条消息
	 */
	private void generateAndPersist(String sessionId, String userMessage) {
		try {
			ChatSession session = chatSessionService.findBySessionId(sessionId);
			if (session == null) {
				log.warn("Session {} not found when generating title", sessionId);
				return;
			}
			// 已有自定义标题则跳过生成
			if (hasCustomTitle(session)) {
				log.debug("Session {} already has custom title, skip generating", sessionId);
				return;
			}

			// 调用 LLM 生成标题
			String title = requestSummary(userMessage);
			// LLM 生成失败时使用回退标题
			if (!StringUtils.hasText(title)) {
				title = fallbackTitle(userMessage);
			}
			title = normalizeTitle(title);
			if (!StringUtils.hasText(title)) {
				log.warn("LLM returned empty title for session {}", sessionId);
				return;
			}

			// 持久化标题并推送更新事件
			chatSessionService.renameSession(sessionId, title);
			sessionEventPublisher.publishTitleUpdated(session.getAgentId(), sessionId, title);
			log.info("Generated session title '{}' for session {}", title, sessionId);
		}
		catch (Exception ex) {
			log.error("Failed to generate session title for session {}: {}", sessionId, ex.getMessage());
		}
	}

	/**
	 * 检查会话是否已有自定义标题（非默认标题）。
	 * @param session 会话对象
	 * @return 是否已有自定义标题
	 */
	private boolean hasCustomTitle(ChatSession session) {
		return StringUtils.hasText(session.getTitle()) && !DEFAULT_TITLE.equals(session.getTitle());
	}

	/**
	 * 调用 LLM 生成会话标题摘要。
	 * @param userMessage 用户的首条消息
	 * @return LLM 生成的标题，失败时返回 null
	 */
	private String requestSummary(String userMessage) {
		try {
			String systemPrompt = """
					你是一名对话助手，请根据用户的第一条输入生成不超过20个字的会话标题。
					使用中文输出，避免使用标点或引号，仅保留核心主题。
					""";
			String userPrompt = "用户输入：" + userMessage;
			Flux<String> responseFlux = llmService.toStringFlux(llmService.call(systemPrompt, userPrompt));
			// 阻塞等待 LLM 响应，超时 15 秒
			return responseFlux.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block(Duration.ofSeconds(15));
		}
		catch (Exception ex) {
			log.warn("LLM title generation failed: {}", ex.getMessage());
			return null;
		}
	}

	/**
	 * 规范化标题，去除换行和引号，并限制为 20 字以内。
	 * @param raw 原始标题
	 * @return 规范化后的标题，为空时返回 null
	 */
	private String normalizeTitle(String raw) {
		if (!StringUtils.hasText(raw)) {
			return null;
		}
		// 去除换行符和引号
		String sanitized = raw.replaceAll("[\\r\\n]+", " ").replaceAll("[\"“”]+", "").trim();
		if (sanitized.length() > 20) {
			sanitized = sanitized.substring(0, 20);
		}
		return sanitized;
	}

	/**
	 * LLM 生成失败时的回退标题，截取用户消息前 20 字。
	 * @param userMessage 用户的首条消息
	 * @return 回退标题
	 */
	private String fallbackTitle(String userMessage) {
		String text = userMessage.replaceAll("\\s+", " ").trim();
		if (text.length() > 20) {
			text = text.substring(0, 20);
		}
		return StringUtils.hasText(text) ? text : DEFAULT_TITLE;
	}

}
