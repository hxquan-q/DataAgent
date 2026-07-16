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
package com.alibaba.cloud.ai.dataagent.workflow.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 人工反馈节点，用于执行计划的人工审核与修改。
 *
 * <p>
 * 该节点在工作流中处理用户对执行计划的反馈。若用户批准计划，则进入计划执行节点； 若用户拒绝计划，则返回计划生成节点重新生成，并将用户反馈作为提示信息传入。
 * 支持最大修复次数（默认 3 次）限制。
 * </p>
 *
 * @author Makoto
 */
@Slf4j
@Component
public class HumanFeedbackNode implements NodeAction {

	private static final int MAX_FEEDBACK_CHARS = 1_200;


	/**
	 * 处理人工反馈逻辑。
	 * <p>
	 * 检查修复次数是否超限，解析反馈数据。若反馈为空则等待用户输入； 若用户批准则进入执行节点，否则返回计划生成节点并清空旧计划。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含下一个节点路由信息的 Map
	 * @throws Exception 处理反馈时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		Map<String, Object> updated = new HashMap<>();

		// 检查最大修复次数
		int repairCount = StateUtil.getObjectValue(state, PLAN_REPAIR_COUNT, Integer.class, 0);
		if (repairCount >= 3) {
			log.warn("超过最大修复次数（3 次），结束流程");
			updated.put("human_next_node", "END");
			return updated;
		}

		// 获取反馈数据
		Map<String, Object> feedbackData = StateUtil.getObjectValue(state, HUMAN_FEEDBACK_DATA, Map.class, Map.of());
		if (feedbackData.isEmpty()) {
			// 反馈数据为空，等待用户输入
			updated.put("human_next_node", "WAIT_FOR_FEEDBACK");
			return updated;
		}

		// 解析反馈结果：是否批准
		Object approvedValue = feedbackData.getOrDefault("feedback", true);
		boolean approved = approvedValue instanceof Boolean approvedBoolean ? approvedBoolean
				: Boolean.parseBoolean(approvedValue.toString());

		if (approved) {
			// 用户批准计划，进入执行节点
			log.info("计划已批准 → 进入执行");
			updated.put("human_next_node", PLAN_EXECUTOR_NODE);
			updated.put(HUMAN_REVIEW_ENABLED, false);
		}
		else {
			// 用户拒绝计划，返回计划生成节点重新生成
			log.info("计划被拒绝 → 重新生成（第 {} 次尝试）", repairCount + 1);
			updated.put("human_next_node", PLANNER_NODE);
			updated.put(PLAN_REPAIR_COUNT, repairCount + 1);
			updated.put(PLAN_CURRENT_STEP, 1);
			updated.put(HUMAN_REVIEW_ENABLED, true);

			// 保存用户反馈内容
			String feedbackContent = feedbackData.getOrDefault("feedback_content", "").toString();
			// R25: 拒绝反馈有界（与 PlannerNode 上限对齐）
			if (StringUtils.hasLength(feedbackContent) && feedbackContent.length() > MAX_FEEDBACK_CHARS) {
				feedbackContent = feedbackContent.substring(0, MAX_FEEDBACK_CHARS) + "…";
			}
			updated.put(PLAN_VALIDATION_ERROR, StringUtils.hasLength(feedbackContent) ? feedbackContent : "用户拒绝了计划");
			// 清空旧的计划输出
			updated.put(PLANNER_NODE_OUTPUT, "");
		}

		return updated;
	}

}
