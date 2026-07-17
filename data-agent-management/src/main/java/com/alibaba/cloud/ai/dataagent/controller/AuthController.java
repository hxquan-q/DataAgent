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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.entity.AdminUser;
import com.alibaba.cloud.ai.dataagent.security.AdminPrincipal;
import com.alibaba.cloud.ai.dataagent.service.admin.AdminUserService;
import com.alibaba.cloud.ai.dataagent.service.admin.AdminUserService.AuthException;
import com.alibaba.cloud.ai.dataagent.service.admin.AdminUserService.LoginResult;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 管理端登录 / 当前用户 / 改密。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

	private final AdminUserService adminUserService;

	@PostMapping("/login")
	public ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest request) {
		LoginResult result = adminUserService.login(request.getUsername(), request.getPassword());
		return ApiResponse.success("ok", result);
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout() {
		// v1: 客户端丢弃 token；服务端 no-op
		return ApiResponse.success("ok");
	}

	@GetMapping("/me")
	public ApiResponse<Map<String, Object>> me(Authentication authentication) {
		AdminPrincipal principal = requirePrincipal(authentication);
		AdminUser user = adminUserService.requireUser(principal.adminId());
		return ApiResponse.success("ok", Map.of("adminId", user.getId(), "username", user.getUsername(), "displayName",
				user.getDisplayName() == null ? user.getUsername() : user.getDisplayName()));
	}

	@PostMapping("/change-password")
	public ApiResponse<Void> changePassword(Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest request) {
		AdminPrincipal principal = requirePrincipal(authentication);
		adminUserService.changePassword(principal.adminId(), request.getOldPassword(), request.getNewPassword());
		return ApiResponse.success("密码已修改，请重新登录");
	}

	@GetMapping("/bootstrap-status")
	public ApiResponse<Map<String, Boolean>> bootstrapStatus() {
		return ApiResponse.success("ok", Map.of("initialized", adminUserService.isInitialized()));
	}

	@ExceptionHandler(AuthException.class)
	public ResponseEntity<ApiResponse<Void>> handleAuth(AuthException e) {
		HttpStatus status = HttpStatus.resolve(e.getStatus());
		if (status == null) {
			status = HttpStatus.BAD_REQUEST;
		}
		return ResponseEntity.status(status).body(ApiResponse.error(e.getMessage()));
	}

	private static AdminPrincipal requirePrincipal(Authentication authentication) {
		if (authentication == null || !(authentication.getPrincipal() instanceof AdminPrincipal p)) {
			throw new AuthException(401, "未认证");
		}
		return p;
	}

	@Data
	public static class LoginRequest {

		@NotBlank
		private String username;

		@NotBlank
		private String password;

	}

	@Data
	public static class ChangePasswordRequest {

		@NotBlank
		private String oldPassword;

		@NotBlank
		private String newPassword;

	}

}
