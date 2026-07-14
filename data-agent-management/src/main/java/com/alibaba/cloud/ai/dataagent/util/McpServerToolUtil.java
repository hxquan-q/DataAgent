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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.annotation.McpServerTool;
import org.springframework.context.support.GenericApplicationContext;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * MCP Server 工具类。
 * <p>
 * 用于在 Spring 容器中按类型筛选 Bean，并排除标注了 {@link McpServerTool} 的 Bean。 便于在工作流节点中只获取非 MCP Server 工具，避免对外暴露的 MCP 工具被节点误用。
 * </p>
 */
public final class McpServerToolUtil {

	private McpServerToolUtil() {
	}

	/**
	 * 获取指定类型的所有 Bean，但排除标注了 {@link McpServerTool} 的 Bean。
	 * @param <T> Bean 类型
	 * @param context Spring 应用上下文
	 * @param type 目标 Bean 类型
	 * @return 不包含 MCP Server 工具的 Bean 列表
	 */
	public static <T> List<T> excludeMcpServerTool(GenericApplicationContext context, Class<T> type) {
		// 获取指定类型的所有 Bean 名称
		String[] namesForType = context.getBeanNamesForType(type);
		// 获取标注了 McpServerTool 的 Bean 名称集合
		Set<String> namesForAnnotation = Set.of(context.getBeanNamesForAnnotation(McpServerTool.class));
		// 过滤掉 MCP Server 工具 Bean，剩余 Bean 实例化后返回
		return Arrays.stream(namesForType)
			.filter(name -> !namesForAnnotation.contains(name))
			.map(name -> context.getBean(name, type))
			.toList();
	}

}
