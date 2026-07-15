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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.SQL_EXECUTE_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 受控 SQL 拼装分发器，根据拼装结果决定下一节点（反问 / 执行 / 结束）。
 *
 * <p>
 * 路由规则（按优先级判定）：
 * <ul>
 * <li>{@link BuildSQLNode#NEEDS_CLARIFICATION}=true：进入人工反馈/反问节点
 * {@value HUMAN_FEEDBACK_NODE}（{@link BuildSQLNode#CLARIFICATION_MESSAGE} 已写入状态）</li>
 * <li>{@link BuildSQLNode#CONTROLLED_SQL} 非空：进入 SQL 执行节点 {@value SQL_EXECUTE_NODE}</li>
 * <li>其他情况（既未触发反问也未产出 SQL）：结束流程 {@code END}</li>
 * </ul>
 * </p>
 *
 * <p>
 * 说明：语义解析缺失、口径歧义、SQL 护栏拦截等场景均由 {@link BuildSQLNode} 统一置位
 * {@code NEEDS_CLARIFICATION=true}，本分发器据此将流程转交人工反馈节点，不再单独判定异常分支。
 * </p>
 *
 * @author dataagent
 */
@Slf4j
public class BuildSQLDispatcher implements EdgeAction {

	/**
	 * 根据受控 SQL 拼装结果决定下一节点。
	 * @param state 工作流全局状态，包含 {@link BuildSQLNode#NEEDS_CLARIFICATION}、
	 * {@link BuildSQLNode#CONTROLLED_SQL}、{@link BuildSQLNode#CLARIFICATION_MESSAGE}
	 * @return 下一节点名称：{@value HUMAN_FEEDBACK_NODE}、{@value SQL_EXECUTE_NODE} 或 {@code END}
	 */
	@Override
	public String apply(OverAllState state) {
		// 1. 需反问 → 人工反馈节点
		boolean needsClarification = state.value(BuildSQLNode.NEEDS_CLARIFICATION, false);
		if (needsClarification) {
			String message = state.value(BuildSQLNode.CLARIFICATION_MESSAGE, "");
			log.info("BuildSQL 需反问，进入人工反馈节点。反问消息: {}", message);
			return HUMAN_FEEDBACK_NODE;
		}

		// 2. 受控 SQL 非空 → SQL 执行节点
		String controlledSql = state.value(BuildSQLNode.CONTROLLED_SQL, "");
		if (controlledSql != null && !controlledSql.isBlank()) {
			log.info("BuildSQL 拼装完成，进入 SQL 执行节点: {}", SQL_EXECUTE_NODE);
			return SQL_EXECUTE_NODE;
		}

		// 3. 既未触发反问也未产出 SQL，结束流程
		log.warn("BuildSQL 未产出 SQL 且未触发反问，结束流程。");
		return END;
	}

}
