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
import com.alibaba.cloud.ai.dataagent.util.StateUtil;

import java.util.Optional;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 表关系推断分发器，根据表关系推断结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>存在异常且可重试（未超过最大重试次数）：返回表关系推断节点重试</li>
 * <li>存在异常但不可重试或超过重试次数：结束流程</li>
 * <li>无异常且有表关系输出：进入可行性评估节点</li>
 * <li>无输出：结束流程</li>
 * </ul>
 * </p>
 */
public class TableRelationDispatcher implements EdgeAction {

	/** 最大重试次数 */
	private static final int MAX_RETRY_COUNT = 3;

	/**
	 * 根据表关系推断结果决定下一个节点。
	 * @param state 工作流全局状态，包含表关系输出和异常信息
	 * @return 下一个节点名称
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {

		// 检查是否存在异常
		String errorFlag = StateUtil.getStringValue(state, TABLE_RELATION_EXCEPTION_OUTPUT, null);
		Integer retryCount = StateUtil.getObjectValue(state, TABLE_RELATION_RETRY_COUNT, Integer.class, 0);

		if (errorFlag != null && !errorFlag.isEmpty()) {
			// 异常可重试且未超过最大重试次数，返回表关系推断节点
			if (isRetryableError(errorFlag) && retryCount < MAX_RETRY_COUNT) {
				return TABLE_RELATION_NODE;
			}
			else {
				return END;
			}
		}

		// 无异常，检查是否有表关系输出
		Optional<String> tableRelationOutput = state.value(TABLE_RELATION_OUTPUT);
		if (tableRelationOutput.isPresent()) {
			return FEASIBILITY_ASSESSMENT_NODE;
		}

		// 无输出，结束流程
		return END;
	}

	/**
	 * 判断异常是否可重试。
	 * @param errorMessage 异常信息
	 * @return 若异常信息以 "RETRYABLE:" 开头则返回 true
	 */
	private boolean isRetryableError(String errorMessage) {
		return errorMessage.startsWith("RETRYABLE:");
	}

}
