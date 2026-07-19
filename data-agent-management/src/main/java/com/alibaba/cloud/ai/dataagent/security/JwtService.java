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
package com.alibaba.cloud.ai.dataagent.security;

import com.alibaba.cloud.ai.dataagent.properties.AdminAuthProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;

/**
 * JWT 签发与校验（HS256）。密钥与 AES/embed HMAC 分离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

	private final AdminAuthProperties properties;

	private final Environment environment;

	private SecretKey secretKey;

	private long ttlSeconds;

	@PostConstruct
	void init() {
		this.ttlSeconds = Math.max(60L, properties.getJwtTtlSeconds());
		String secret = properties.getJwtSecret() == null ? "" : properties.getJwtSecret().trim();
		boolean prodLike = isProdLike();
		if (secret.isEmpty() || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
			if (prodLike) {
				throw new IllegalStateException(
						"DATA_AGENT_JWT_SECRET / spring.ai.alibaba.data-agent.admin.jwt-secret must be set "
								+ "(≥32 bytes) in production profiles");
			}
			byte[] random = new byte[32];
			new SecureRandom().nextBytes(random);
			secret = Base64.getEncoder().encodeToString(random);
			log.warn("Admin JWT secret missing/short — generated ephemeral secret for this process (tokens reset on restart)");
		}
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
	}

	public String issue(Long adminId, String username) {
		Instant now = Instant.now();
		Instant exp = now.plusSeconds(ttlSeconds);
		return Jwts.builder()
			.subject(String.valueOf(adminId))
			.claim("username", username)
			.issuedAt(Date.from(now))
			.expiration(Date.from(exp))
			.signWith(secretKey)
			.compact();
	}

	public AdminPrincipal parse(String token) {
		Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
		Long adminId = Long.valueOf(claims.getSubject());
		String username = claims.get("username", String.class);
		if (username == null || username.isBlank()) {
			username = claims.getSubject();
		}
		return new AdminPrincipal(adminId, username);
	}

	public long getTtlSeconds() {
		return ttlSeconds;
	}

	/**
	 * 仅显式 prod 强制 JWT secret；无 profile / local / h2 / test 允许 ephemeral secret
	 * + WARN（降低升级摩擦）。
	 */
	boolean isProdLike() {
		return java.util.Arrays.stream(environment.getActiveProfiles())
			.anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
	}

	/** 运维：生成随机 BCrypt 友好 salt 材料（非密码哈希）。 */
	public static String randomHex(int bytes) {
		byte[] buf = new byte[bytes];
		new SecureRandom().nextBytes(buf);
		return HexFormat.of().formatHex(buf);
	}

}
