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

import com.alibaba.cloud.ai.dataagent.annotation.McpServerTool;
import com.alibaba.cloud.ai.dataagent.service.mcp.McpServerService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 工具注册配置。
 * <p>
 * 通过 {@code @McpServerTool} 自定义注解延迟注册 MCP 工具，解决循环依赖问题： ChatClient 依赖 ChatModel，而
 * ChatModel 初始化时立即扫描 Tool， 但 NL2SQL 等工具功能又依赖 ChatClient，形成循环。 使用自定义注解将工具注册推迟到合适时机。
 * </p>
 */
// TODO 2025/12/08 合并包后移动到DataAgentConfiguration 中
@Configuration
public class McpServerConfig {

	/**
	 * 注册 MCP Server 工具回调提供者。
	 * <p>
	 * 将 {@link McpServerService} 中的 @Tool 方法注册为 Spring AI 工具回调， 供 ChatClient / MCP 协议调用。
	 * </p>
	 * @param mcpServerService MCP 工具服务
	 * @return 工具回调提供者
	 */
	@Bean
	@McpServerTool
	public ToolCallbackProvider mcpServerTools(McpServerService mcpServerService) {
		return MethodToolCallbackProvider.builder().toolObjects(mcpServerService).build();
	}

}
