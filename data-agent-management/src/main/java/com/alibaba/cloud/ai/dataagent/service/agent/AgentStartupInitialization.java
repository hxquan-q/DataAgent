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
import com.alibaba.cloud.ai.dataagent.entity.AgentDatasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.AgentDatasourceService;
import com.alibaba.cloud.ai.dataagent.service.vectorstore.AgentVectorStoreService;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

/**
 * Agent 启动初始化服务，在应用启动时自动初始化所有已发布（published）状态的 Agent 的数据源和向量数据。
 *
 * <p>
 * 通过实现 {@link ApplicationRunner} 在应用启动后异步执行初始化逻辑，避免阻塞 Spring 主启动线程； 通过实现
 * {@link DisposableBean} 在应用关闭时释放线程池资源。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentStartupInitialization implements ApplicationRunner, DisposableBean {

	/** Agent 管理服务 */
	private final AgentService agentService;

	/** 向量存储服务，用于检查初始化状态 */
	private final AgentVectorStoreService agentVectorStoreService;

	/** Agent 数据源服务，用于初始化数据源 Schema */
	private final AgentDatasourceService agentDatasourceService;

	/** 异步执行线程池，用于后台执行初始化任务 */
	private final ExecutorService executorService;

	/**
	 * 应用启动入口，异步触发已发布 Agent 的自动初始化。
	 * @param args 应用启动参数
	 */
	@Override
	public void run(ApplicationArguments args) {
		log.info("Starting automatic initialization of published agents...");

		try {
			// 异步执行初始化，让初始化过程在后台运行，不阻塞 Spring 启动主线程，
			// 提高启动速度和响应性；即使初始化很耗时也不会影响主程序正常启动
			CompletableFuture.runAsync(this::initializePublishedAgents, executorService).exceptionally(throwable -> {
				log.error("Error during agent initialization: {}", throwable.getMessage());
				return null;
			});

		}
		catch (Exception e) {
			log.error("Failed to start agent initialization process", e);
		}
	}

	/** 初始化所有已发布状态的 Agent */
	private void initializePublishedAgents() {
		try {
			// 查询所有已发布状态的 Agent
			List<Agent> publishedAgents = agentService.findByStatus("published");

			if (publishedAgents.isEmpty()) {
				log.info("No published agents found, skipping initialization");
				return;
			}

			log.info("Found {} published agents, starting initialization...", publishedAgents.size());

			int successCount = 0;
			int failureCount = 0;

			// 逐个初始化已发布的 Agent
			for (Agent agent : publishedAgents) {
				try {
					boolean initialized = initializeAgentDataSource(agent);
					if (initialized) {
						successCount++;
						log.info("Successfully initialized agent: {} (ID: {})", agent.getName(), agent.getId());
					}
					else {
						failureCount++;
						log.warn("Failed to initialize agent: {} (ID: {}) - no active datasource or tables",
								agent.getName(), agent.getId());
					}
				}
				catch (Exception e) {
					failureCount++;
					log.error("Error initializing agent: {} (ID: {}, reason: {})", agent.getName(), agent.getId(),
							e.getMessage());
				}

				// 每个 Agent 初始化之间间隔 1 秒，避免资源争抢
				try {
					Thread.sleep(1000);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}

			log.info("Agent initialization completed. Success: {}, Failed: {}, Total: {}", successCount, failureCount,
					publishedAgents.size());

		}
		catch (Exception e) {
			log.error("Error during published agents initialization", e);
		}
	}

	/**
	 * 初始化单个 Agent 的数据源。
	 * @param agent 待初始化的 Agent
	 * @return 初始化是否成功
	 */
	private boolean initializeAgentDataSource(Agent agent) {
		try {
			Long agentId = agent.getId();

			// 检查是否已有向量数据，避免重复初始化
			boolean hasData = isAlreadyInitialized(agentId);

			if (hasData) {
				log.info("Agent {} already has vector data , skipping initialization", agentId);
				return true;
			}

			// 获取 Agent 当前激活的数据源配置
			AgentDatasource activeDatasource = agentDatasourceService.getCurrentAgentDatasource(agentId);

			Integer datasourceId = activeDatasource.getDatasourceId();

			List<String> tables = activeDatasource.getSelectTables();

			if (tables.isEmpty()) {
				log.warn("Datasource {} has no tables available for agent {}", datasourceId, agentId);
				return false;
			}

			log.info("Initializing agent {} with datasource {} and {} tables", agentId, datasourceId, tables.size());

			// 调用数据源服务初始化 Agent 的 Schema 向量数据
			Boolean result = agentDatasourceService.initializeSchemaForAgentWithDatasource(agentId, datasourceId,
					tables);

			if (result) {
				log.info("Successfully initialized datasource for agent {} with {} tables", agentId, tables.size());
				return true;
			}
			else {
				log.error("Failed to initialize datasource for agent {}", agentId);
				return false;
			}

		}
		catch (Exception e) {
			log.error("Error initializing datasource for agent {}, reason: {}", agent.getId(), e.getMessage());
			return false;
		}
	}

	/**
	 * 检查指定 Agent 是否已经初始化过向量数据。
	 * @param agentId Agent 主键 ID
	 * @return 是否已存在向量数据
	 */
	private boolean isAlreadyInitialized(Long agentId) {
		try {
			String agentIdStr = String.valueOf(agentId);
			return agentVectorStoreService.hasDocuments(agentIdStr);
		}
		catch (Exception e) {
			// 检查失败时假定为未初始化
			log.error("Failed to check initialization status for agent: {}, assuming not initialized", agentId, e);
			return false;
		}
	}

	/**
	 * 应用关闭时清理资源，关闭初始化线程池。
	 */
	@Override
	public void destroy() {
		if (!executorService.isShutdown()) {
			log.info("Shutting down agent initialization executor service");
			executorService.shutdown();
		}
	}

}
