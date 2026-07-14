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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import com.alibaba.cloud.ai.dataagent.util.ApiKeyUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;

/**
 * Agent 管理服务实现类，提供 Agent 实体的增删改查、API Key 生成与管理，
 * 以及删除时关联向量数据和头像文件的级联清理。
 */
@Slf4j
@Service
@AllArgsConstructor
public class AgentServiceImpl implements AgentService {

	/** Agent 数据访问层 */
	private final AgentMapper agentMapper;

	/** 向量存储服务，用于清理 Agent 关联的向量数据 */
	private final AgentVectorStoreService agentVectorStoreService;

	/** 文件存储服务，用于清理 Agent 头像文件 */
	private final FileStorageService fileStorageService;

	/**
	 * 查询全部 Agent。
	 * @return Agent 列表
	 */
	@Override
	public List<Agent> findAll() {
		return agentMapper.findAll();
	}

	/**
	 * 根据主键 ID 查询单个 Agent。
	 * @param id Agent 主键 ID
	 * @return 对应的 Agent 对象，不存在时返回 null
	 */
	@Override
	public Agent findById(Long id) {
		return agentMapper.findById(id);
	}

	/**
	 * 根据状态查询 Agent 列表。
	 * @param status Agent 状态
	 * @return 符合状态的 Agent 列表
	 */
	@Override
	public List<Agent> findByStatus(String status) {
		return agentMapper.findByStatus(status);
	}

	/**
	 * 根据关键字搜索 Agent。
	 * @param keyword 搜索关键字
	 * @return 匹配的 Agent 列表
	 */
	@Override
	public List<Agent> search(String keyword) {
		return agentMapper.searchByKeyword(keyword);
	}

	/**
	 * 保存 Agent，根据是否存在 ID 判断新增或更新操作。
	 * @param agent 待保存的 Agent 对象
	 * @return 保存后的 Agent 对象
	 */
	@Override
	public Agent save(Agent agent) {
		LocalDateTime now = LocalDateTime.now();

		if (agent.getId() == null) {
			// 新增：设置创建和更新时间
			agent.setCreateTime(now);
			agent.setUpdateTime(now);
			if (agent.getApiKeyEnabled() == null) {
				agent.setApiKeyEnabled(0);
			}

			agentMapper.insert(agent);
		}
		else {
			// 更新：仅更新更新时间
			agent.setUpdateTime(now);
			if (agent.getApiKeyEnabled() == null) {
				agent.setApiKeyEnabled(0);
			}
			agentMapper.updateById(agent);
		}

		return agent;
	}

	/**
	 * 根据主键 ID 删除 Agent，同时级联清理关联的向量数据和头像文件。
	 * @param id Agent 主键 ID
	 */
	@Override
	public void deleteById(Long id) {
		try {
			// 获取头像信息用于文件清理
			Agent existing = agentMapper.findById(id);
			String avatar = existing != null ? existing.getAvatar() : null;

			// 删除数据库中的 Agent 记录
			agentMapper.deleteById(id);

			// 清理该 Agent 关联的向量数据
			if (agentVectorStoreService != null) {
				try {
					agentVectorStoreService.deleteDocumentsByMetedata(id.toString(), new HashMap<>());
					log.info("Successfully deleted vector data for agent: {}", id);
				}
				catch (Exception vectorException) {
					log.warn("Failed to delete vector data for agent: {}, error: {}", id, vectorException.getMessage());
					// 向量数据删除失败不影响主流程
				}
			}

			// 清理头像文件
			try {
				if (avatar != null && !avatar.isBlank()) {
					fileStorageService.deleteFile(avatar);
					log.info("Successfully deleted avatar file: {} for agent: {}", avatar, id);
				}
			}
			catch (Exception avatarEx) {
				log.warn("Failed to cleanup avatar file: {} for agent: {}, error: {}", avatar, id,
						avatarEx.getMessage());
			}

			log.info("Successfully deleted agent: {}", id);
		}
		catch (Exception e) {
			log.error("Failed to delete agent: {}", id, e);
			throw e;
		}
	}

	/**
	 * 为指定 Agent 生成新的 API Key 并启用。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	@Override
	public Agent generateApiKey(Long id) {
		Agent agent = requireAgent(id);
		// 生成新的 API Key 并更新到数据库
		String apiKey = ApiKeyUtil.generate();
		agentMapper.updateApiKey(id, apiKey, 1);
		agent.setApiKey(apiKey);
		agent.setApiKeyEnabled(1);
		return agent;
	}

	/**
	 * 重置指定 Agent 的 API Key，等同于重新生成。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	@Override
	public Agent resetApiKey(Long id) {
		return generateApiKey(id);
	}

	/**
	 * 删除指定 Agent 的 API Key 并禁用。
	 * @param id Agent 主键 ID
	 * @return 更新后的 Agent 对象
	 */
	@Override
	public Agent deleteApiKey(Long id) {
		Agent agent = requireAgent(id);
		// 清空 API Key 并设为禁用状态
		agentMapper.updateApiKey(id, null, 0);
		agent.setApiKey(null);
		agent.setApiKeyEnabled(0);
		return agent;
	}

	/**
	 * 切换指定 Agent 的 API Key 启用状态。
	 * @param id Agent 主键 ID
	 * @param enabled 是否启用
	 * @return 更新后的 Agent 对象
	 */
	@Override
	public Agent toggleApiKey(Long id, boolean enabled) {
		agentMapper.toggleApiKey(id, enabled ? 1 : 0);
		Agent agent = requireAgent(id);
		agent.setApiKeyEnabled(enabled ? 1 : 0);
		return agent;
	}

	/**
	 * 获取指定 Agent 的 API Key 脱敏字符串。
	 * @param id Agent 主键 ID
	 * @return 脱敏后的 API Key 字符串，不存在时返回 null
	 */
	@Override
	public String getApiKeyMasked(Long id) {
		Agent agent = requireAgent(id);
		String apiKey = agent.getApiKey();
		if (apiKey == null || apiKey.isBlank()) {
			return null;
		}
		// 对 API Key 进行脱敏处理后返回
		return ApiKeyUtil.mask(apiKey);
	}

	/**
	 * 根据主键 ID 获取 Agent，不存在时抛出异常。
	 * @param id Agent 主键 ID
	 * @return 查询到的 Agent 对象
	 * @throws IllegalArgumentException 当 Agent 不存在时抛出
	 */
	private Agent requireAgent(Long id) {
		Agent agent = agentMapper.findById(id);
		if (agent == null) {
			throw new IllegalArgumentException("Agent not found: " + id);
		}
		return agent;
	}

}
