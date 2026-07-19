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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * embed 会话令牌（session token）工具：自包含 HMAC-SHA256 签名令牌，零依赖（纯 JDK）。
 *
 * <p>
 * 格式：{@code das_<base64url(payloadJson)>.<base64url(hmacSha256(payloadB64))>}。
 * payload 为 {@code {aid, iat, exp}}。校验时重算 HMAC（常量时间比对）并检查 {@code exp}。
 * </p>
 *
 * <p>
 * 发布令牌（长期）= {@code Agent.apiKey}，仅在 {@code exchange} 端点比对一次后换得本工具签发的短期会话令牌，
 * 发布令牌不进入浏览器。⚠️ R2 起 {@code token-hmac-key} 在生产 profile 必须显式配置（启动 fail-closed）。
 * </p>
 */
@Component
public class EmbedSessionTokenUtil {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private static final String ALGORITHM = "HmacSHA256";

	private static final String PREFIX = "das_";

	private final byte[] hmacKey;

	private final long ttlSeconds;

	public EmbedSessionTokenUtil(
			@Value("${spring.ai.alibaba.data-agent.embed.token-hmac-key:dataagent-embed-dev-hmac-key-change-in-prod}") String key,
			@Value("${spring.ai.alibaba.data-agent.embed.session-ttl-seconds:1800}") long ttlSeconds) {
		this.hmacKey = key.getBytes(StandardCharsets.UTF_8);
		this.ttlSeconds = ttlSeconds;
	}

	/** 签发结果：令牌字符串 + 有效期（秒）。 */
	public record IssueResult(String token, long expiresIn) {
	}

	/**
	 * 为指定 Agent 签发会话令牌。
	 * @param agentId 智能体 ID
	 * @return 令牌与有效期
	 */
	public IssueResult issue(long agentId) {
		long now = System.currentTimeMillis() / 1000L;
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("aid", agentId);
		payload.put("iat", now);
		payload.put("exp", now + ttlSeconds);
		return new IssueResult(encode(payload), ttlSeconds);
	}

	/**
	 * 校验会话令牌。
	 * @param token 令牌字符串
	 * @return 令牌归属的 Agent ID
	 * @throws IllegalArgumentException 令牌无效/过期/签名不符
	 */
	public long verify(String token) {
		if (token == null || !token.startsWith(PREFIX)) {
			throw new IllegalArgumentException("invalid embed session token");
		}
		String body = token.substring(PREFIX.length());
		int dot = body.lastIndexOf('.');
		if (dot <= 0) {
			throw new IllegalArgumentException("invalid embed session token");
		}
		String payloadB64 = body.substring(0, dot);
		String sigB64 = body.substring(dot + 1);
		if (!constantTimeEquals(hmac(payloadB64), sigB64)) {
			throw new IllegalArgumentException("invalid embed session token signature");
		}
		try {
			String json = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
			@SuppressWarnings("unchecked")
			Map<String, Object> payload = MAPPER.readValue(json, Map.class);
			long exp = ((Number) payload.get("exp")).longValue();
			if (System.currentTimeMillis() / 1000L > exp) {
				throw new IllegalArgumentException("embed session token expired");
			}
			return ((Number) payload.get("aid")).longValue();
		}
		catch (IllegalArgumentException e) {
			throw e;
		}
		catch (Exception e) {
			throw new IllegalArgumentException("invalid embed session token", e);
		}
	}

	private String encode(Map<String, Object> payload) {
		try {
			String json = MAPPER.writeValueAsString(payload);
			String payloadB64 = Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(json.getBytes(StandardCharsets.UTF_8));
			return PREFIX + payloadB64 + "." + hmac(payloadB64);
		}
		catch (Exception e) {
			throw new IllegalStateException("failed to encode embed session token", e);
		}
	}

	private String hmac(String data) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(hmacKey, ALGORITHM));
			byte[] sig = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
		}
		catch (Exception e) {
			throw new IllegalStateException("failed to compute embed token hmac", e);
		}
	}

	/**
	 * 常量时间字符串比较，防时序侧信道（发布令牌 / 签名校验共用）。
	 * 长度不等时仍做一轮固定开销比较，避免过早返回。
	 */
	public static boolean constantTimeEquals(String a, String b) {
		if (a == null || b == null) {
			return false;
		}
		byte[] ab = a.getBytes(StandardCharsets.UTF_8);
		byte[] bb = b.getBytes(StandardCharsets.UTF_8);
		// MessageDigest.isEqual：长度不等返回 false，等长时常量时间比较
		return java.security.MessageDigest.isEqual(ab, bb);
	}

}
