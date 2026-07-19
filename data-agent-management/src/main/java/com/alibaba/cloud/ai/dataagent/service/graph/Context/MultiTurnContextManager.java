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
package com.alibaba.cloud.ai.dataagent.service.graph.Context;

import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 多轮对话上下文管理器，为每个会话线程维护多轮对话上下文。
 *
 * <p>
 * 该管理器保留一份轻量级的历史记录，包含用户问题和对应的计划（Planner）输出， 以便下游提示词（Prompt）可以引用之前的多轮对话内容，从而实现上下文连贯的多轮交互。
 * </p>
 *
 * @author Makoto
 * @since 2025/11/28
 */
@Slf4j
@Component
@AllArgsConstructor
public class MultiTurnContextManager {

	/** R40: 单轮计划流式拼接上限。 */
	private static final int MAX_PLAN_BUILDER_CHARS = 6_000;


	private final DataAgentProperties properties;

	// todo：考虑持久化存储
	/** 以 threadId 为键的对话历史记录，存储已完成的多轮对话 */
	private final Map<String, Deque<ConversationTurn>> history = new ConcurrentHashMap<>();

	/** 以 threadId 为键的待处理轮次，记录当前轮次用户问题与计划输出构建器 */
	private final Map<String, PendingTurn> pendingTurns = new ConcurrentHashMap<>();

	/**
	 * 开始追踪指定会话线程的新一轮对话。
	 * @param threadId 会话线程标识
	 * @param userQuestion 本轮用户问题
	 */
	public void beginTurn(String threadId, String userQuestion) {
		if (StringUtils.isAnyBlank(threadId, userQuestion)) {
			return;
		}
		// R41: 用户问题入库前有界
		pendingTurns.put(threadId, new PendingTurn(PromptHelper.boundQuery(userQuestion.trim())));
	}

	/**
	 * 为当前轮次追加计划（Planner）输出的流式片段。
	 * @param threadId 会话线程标识
	 * @param chunk 计划流式输出片段
	 */
	public void appendPlannerChunk(String threadId, String chunk) {
		if (StringUtils.isAnyBlank(threadId, chunk)) {
			return;
		}
		PendingTurn pending = pendingTurns.get(threadId);
		if (pending != null && pending.planBuilder.length() < MAX_PLAN_BUILDER_CHARS) {
			int room = MAX_PLAN_BUILDER_CHARS - pending.planBuilder.length();
			if (chunk.length() > room) {
				pending.planBuilder.append(chunk, 0, room);
			}
			else {
				pending.planBuilder.append(chunk);
			}
		}
	}

	/**
	 * 完成当前轮次，若存在计划输出则将其加入历史记录。
	 * @param threadId 会话线程标识
	 */
	public void finishTurn(String threadId) {
		PendingTurn pending = pendingTurns.remove(threadId);
		if (pending == null) {
			return;
		}
		String plan = StringUtils.trimToEmpty(pending.planBuilder.toString());
		if (StringUtils.isBlank(plan)) {
			log.debug("No planner output recorded for thread {}, skipping history update", threadId);
			return;
		}

		// 根据配置限制计划长度，避免上下文过长
		String trimmedPlan = StringUtils.abbreviate(plan, properties.getMaxplanlength());
		Deque<ConversationTurn> deque = history.computeIfAbsent(threadId, k -> new ArrayDeque<>());
		synchronized (deque) {
			// 当历史记录超过最大轮数限制时，移除最早的记录
			while (deque.size() >= properties.getMaxturnhistory()) {
				deque.pollFirst();
			}
			deque.addLast(new ConversationTurn(pending.userQuestion, trimmedPlan));
		}
	}

	/**
	 * 移除待处理的轮次数据，但不影响已持久化的历史记录。通常在运行被中止时调用。
	 * @param threadId 会话线程标识
	 */
	public void discardPending(String threadId) {
		pendingTurns.remove(threadId);
	}

	/**
	 * 重启最近一轮对话，使新的计划输出可以替换它（例如人工反馈后）。 将移除最后一条存储的轮次，并复用其中的用户问题。
	 * @param threadId 会话线程标识
	 */
	public void restartLastTurn(String threadId) {
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return;
		}
		ConversationTurn lastTurn;
		synchronized (deque) {
			lastTurn = deque.pollLast();
		}
		if (lastTurn != null) {
			// 复用上一轮的用户问题，重新开启待处理轮次
			pendingTurns.put(threadId, new PendingTurn(PromptHelper.boundQuery(lastTurn.userQuestion())));
		}
	}

	/**
	 * 构建用于提示词注入的多轮上下文字符串。
	 * @param threadId 会话线程标识
	 * @return 格式化后的历史记录字符串，无历史时返回 "(无)"
	 */
	public String buildContext(String threadId) {
		Deque<ConversationTurn> deque = history.get(threadId);
		if (deque == null || deque.isEmpty()) {
			return "(无)";
		}
		// R39: 构建时即有界，避免历史无限增长灌入所有节点
		String raw = deque.stream()
			.map(turn -> "用户: " + turn.userQuestion() + "\nAI计划: " + turn.plan())
			.collect(Collectors.joining("\n"));
		return PromptHelper.boundMultiTurn(raw);
	}

	/**
	 * 对话轮次记录，包含用户问题与计划输出
	 *
	 * @param userQuestion 用户问题
	 * @param plan 计划输出
	 */
	private record ConversationTurn(String userQuestion, String plan) {
	}

	/**
	 * 待处理轮次，记录当前轮次的用户问题和计划输出构建器。
	 */
	private static class PendingTurn {

		/** 本轮用户问题 */
		private final String userQuestion;

		/** 计划输出构建器，用于流式拼接计划文本 */
		private final StringBuilder planBuilder = new StringBuilder();

		private PendingTurn(String userQuestion) {
			this.userQuestion = userQuestion;
		}

	}

}
