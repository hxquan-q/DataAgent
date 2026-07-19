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

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 解析 Bearer JWT；仅在 SSE 路径且 Header 缺失时接受 query {@code access_token}。
 * <p>
 * 由 {@link com.alibaba.cloud.ai.dataagent.config.SecurityConfig} 显式挂入链，勿标
 * {@code @Component} 以免全局 WebFilter 双注册。
 * </p>
 */
@RequiredArgsConstructor
public class JwtAuthenticationWebFilter implements WebFilter {

	private final JwtService jwtService;

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String token = resolveToken(exchange.getRequest());
		if (!StringUtils.hasText(token)) {
			return chain.filter(exchange);
		}
		try {
			AdminPrincipal principal = jwtService.parse(token);
			var auth = new UsernamePasswordAuthenticationToken(principal, null,
					List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
			return chain.filter(exchange).contextWrite(ReactiveSecurityContextHolder.withAuthentication(auth));
		}
		catch (Exception ignored) {
			// invalid token → leave unauthenticated; protected routes return 401
			return chain.filter(exchange);
		}
	}

	static String resolveToken(ServerHttpRequest request) {
		String header = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
		if (StringUtils.hasText(header) && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
			return header.substring(7).trim();
		}
		if (isSsePath(request.getPath().value())) {
			String q = request.getQueryParams().getFirst("access_token");
			if (StringUtils.hasText(q)) {
				return q.trim();
			}
		}
		return null;
	}

	/** 仅管理端 SSE 允许 query token（非任意 CRUD）。 */
	static boolean isSsePath(String path) {
		if (path == null) {
			return false;
		}
		if (path.startsWith("/api/stream/")) {
			return true;
		}
		// /api/agent/{id}/sessions/stream
		return path.startsWith("/api/agent/") && path.endsWith("/sessions/stream");
	}

}
