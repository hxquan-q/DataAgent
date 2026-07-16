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

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.vo.ApiResponse;
import com.alibaba.cloud.ai.dataagent.vo.EmbedConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * embed 管理端点（管理后台调用，配/禁用某 Agent 的网页嵌入）。
 *
 * <p>
 * 与 {@link EmbedPublicController}（公开、令牌鉴权）相对，本控制器供后台在 Agent 编辑页配置
 * {@code embed_enabled} + {@code embed_config}。发布令牌（apiKey）的生成/轮换复用
 * {@code AgentController} 的 {@code /api/agent/{id}/api-key/*}。
 * </p>
 * <p>
 * ⚠️ 鉴权：当前与 {@code AgentController} 一致——项目全局<strong>尚无</strong>登录/RBAC
 * （无 Spring Security / {@code @PreAuthorize}）。任意能访问管理 API 的客户端均可改任意 agent
 * 的 embed 配置。补全用户体系后须在此加 ownership/admin 校验，勿单独给 embed 做假安全感。
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
public class EmbedManageController {

	private final AgentMapper agentMapper;

	private final ObjectMapper objectMapper;

	/** 读 embed 配置（apiKey 不返回）。 */
	@GetMapping("/{id}/embed-config")
	public ResponseEntity<Map<String, Object>> getEmbed(@PathVariable Long id) {
		Agent agent = agentMapper.findById(id);
		if (agent == null) {
			return ResponseEntity.status(404).body(Map.of("error", "agent not found"));
		}
		return ResponseEntity.ok(Map.of("embedEnabled", agent.getEmbedEnabled() == null ? 0 : agent.getEmbedEnabled(),
				"embedConfig", parse(agent.getEmbedConfig())));
	}

	/**
	 * 保存 embed 配置并启用（embed_enabled=1）。
	 * 安全校验：allowedOrigins 不得为空或仅通配符 "*"——必须显式列宿主域（η₅ 信任边界）。
	 */
	@PutMapping("/{id}/embed-config")
	public ApiResponse<EmbedConfig> updateEmbed(@PathVariable Long id, @RequestBody EmbedConfig config) {
		Agent agent = agentMapper.findById(id);
		if (agent == null) {
			return ApiResponse.error("agent not found");
		}
		if (config == null) {
			return ApiResponse.error("embed config body is required");
		}
		if (config.getAllowedOrigins() == null || config.getAllowedOrigins().isEmpty()) {
			return ApiResponse.error("allowedOrigins 不能为空，必须显式列出允许嵌入的宿主域");
		}
		if (config.getAllowedOrigins().size() == 1 && "*".equals(config.getAllowedOrigins().get(0))) {
			return ApiResponse.error("allowedOrigins 生产环境禁用通配符 *，请显式列出宿主域");
		}
		try {
			String json = objectMapper.writeValueAsString(config);
			agentMapper.updateEmbedConfig(id, 1, json);
			log.info("embed config enabled for agent {}", id);
			return ApiResponse.success("embed 配置已保存", config);
		}
		catch (Exception e) {
			log.error("failed to save embed config for agent {}", id, e);
			return ApiResponse.error("保存 embed 配置失败: " + e.getMessage());
		}
	}

	/** 禁用 embed（embed_enabled=0，保留配置）。 */
	@DeleteMapping("/{id}/embed-config")
	public ApiResponse<Void> disableEmbed(@PathVariable Long id) {
		agentMapper.updateEmbedConfig(id, 0, null);
		log.info("embed disabled for agent {}", id);
		return ApiResponse.success("embed 已禁用");
	}

	private EmbedConfig parse(String json) {
		if (json == null || json.isBlank()) {
			return EmbedConfig.defaults();
		}
		try {
			return objectMapper.readValue(json, EmbedConfig.class);
		}
		catch (Exception e) {
			return EmbedConfig.defaults();
		}
	}

}
