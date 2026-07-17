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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

	private JwtService jwtService;

	@BeforeEach
	void setUp() {
		AdminAuthProperties props = new AdminAuthProperties();
		props.setJwtSecret("test-jwt-secret-at-least-32-bytes-long!!");
		props.setJwtTtlSeconds(3600);
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("test");
		jwtService = new JwtService(props, env);
		jwtService.init();
	}

	@Test
	void issueAndParse() {
		String token = jwtService.issue(1L, "admin");
		AdminPrincipal p = jwtService.parse(token);
		assertThat(p.adminId()).isEqualTo(1L);
		assertThat(p.username()).isEqualTo("admin");
	}

	@Test
	void rejectTampered() {
		String token = jwtService.issue(1L, "admin");
		String bad = token.substring(0, token.length() - 4) + "xxxx";
		assertThatThrownBy(() -> jwtService.parse(bad)).isInstanceOf(Exception.class);
	}

}
