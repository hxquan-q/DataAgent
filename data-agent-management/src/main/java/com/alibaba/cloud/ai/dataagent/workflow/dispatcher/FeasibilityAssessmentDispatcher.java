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
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.FEASIBILITY_ASSESSMENT_NODE_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.PLANNER_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 可行性评估分发器，根据可行性评估结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>需求类型为"《数据分析》"：进入计划生成节点</li>
 * <li>其他需求类型：结束流程</li>
 * </ul>
 * </p>
 */
@Slf4j
public class FeasibilityAssessmentDispatcher implements EdgeAction {

	/**
	 * 根据可行性评估结果决定下一个节点。
	 * @param state 工作流全局状态，包含可行性评估结果
	 * @return 下一个节点名称：{@value PLANNER_NODE} 或 {@code END}
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		// value 的值与 resources/feasibility-assessment.txt 的输出一致，例如
		// 【需求类型】：《数据分析》
		// 【语种类型】：《中文》
		// 【需求内容】：查询所有"核心用户"的数量
		String value = state.value(FEASIBILITY_ASSESSMENT_NODE_OUTPUT, END);

		if (value != null && value.contains("【需求类型】：《数据分析》")) {
			log.info("[可行性评估分发器] 需求类型为数据分析，进入计划生成节点");
			return PLANNER_NODE;
		}
		else {
			log.info("[可行性评估分发器] 需求类型非数据分析，结束流程");
			return END;
		}
	}

}
