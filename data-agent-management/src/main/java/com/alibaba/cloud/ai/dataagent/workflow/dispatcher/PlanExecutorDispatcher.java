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
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 计划执行分发器，根据计划校验状态决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>校验通过：进入计划中指定的下一个节点（或结束流程）</li>
 * <li>校验失败且超过最大修复次数：结束流程</li>
 * <li>校验失败且未超过最大修复次数：返回计划生成节点修复</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
public class PlanExecutorDispatcher implements EdgeAction {

	/** 最大修复次数 */
	private static final int MAX_REPAIR_ATTEMPTS = 2;

	/**
	 * 根据计划校验状态决定下一个节点。
	 * @param state 工作流全局状态，包含校验状态和修复次数
	 * @return 下一个节点名称
	 */
	@Override
	public String apply(OverAllState state) {
		boolean validationPassed = StateUtil.getObjectValue(state, PLAN_VALIDATION_STATUS, Boolean.class, false);

		if (validationPassed) {
			// 校验通过，进入下一个执行步骤
			log.info("计划校验通过，进入下一步。");
			String nextNode = state.value(PLAN_NEXT_NODE, END);
			// 若返回 "END"，直接返回 END 常量
			if ("END".equals(nextNode)) {
				log.info("计划执行成功完成。");
				return END;
			}
			return nextNode;
		}
		else {
			// 校验失败，检查修复次数并决定是否重试
			int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);

			if (repairCount > MAX_REPAIR_ATTEMPTS) {
				log.error("计划修复次数超过最大限制 {}，终止执行。", MAX_REPAIR_ATTEMPTS);
				return END;
			}

			log.warn("计划校验失败，返回计划生成节点进行修复。状态中的修复次数: {}。", repairCount);
			return PLANNER_NODE;
		}
	}

}
