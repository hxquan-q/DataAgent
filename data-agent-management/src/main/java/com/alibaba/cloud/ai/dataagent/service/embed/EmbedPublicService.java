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
package com.alibaba.cloud.ai.dataagent.service.embed;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.entity.ChatSession;
import com.alibaba.cloud.ai.dataagent.exception.EmbedException;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.chat.ChatSessionService;
import com.alibaba.cloud.ai.dataagent.util.EmbedSessionTokenUtil;
import com.alibaba.cloud.ai.dataagent.vo.EmbedConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * embed 公开接口服务：发布令牌换会话令牌、会话令牌校验、embed 配置读取、embed 会话创建、单层限流。
 *
 * <p>
 * 发布令牌 = {@link Agent#getApiKey()}（仅在 {@code exchange} 比对一次），换得
 * {@link EmbedSessionTokenUtil} 签发的短期会话令牌。限流为 per-Agent 每分钟滑动窗口（R1 单层兜底）；
 * // ponytail: 全局 synchronized per-bucket，QPS 升高时换 Bucket4j + Redis（R3）。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EmbedPublicService {

	private final AgentMapper agentMapper;

	private final ChatSessionService chatSessionService;

	private final EmbedSessionTokenUtil tokenUtil;

	private final ObjectMapper objectMapper;

	@Value("${spring.ai.alibaba.data-agent.embed.rate-limit-per-minute:30}")
	private int defaultRateLimitPerMinute;

	/** per-Agent 请求时间戳滑动窗口（分钟级）。 */
	private final ConcurrentHashMap<Long, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

	/** exchange 返回：会话令牌 + 有效期（秒）。 */
	public record ExchangeResult(String sessionToken, long expiresIn) {
	}

	/**
	 * 发布令牌换会话令牌。
	 * @param agentId 智能体 ID
	 * @param publishToken 发布令牌（= Agent.apiKey）
	 */
	public ExchangeResult exchange(Long agentId, String publishToken) {
		Agent agent = requireEmbedAgent(agentId);
		if (!Integer.valueOf(1).equals(agent.getApiKeyEnabled()) || agent.getApiKey() == null
				|| !EmbedSessionTokenUtil.constantTimeEquals(agent.getApiKey(), publishToken)) {
			throw new EmbedException(401, "invalid publish token");
		}
		rateLimitCheck(agentId, agent);
		EmbedSessionTokenUtil.IssueResult issued = tokenUtil.issue(agentId);
		return new ExchangeResult(issued.token(), issued.expiresIn());
	}

	/**
	 * 读 embed 配置（<strong>不含任何令牌</strong>），供 widget 初始化 UI。
	 */
	public EmbedConfig getEmbedConfig(Long agentId) {
		Agent agent = agentMapper.findById(agentId);
		if (agent == null) {
			throw new EmbedException(404, "agent not found");
		}
		if (!Integer.valueOf(1).equals(agent.getEmbedEnabled())) {
			throw new EmbedException(403, "embed not enabled for this agent");
		}
		return parseConfig(agent.getEmbedConfig());
	}

	/**
	 * 校验会话令牌归属与 embed 启用状态，并计限流。
	 */
	public void verifySession(String sessionToken, Long agentId) {
		long tokenAgentId;
		try {
			tokenAgentId = tokenUtil.verify(sessionToken);
		}
		catch (IllegalArgumentException e) {
			throw new EmbedException(401, "invalid or expired session token");
		}
		if (tokenAgentId != agentId) {
			throw new EmbedException(401, "session token does not match agent");
		}
		Agent agent = requireEmbedAgent(agentId);
		rateLimitCheck(agentId, agent);
	}

	/**
	 * 创建 embed 会话，返回 sessionId（同时用作 Graph 多轮 threadId）。
	 */
	public String createEmbedSession(Long agentId, String sessionToken) {
		verifySession(sessionToken, agentId);
		ChatSession session = chatSessionService.createSession(agentId.intValue(), "嵌入会话", null);
		return session.getId();
	}

	private Agent requireEmbedAgent(Long agentId) {
		Agent agent = agentMapper.findById(agentId);
		if (agent == null) {
			throw new EmbedException(404, "agent not found");
		}
		if (!Integer.valueOf(1).equals(agent.getEmbedEnabled())) {
			throw new EmbedException(403, "embed not enabled for this agent");
		}
		return agent;
	}

	private EmbedConfig parseConfig(String json) {
		if (json == null || json.isBlank()) {
			return EmbedConfig.defaults();
		}
		try {
			return objectMapper.readValue(json, EmbedConfig.class);
		}
		catch (Exception e) {
			// ponytail: 坏配置降级为默认，不阻断嵌入（fail-open 仅限 UI 配置，不涉信任边界）
			return EmbedConfig.defaults();
		}
	}

	private void rateLimitCheck(Long agentId, Agent agent) {
		int limit = defaultRateLimitPerMinute;
		if (agent != null) {
			EmbedConfig cfg = parseConfig(agent.getEmbedConfig());
			if (cfg.getRateLimitPerMinute() != null && cfg.getRateLimitPerMinute() > 0) {
				limit = cfg.getRateLimitPerMinute();
			}
		}
		long now = System.currentTimeMillis();
		long windowStart = now - 60_000L;
		Deque<Long> bucket = rateBuckets.computeIfAbsent(agentId, k -> new ConcurrentLinkedDeque<>());
		synchronized (bucket) {
			while (!bucket.isEmpty() && bucket.peekFirst() < windowStart) {
				bucket.pollFirst();
			}
			if (bucket.size() >= limit) {
				throw new EmbedException(429, "embed rate limit exceeded, retry later");
			}
			bucket.addLast(now);
		}
	}

}
