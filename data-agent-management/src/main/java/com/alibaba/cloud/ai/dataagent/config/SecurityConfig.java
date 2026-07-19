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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.security.JwtAuthenticationWebFilter;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 管理端 WebFlux Security。
 * <p>
 * permitAll 白名单（写死）：
 * <ul>
 * <li>POST /api/auth/login, GET /api/auth/bootstrap-status</li>
 * <li>/api/embed/public/**</li>
 * <li>/echo/**</li>
 * <li>MCP /sse/**, /mcp/**</li>
 * <li>/uploads/**, GET /api/upload/**</li>
 * <li>OPTIONS /**</li>
 * <li>/actuator/health（若启用）</li>
 * <li>local/h2/test: swagger</li>
 * </ul>
 * 其余 authenticated。SSE query token 仅由 {@link JwtAuthenticationWebFilter} 在 stream 路径解析。
 * </p>
 */
@Configuration
@EnableWebFluxSecurity
@RequiredArgsConstructor
public class SecurityConfig {

	private final ObjectMapper objectMapper;

	private final Environment environment;

	private final com.alibaba.cloud.ai.dataagent.security.JwtService jwtService;

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
		boolean swaggerOpen = isDevProfile();
		JwtAuthenticationWebFilter jwtAuthenticationWebFilter = new JwtAuthenticationWebFilter(jwtService);

		http.csrf(ServerHttpSecurity.CsrfSpec::disable)
			.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
			.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
			.logout(ServerHttpSecurity.LogoutSpec::disable)
			.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
			.exceptionHandling(ex -> ex.authenticationEntryPoint((exchange, e) -> writeJson(exchange,
					HttpStatus.UNAUTHORIZED, "未认证"))
				.accessDeniedHandler((exchange, e) -> writeJson(exchange, HttpStatus.FORBIDDEN, "禁止访问")))
			.authorizeExchange(auth -> {
				auth.pathMatchers(HttpMethod.OPTIONS, "/**").permitAll();
				auth.pathMatchers(HttpMethod.POST, "/api/auth/login").permitAll();
				auth.pathMatchers(HttpMethod.GET, "/api/auth/bootstrap-status").permitAll();
				auth.pathMatchers("/api/embed/public/**").permitAll();
				auth.pathMatchers("/echo/**").permitAll();
				auth.pathMatchers("/sse", "/sse/**").permitAll();
				auth.pathMatchers("/mcp", "/mcp/**").permitAll();
				auth.pathMatchers("/uploads/**").permitAll();
				auth.pathMatchers(HttpMethod.GET, "/api/upload/**").permitAll();
				auth.pathMatchers("/actuator/health", "/actuator/health/**").permitAll();
				if (swaggerOpen) {
					auth.pathMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs", "/v3/api-docs/**",
							"/webjars/**")
						.permitAll();
				}
				auth.anyExchange().authenticated();
			})
			.addFilterAt(jwtAuthenticationWebFilter, SecurityWebFiltersOrder.AUTHENTICATION);

		return http.build();
	}

	private boolean isDevProfile() {
		return Arrays.stream(environment.getActiveProfiles())
			.anyMatch(p -> "local".equalsIgnoreCase(p) || "h2".equalsIgnoreCase(p) || "test".equalsIgnoreCase(p));
	}

	private Mono<Void> writeJson(org.springframework.web.server.ServerWebExchange exchange, HttpStatus status,
			String message) {
		exchange.getResponse().setStatusCode(status);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
		try {
			byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(message));
			DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
			return exchange.getResponse().writeWith(Mono.just(buffer));
		}
		catch (Exception e) {
			byte[] bytes = ("{\"success\":false,\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
			DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
			return exchange.getResponse().writeWith(Mono.just(buffer));
		}
	}

}
