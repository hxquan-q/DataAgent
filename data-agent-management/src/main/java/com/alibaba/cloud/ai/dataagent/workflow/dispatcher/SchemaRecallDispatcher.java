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

import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.EdgeAction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.TABLE_RELATION_NODE;
import static com.alibaba.cloud.ai.graph.StateGraph.END;

/**
 * Schema 召回分发器，根据 Schema 召回结果决定下一个执行节点。
 *
 * <p>
 * 路由规则：
 * <ul>
 * <li>召回了表文档：进入表关系推断节点</li>
 * <li>未召回表文档：结束流程</li>
 * </ul>
 * </p>
 */
@Slf4j
public class SchemaRecallDispatcher implements EdgeAction {

	/**
	 * 根据 Schema 召回结果决定下一个节点。
	 * @param state 工作流全局状态，包含召回的表文档
	 * @return 下一个节点名称：{@value TABLE_RELATION_NODE} 或 {@code END}
	 * @throws Exception 读取状态时可能抛出的异常
	 */
	@Override
	public String apply(OverAllState state) throws Exception {
		// 获取召回的表文档列表
		List<Document> tableDocuments = StateUtil.getDocumentList(state, TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT);
		if (tableDocuments != null && !tableDocuments.isEmpty())
			return TABLE_RELATION_NODE;
		log.info("未找到表文档，结束流程");
		return END;
	}

}
