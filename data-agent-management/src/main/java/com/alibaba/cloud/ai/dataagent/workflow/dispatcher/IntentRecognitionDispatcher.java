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

import com.alibaba.cloud.ai.dataagent.dto.prompt.IntentRecognitionOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.EVIDENCE_RECALL_NODE;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.INTENT_RECOGNITION_NODE_OUTPUT;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * 意图识别分发器，根据意图识别节点的结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>分类为"《闲聊或无关指令》"：结束流程</li>
 * <li>其他分类（潜在数据分析请求）：进入证据召回节点</li>
 * </ul>
 * </p>
 */
@Slf4j
public class IntentRecognitionDispatcher implements EdgeAction {

	/**
	 * 根据意图识别结果决定下一个节点。
	 * @param state 工作流全局状态，包含意图识别结果
	 * @return 下一个节点名称：{@value EVIDENCE_RECALL_NODE} 或 {@code END}
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取意图识别结果
		IntentRecognitionOutputDTO intentResult = StateUtil.getObjectValue(state, INTENT_RECOGNITION_NODE_OUTPUT,
				IntentRecognitionOutputDTO.class);

		// 结果为空时默认结束流程
		if (intentResult == null || intentResult.getClassification() == null
				|| intentResult.getClassification().trim().isEmpty()) {
			log.warn("意图识别结果为空，默认结束流程");
			return END;
		}

		String classification = intentResult.getClassification();

		// 根据分类结果决定下一个节点
		if ("《闲聊或无关指令》".equals(classification)) {
			log.warn("意图分类为闲聊或无关指令，结束对话");
			return END;
		}
		else {
			log.info("意图分类为潜在数据分析请求，进入证据召回节点");
			return EVIDENCE_RECALL_NODE;
		}
	}

}
