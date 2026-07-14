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

import com.alibaba.cloud.ai.graph.action.AsyncEdgeAction;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

/**
 * Node/Edge Bean 管理工具类。
 * <p>
 * 封装从 Spring 容器获取工作流节点（{@link NodeAction}）和边（{@link EdgeAction}）Bean 的逻辑， 并提供同步/异步的获取方式，简化工作流编排中对 Bean 的引用。
 * </p>
 *
 * @author vlsmb
 * @since 2025/9/28
 */
@Component
@AllArgsConstructor
public class NodeBeanUtil {

	private final ApplicationContext context;

	/**
	 * 获取指定类型的节点 Bean。
	 * @param <T> 节点动作类型
	 * @param clazz 节点动作的 Class 对象
	 * @return 节点动作 Bean 实例
	 */
	public <T extends NodeAction> NodeAction getNodeBean(Class<T> clazz) {
		return context.getBean(clazz);
	}

	/**
	 * 获取指定类型节点的异步执行包装。
	 * @param <T> 节点动作类型
	 * @param clazz 节点动作的 Class 对象
	 * @return 包装为异步执行的节点动作
	 */
	public <T extends NodeAction> AsyncNodeAction getNodeBeanAsync(Class<T> clazz) {
		return AsyncNodeAction.node_async(getNodeBean(clazz));
	}

	/**
	 * 获取指定类型的边 Bean。
	 * @param <T> 边动作类型
	 * @param clazz 边动作的 Class 对象
	 * @return 边动作 Bean 实例
	 */
	public <T extends EdgeAction> EdgeAction getEdgeBean(Class<T> clazz) {
		return context.getBean(clazz);
	}

	/**
	 * 获取指定类型边的异步执行包装。
	 * @param <T> 边动作类型
	 * @param clazz 边动作的 Class 对象
	 * @return 包装为异步执行的边动作
	 */
	public <T extends EdgeAction> AsyncEdgeAction getEdgeBeanAsync(Class<T> clazz) {
		return AsyncEdgeAction.edge_async(getEdgeBean(clazz));
	}

}
