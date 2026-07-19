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

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SCHEMA_RECALL_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 查询增强分发器，根据查询增强节点的结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>查询增强结果为空或字段缺失：结束流程</li>
 * <li>结果有效（规范化查询和扩展查询均非空）：进入 Schema 召回节点</li>
 * </ul>
 * </p>
 */
@Slf4j
public class QueryEnhanceDispatcher implements EdgeAction {

	/**
	 * 根据查询增强结果决定下一个节点。
	 * @param state 工作流全局状态，包含查询增强结果
	 * @return 下一个节点名称：{@value SCHEMA_RECALL_NODE} 或 {@code END}
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取查询增强结果
		QueryEnhanceOutputDTO queryProcessOutput = StateUtil.getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);

		// 检查查询增强结果是否为空
		if (queryProcessOutput == null) {
			log.warn("查询增强结果为空，结束流程");
			return END;
		}

		// 检查各字段是否为空
		boolean isCanonicalQueryEmpty = queryProcessOutput.getCanonicalQuery() == null
				|| queryProcessOutput.getCanonicalQuery().trim().isEmpty();
		boolean isExpandedQueriesEmpty = queryProcessOutput.getExpandedQueries() == null
				|| queryProcessOutput.getExpandedQueries().isEmpty();

		if (isCanonicalQueryEmpty || isExpandedQueriesEmpty) {
			log.warn("查询增强结果字段为空 - 规范化查询为空: {}, 扩展查询为空: {}", isCanonicalQueryEmpty, isExpandedQueriesEmpty);
			return END;
		}
		else {
			log.info("查询增强结果有效，进入 Schema 召回节点");
			return SCHEMA_RECALL_NODE;
		}
	}

}
