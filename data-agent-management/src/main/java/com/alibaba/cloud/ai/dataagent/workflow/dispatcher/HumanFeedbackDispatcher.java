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

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.HUMAN_FEEDBACK_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 人工反馈分发器，根据人工反馈节点的处理结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>等待反馈状态（WAIT_FOR_FEEDBACK）：返回人工反馈节点以暂停图执行</li>
 * <li>其他状态：按人工反馈节点设置的下一个节点路由</li>
 * </ul>
 * </p>
 *
 * @author Makoto
 */
public class HumanFeedbackDispatcher implements EdgeAction {

	/**
	 * 根据人工反馈结果决定下一个节点。
	 * @param state 工作流全局状态，包含人工反馈路由信息
	 * @return 下一个节点名称
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		String nextNode = (String) state.value("human_next_node", END);

		// 如果是等待反馈状态，返回人工反馈节点让图暂停
		if ("WAIT_FOR_FEEDBACK".equals(nextNode)) {
			return HUMAN_FEEDBACK_NODE;
		}

		return nextNode;
	}

}
