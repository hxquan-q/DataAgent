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

import com.alibaba.cloud.ai.dataagent.dto.datasource.SqlRetryDto;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.extern.slf4j.Slf4j;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * SQL 执行分发器，根据 SQL 执行结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>SQL 执行失败：返回 SQL 生成节点重新生成</li>
 * <li>SQL 执行成功：返回计划执行节点继续执行下一步</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 */
@Slf4j
public class SQLExecutorDispatcher implements EdgeAction {

	/**
	 * 根据 SQL 执行结果决定下一个节点。
	 * @param state 工作流全局状态，包含 SQL 重试原因
	 * @return 下一个节点名称：{@value SQL_GENERATE_NODE} 或 {@value PLAN_EXECUTOR_NODE}
	 */
	@Override
	public String apply(OverAllState state) {
		SqlRetryDto retryDto = StateUtil.getObjectValue(state, SQL_REGENERATE_REASON, SqlRetryDto.class);
		if (retryDto.sqlExecuteFail()) {
			log.warn("SQL 执行失败，需要重新生成！");
			return SQL_GENERATE_NODE;
		}
		else {
			log.info("SQL 执行成功，返回计划执行节点。");
			return PLAN_EXECUTOR_NODE;
		}
	}

}
