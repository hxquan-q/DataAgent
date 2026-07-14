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
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * Python 执行分发器，根据 Python 执行结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>降级模式：跳过重试直接进入分析节点</li>
 * <li>执行失败且超过最大重试次数：结束流程</li>
 * <li>执行失败且未超过最大重试次数：返回 Python 生成节点重新生成</li>
 * <li>执行成功：进入 Python 分析节点</li>
 * </ul>
 * </p>
 *
 * @author vlsmb
 * @since 2025/7/29
 */
@Slf4j
public class PythonExecutorDispatcher implements EdgeAction {

	private final CodeExecutorProperties codeExecutorProperties;

	public PythonExecutorDispatcher(CodeExecutorProperties codeExecutorProperties) {
		this.codeExecutorProperties = codeExecutorProperties;
	}

	/**
	 * 根据 Python 执行结果决定下一个节点。
	 * @param state 工作流全局状态，包含 Python 执行结果和重试次数
	 * @return 下一个节点名称：{@value PYTHON_ANALYZE_NODE}、{@value PYTHON_GENERATE_NODE} 或 {@code END}
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		// 检查是否为降级模式
		boolean isFallbackMode = StateUtil.getObjectValue(state, PYTHON_FALLBACK_MODE, Boolean.class, false);
		if (isFallbackMode) {
			log.warn("Python 执行进入降级模式，跳过重试直接进入分析节点");
			return PYTHON_ANALYZE_NODE;
		}

		// 判断执行是否成功
		boolean isSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, false);
		if (!isSuccess) {
			String message = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
			log.error("Python 执行节点错误: {}", message);
			int tries = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);
			if (tries >= codeExecutorProperties.getPythonMaxTriesCount()) {
				// 超过最大重试次数，结束流程
				log.error("Python 执行失败且已超过最大重试次数（已尝试次数：{}），流程终止", tries);
				return END;
			}
			else {
				// 未超过最大重试次数，返回代码生成节点重新生成
				return PYTHON_GENERATE_NODE;
			}
		}
		// 执行成功，进入代码执行结果分析节点
		return PYTHON_ANALYZE_NODE;
	}

}
