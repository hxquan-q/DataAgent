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

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 语义一致性校验分发器，根据语义一致性校验结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>校验通过：进入 SQL 执行节点</li>
 * <li>校验未通过：返回 SQL 生成节点重新生成</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
public class SemanticConsistenceDispatcher implements EdgeAction {

	/**
	 * 根据语义一致性校验结果决定下一个节点。
	 * @param state 工作流全局状态，包含语义一致性校验结果
	 * @return 下一个节点名称：{@value SQL_EXECUTE_NODE} 或 {@value SQL_GENERATE_NODE}
	 */
	@Override
	public String apply(OverAllState state) {
		Boolean validate = (Boolean) state.value(SEMANTIC_CONSISTENCY_NODE_OUTPUT).orElse(false);
		log.info("语义一致性校验结果: {}，跳转节点配置", validate);
		if (validate) {
			log.info("语义一致性校验通过，跳转到 SQL 执行节点。");
			return SQL_EXECUTE_NODE;
		}
		else {
			log.info("语义一致性校验未通过，跳转到 SQL 生成节点。");
			return SQL_GENERATE_NODE;
		}
	}

}
