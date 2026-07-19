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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 管理员账号：bootstrap、登录校验、改密。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserService {

	public static final String LOGIN_ERROR_MESSAGE = "用户名或密码错误";

	private final AdminUserMapper adminUserMapper;

	private final PasswordEncoder passwordEncoder;

	private final AdminAuthProperties properties;

	private final JwtService jwtService;

	private final Environment environment;

	public boolean isInitialized() {
		return adminUserMapper.count() > 0;
	}

	/**
	 * 空表时创建默认管理员。local 可用弱默认密码；prod 无密码则 fail-fast。
	 */
	public void bootstrapIfEmpty() {
		if (!properties.isBootstrapEnabled()) {
			log.info("Admin bootstrap disabled (admin.bootstrap-enabled=false)");
			return;
		}
		if (adminUserMapper.count() > 0) {
			return;
		}
		String username = StringUtils.hasText(properties.getUsername()) ? properties.getUsername().trim() : "admin";
		String password = properties.getPassword() == null ? "" : properties.getPassword();
		boolean localLike = isLocalLike();
		if (!StringUtils.hasText(password)) {
			if (localLike) {
				password = "admin123";
				log.warn("Admin bootstrap using default password 'admin123' — change immediately after login");
			}
			else {
				throw new IllegalStateException(
						"admin_user is empty and DATA_AGENT_ADMIN_PASSWORD / spring.ai.alibaba.data-agent.admin.password "
								+ "is not set — refuse to start without bootstrap password in non-local profiles");
			}
		}
		else if ("admin123".equals(password)) {
			log.warn("Admin bootstrap password is the weak default admin123 — change after first login");
		}

		AdminUser user = AdminUser.builder()
			.username(username)
			.passwordHash(passwordEncoder.encode(password))
			.displayName(username)
			.status(1)
			.build();
		try {
			adminUserMapper.insert(user);
			log.info("Bootstrapped admin user '{}' (id={})", username, user.getId());
		}
		catch (Exception e) {
			// UNIQUE race with multi-instance
			if (adminUserMapper.count() > 0) {
				log.info("Admin user already created concurrently, skip bootstrap");
				return;
			}
			throw e;
		}
	}

	public LoginResult login(String username, String password) {
		if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
			throw new AuthException(401, LOGIN_ERROR_MESSAGE);
		}
		AdminUser user = adminUserMapper.findByUsername(username.trim());
		if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
			throw new AuthException(401, LOGIN_ERROR_MESSAGE);
		}
		if (user.getStatus() == null || user.getStatus() != 1) {
			throw new AuthException(403, "账号已禁用");
		}
		adminUserMapper.touchLastLogin(user.getId());
		String token = jwtService.issue(user.getId(), user.getUsername());
		return new LoginResult(token, jwtService.getTtlSeconds(), user.getUsername(), user.getId(),
				user.getDisplayName());
	}

	public AdminUser requireUser(Long adminId) {
		AdminUser user = adminUserMapper.findById(adminId);
		if (user == null) {
			throw new AuthException(401, "未认证");
		}
		if (user.getStatus() == null || user.getStatus() != 1) {
			throw new AuthException(403, "账号已禁用");
		}
		return user;
	}

	public void changePassword(Long adminId, String oldPassword, String newPassword) {
		if (!StringUtils.hasText(newPassword) || newPassword.length() < 8) {
			throw new AuthException(400, "新密码至少 8 位");
		}
		AdminUser user = requireUser(adminId);
		if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
			throw new AuthException(400, "原密码不正确");
		}
		adminUserMapper.updatePassword(adminId, passwordEncoder.encode(newPassword));
	}

	/**
	 * local/h2/test 或未声明 profile：允许弱默认密码 bootstrap（WARN）。
	 * 仅显式 prod/production 要求必须配置密码。
	 */
	private boolean isLocalLike() {
		String[] active = environment.getActiveProfiles();
		if (active.length == 0) {
			return true;
		}
		boolean prod = java.util.Arrays.stream(active)
			.anyMatch(p -> "prod".equalsIgnoreCase(p) || "production".equalsIgnoreCase(p));
		return !prod;
	}

	public record LoginResult(String token, long expiresIn, String username, Long adminId, String displayName) {
	}

	/**
	 * 鉴权业务异常，由 Controller / EntryPoint 映射为 HTTP 状态。
	 */
	public static class AuthException extends RuntimeException {

		private final int status;

		public AuthException(int status, String message) {
			super(message);
			this.status = status;
		}

		public int getStatus() {
			return status;
		}

	}

}
