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
package com.alibaba.cloud.ai.dataagent.service.admin;

import com.alibaba.cloud.ai.dataagent.entity.AdminUser;
import com.alibaba.cloud.ai.dataagent.mapper.AdminUserMapper;
import com.alibaba.cloud.ai.dataagent.properties.AdminAuthProperties;
import com.alibaba.cloud.ai.dataagent.security.JwtService;
import com.alibaba.cloud.ai.dataagent.service.admin.AdminUserService.AuthException;
import com.alibaba.cloud.ai.dataagent.service.admin.AdminUserService.LoginResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

	@Mock
	private AdminUserMapper mapper;

	@Mock
	private JwtService jwtService;

	private PasswordEncoder encoder;

	private AdminUserService service;

	@BeforeEach
	void setUp() {
		encoder = new BCryptPasswordEncoder();
		AdminAuthProperties props = new AdminAuthProperties();
		props.setUsername("admin");
		props.setPassword("admin123");
		props.setBootstrapEnabled(true);
		MockEnvironment env = new MockEnvironment();
		env.setActiveProfiles("test");
		service = new AdminUserService(mapper, encoder, props, jwtService, env);
	}

	@Test
	void loginSuccess() {
		AdminUser user = AdminUser.builder()
			.id(1L)
			.username("admin")
			.passwordHash(encoder.encode("admin123"))
			.status(1)
			.displayName("Admin")
			.build();
		when(mapper.findByUsername("admin")).thenReturn(user);
		when(jwtService.issue(1L, "admin")).thenReturn("tok");
		when(jwtService.getTtlSeconds()).thenReturn(3600L);

		LoginResult result = service.login("admin", "admin123");
		assertThat(result.token()).isEqualTo("tok");
		assertThat(result.username()).isEqualTo("admin");
		verify(mapper).touchLastLogin(1L);
	}

	@Test
	void loginWrongPassword() {
		AdminUser user = AdminUser.builder()
			.id(1L)
			.username("admin")
			.passwordHash(encoder.encode("admin123"))
			.status(1)
			.build();
		when(mapper.findByUsername("admin")).thenReturn(user);
		assertThatThrownBy(() -> service.login("admin", "wrong")).isInstanceOf(AuthException.class)
			.hasMessageContaining("用户名或密码错误");
	}

	@Test
	void loginDisabled() {
		AdminUser user = AdminUser.builder()
			.id(1L)
			.username("admin")
			.passwordHash(encoder.encode("admin123"))
			.status(0)
			.build();
		when(mapper.findByUsername("admin")).thenReturn(user);
		assertThatThrownBy(() -> service.login("admin", "admin123")).isInstanceOf(AuthException.class)
			.extracting(e -> ((AuthException) e).getStatus())
			.isEqualTo(403);
	}

	@Test
	void bootstrapWhenEmpty() {
		when(mapper.count()).thenReturn(0L);
		when(mapper.insert(any())).thenAnswer(inv -> {
			AdminUser u = inv.getArgument(0);
			u.setId(9L);
			return 1;
		});
		service.bootstrapIfEmpty();
		ArgumentCaptor<AdminUser> cap = ArgumentCaptor.forClass(AdminUser.class);
		verify(mapper).insert(cap.capture());
		assertThat(cap.getValue().getUsername()).isEqualTo("admin");
		assertThat(encoder.matches("admin123", cap.getValue().getPasswordHash())).isTrue();
	}

	@Test
	void changePassword() {
		AdminUser user = AdminUser.builder()
			.id(1L)
			.username("admin")
			.passwordHash(encoder.encode("oldpass12"))
			.status(1)
			.build();
		when(mapper.findById(1L)).thenReturn(user);
		service.changePassword(1L, "oldpass12", "newpass99");
		verify(mapper).updatePassword(anyLong(), anyString());
	}

}
