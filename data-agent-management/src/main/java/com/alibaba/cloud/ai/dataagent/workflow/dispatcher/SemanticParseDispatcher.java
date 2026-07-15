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
package com.alibaba.cloud.ai.dataagent.workflow.dispatcher;

import com.alibaba.cloud.ai.dataagent.workflow.node.BuildSQLNode;
import com.alibaba.cloud.ai.dataagent.workflow.node.SemanticParseNode;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

/**
 * 语义解析分发器，语义解析完成后直连受控 SQL 拼装节点。
 *
 * <p>
 * 路由规则（简化版：语义解析完成后即进入拼装）：
 * <ul>
 * <li>{@link SemanticParseNode#SEMANTIC_OBJECT} 已写入状态：进入
 * {@code build_sql_node}（受控拼装）</li>
 * <li>状态缺失：仍进入 {@code build_sql_node}，由 {@link BuildSQLNode} 内部判定缺失并触发反问</li>
 * </ul>
 * </p>
 *
 * <p>
 * 注意：{@code build_sql_node} 为 v0.2 语义层新节点名，尚未在 {@code Constant} 中定义常量，
 * 此处使用字符串字面量，待主代理统一注册节点时对齐。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
public class SemanticParseDispatcher implements EdgeAction {

	/** 受控 SQL 拼装节点名（v0.2 新增，尚未在 Constant 中定义） */
	private static final String BUILD_SQL_NODE = "build_sql_node";

	/**
	 * 语义解析完成后，进入受控 SQL 拼装节点。
	 * @param state 工作流全局状态，包含 {@link SemanticParseNode#SEMANTIC_OBJECT}
	 * @return 下一节点名称 {@value BUILD_SQL_NODE}
	 */
	@Override
	public String apply(OverAllState state) {
		boolean hasSemanticObject = state.value(SemanticParseNode.SEMANTIC_OBJECT).isPresent();
		if (!hasSemanticObject) {
			log.warn("语义解析产出缺失（SEMANTIC_OBJECT 为空），仍进入拼装节点由其内部触发反问。");
		}
		else {
			log.info("语义解析完成，进入受控 SQL 拼装节点: {}", BUILD_SQL_NODE);
		}
		return BUILD_SQL_NODE;
	}

}
