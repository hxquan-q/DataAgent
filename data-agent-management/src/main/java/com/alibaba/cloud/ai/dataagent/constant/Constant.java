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
package com.alibaba.cloud.ai.dataagent.constant;

/**
 * 系统全局常量定义类。
 * <p>
 * 包含 DataAgent 工作流图（Graph）中各节点名称、状态键（State Key）、 输出键、以及 Python 代码执行、人类复核、流式事件等相关常量。
 * 这些常量在工作流编排和节点间数据传递中起到关键作用。
 *
 * @author zhangshenghang
 */
public final class Constant {

	private Constant() {

	}

	/** Spring 配置属性前缀 */
	public static final String PROJECT_PROPERTIES_PREFIX = "spring.ai.alibaba.data-agent";

	/** 用户输入内容的状态键 */
	public static final String INPUT_KEY = "input";

	/** 智能体 ID */
	public static final String AGENT_ID = "agentId";

	/** 数据源 ID */
	public static final String DATASOURCE_ID = "datasourceId";

	/** 多轮对话上下文 */
	public static final String MULTI_TURN_CONTEXT = "MULTI_TURN_CONTEXT";

	/** 最终结果输出 */
	public static final String RESULT = "result";

	/** NL2SQL 工作流图名称 */
	public static final String NL2SQL_GRAPH_NAME = "nl2sqlGraph";

	/** v0.2 语义层工作流图名称（双 Graph opt-in：NL2Semantic2SQL） */
	public static final String NL2SQL_SEMANTIC_GRAPH_NAME = "nl2sqlSemanticGraph";

	/** 意图识别节点输出 */
	public static final String INTENT_RECOGNITION_NODE_OUTPUT = "INTENT_RECOGNITION_NODE_OUTPUT";

	/** 查询增强节点输出 */
	public static final String QUERY_ENHANCE_NODE_OUTPUT = "QUERY_ENHANCE_NODE_OUTPUT";

	/** 可行性评估节点输出 */
	public static final String FEASIBILITY_ASSESSMENT_NODE_OUTPUT = "FEASIBILITY_ASSESSMENT_NODE_OUTPUT";

	/** 证据信息 */
	public static final String EVIDENCE = "EVIDENCE";

	/** Schema 召回中的表文档输出 */
	public static final String TABLE_DOCUMENTS_FOR_SCHEMA_OUTPUT = "TABLE_DOCUMENTS_FOR_SCHEMA";

	/** Schema 召回节点输出 */
	public static final String SCHEMA_RECALL_NODE_OUTPUT = "SCHEMA_RECALL_NODE_OUTPUT";

	/** Schema 召回中的列文档输出 */
	public static final String COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT = "COLUMN_DOCUMENTS__FOR_SCHEMA_OUTPUT";

	/** 表关系推断节点输出 */
	public static final String TABLE_RELATION_OUTPUT = "TABLE_RELATION_OUTPUT";

	/** 表关系推断异常输出 */
	public static final String TABLE_RELATION_EXCEPTION_OUTPUT = "TABLE_RELATION_EXCEPTION_OUTPUT";

	/** 表关系推断重试次数 */
	public static final String TABLE_RELATION_RETRY_COUNT = "TABLE_RELATION_RETRY_COUNT";

	/** 生成的语义模型提示词 */
	public static final String GENEGRATED_SEMANTIC_MODEL_PROMPT = "GENEGRATED_SEMANTIC_MODEL_PROMPT";

	/** SQL 生成节点输出 */
	public static final String SQL_GENERATE_OUTPUT = "SQL_GENERATE_OUTPUT";

	/** SQL 生成时 Schema 缺失建议 */
	public static final String SQL_GENERATE_SCHEMA_MISSING_ADVICE = "SQL_GENERATE_SCHEMA_MISSING_ADVICE";

	/** SQL 生成次数 */
	public static final String SQL_GENERATE_COUNT = "SQL_GENERATE_COUNT";

	/** 重新生成 SQL 的原因 */
	public static final String SQL_REGENERATE_REASON = "SQL_REGENERATE_REASON";

	/** SQL 自愈累积错误历史 */
	public static final String SQL_HEAL_ERRORS = "SQL_HEAL_ERRORS";

	/** 语义一致性校验节点输出 */
	public static final String SEMANTIC_CONSISTENCY_NODE_OUTPUT = "SEMANTIC_CONSISTENCY_NODE_OUTPUT";

	/** 计划编排节点输出 */
	public static final String PLANNER_NODE_OUTPUT = "PLANNER_NODE_OUTPUT";

	/** SQL 执行节点输出 */
	public static final String SQL_EXECUTE_NODE_OUTPUT = "SQL_EXECUTE_NODE_OUTPUT";

	/** 图表步骤与服务端渲染图片 URL 的映射 */
	public static final String CHART_IMAGE_MAP = "CHART_IMAGE_MAP";

	/** 结果合理性重试标记 */
	public static final String RESULT_SANITY_RETRY = "RESULT_SANITY_RETRY";

	/** 数据库方言类型 */
	public static final String DB_DIALECT_TYPE = "DB_DIALECT_TYPE";

	/** 当前需要执行的步骤编号 */
	public static final String PLAN_CURRENT_STEP = "PLAN_CURRENT_STEP";

	/** 下一个需要进入的节点 */
	public static final String PLAN_NEXT_NODE = "PLAN_NEXT_NODE";

	/** 计划校验状态 */
	public static final String PLAN_VALIDATION_STATUS = "PLAN_VALIDATION_STATUS";

	/** 计划校验错误信息 */
	public static final String PLAN_VALIDATION_ERROR = "PLAN_VALIDATION_ERROR";

	/** 计划修复次数 */
	public static final String PLAN_REPAIR_COUNT = "PLAN_REPAIR_COUNT";

	// ---- 工作流节点名称 ----

	/** 计划编排节点 */
	public static final String PLANNER_NODE = "PLANNER_NODE";

	/** 计划执行节点 */
	public static final String PLAN_EXECUTOR_NODE = "PLAN_EXECUTOR_NODE";

	/** 意图识别节点 */
	public static final String INTENT_RECOGNITION_NODE = "INTENT_RECOGNITION_NODE";

	/** 证据召回节点 */
	public static final String EVIDENCE_RECALL_NODE = "EVIDENCE_RECALL_NODE";

	/** 查询增强节点 */
	public static final String QUERY_ENHANCE_NODE = "QUERY_ENHANCE_NODE";

	/** 可行性评估节点 */
	public static final String FEASIBILITY_ASSESSMENT_NODE = "FEASIBILITY_ASSESSMENT_NODE";

	/** 报告生成节点 */
	public static final String REPORT_GENERATOR_NODE = "REPORT_GENERATOR_NODE";

	/** Schema 召回节点 */
	public static final String SCHEMA_RECALL_NODE = "SCHEMA_RECALL_NODE";

	/** 表关系推断节点 */
	public static final String TABLE_RELATION_NODE = "TABLE_RELATION_NODE";

	/** SQL 生成节点 */
	public static final String SQL_GENERATE_NODE = "SQL_GENERATE_NODE";

	/** SQL 执行节点 */
	public static final String SQL_EXECUTE_NODE = "SQL_EXECUTE_NODE";

	// ---- v0.2 语义层（NL2Semantic2SQL）节点名称 ----

	/** 语义解析节点（v0.2 双 Graph：nl2sqlSemanticGraph 起点节点） */
	public static final String SEMANTIC_PARSE_NODE = "semantic_parse_node";

	/** 受控 SQL 拼装节点（v0.2 双 Graph：SemanticParseDispatcher 返回值对齐此常量） */
	public static final String BUILD_SQL_NODE = "build_sql_node";

	/** 语义一致性校验节点 */
	public static final String SEMANTIC_CONSISTENCY_NODE = "SEMANTIC_CONSISTENCY_NODE";

	/** 人类反馈节点 */
	public static final String HUMAN_FEEDBACK_NODE = "HUMAN_FEEDBACK_NODE";

	// ---- Python 代码执行相关 ----

	/** Python 代码生成节点 */
	public static final String PYTHON_GENERATE_NODE = "PYTHON_GENERATE_NODE";

	/** Python 代码执行节点 */
	public static final String PYTHON_EXECUTE_NODE = "PYTHON_EXECUTE_NODE";

	/** Python 结果分析节点 */
	public static final String PYTHON_ANALYZE_NODE = "PYTHON_ANALYZE_NODE";

	/** SQL 结果列表（用于 Python 内存中传递） */
	public static final String SQL_RESULT_LIST_MEMORY = "SQL_RESULT_LIST_MEMORY";

	/** Python 执行是否成功 */
	public static final String PYTHON_IS_SUCCESS = "PYTHON_IS_SUCCESS";

	/** Python 重试次数 */
	public static final String PYTHON_TRIES_COUNT = "PYTHON_TRIES_COUNT";

	/** 标记是否进入 Python 执行失败的降级模式（超过最大重试次数后触发） */
	public static final String PYTHON_FALLBACK_MODE = "PYTHON_FALLBACK_MODE";

	/** Python 执行节点输出（成功时输出运行结果，失败时输出错误信息） */
	public static final String PYTHON_EXECUTE_NODE_OUTPUT = "PYTHON_EXECUTE_NODE_OUTPUT";

	/** Python 代码生成节点输出 */
	public static final String PYTHON_GENERATE_NODE_OUTPUT = "PYTHON_GENERATE_NODE_OUTPUT";

	/** Python 结果分析节点输出 */
	public static final String PYTHON_ANALYSIS_NODE_OUTPUT = "PYTHON_ANALYSIS_NODE_OUTPUT";

	// ---- NL2SQL 接口预留相关 ----

	/** 是否仅执行 NL2SQL（跳过后续分析和报告） */
	public static final String IS_ONLY_NL2SQL = "IS_ONLY_NL2SQL";

	// ---- 人类复核相关 ----

	/** 是否启用人类复核 */
	public static final String HUMAN_REVIEW_ENABLED = "HUMAN_REVIEW_ENABLED";

	/** 人类反馈数据载体 */
	public static final String HUMAN_FEEDBACK_DATA = "HUMAN_FEEDBACK_DATA";

	// ---- 流式事件常量 ----

	/** 流式事件：完成 */
	public static final String STREAM_EVENT_COMPLETE = "complete";

	/** 流式事件：错误 */
	public static final String STREAM_EVENT_ERROR = "error";

	// ---- Langfuse 追踪 ----

	/** Langfuse 追踪：透传到 graph state 的 threadId，用于 token 累计 */
	public static final String TRACE_THREAD_ID = "TRACE_THREAD_ID";

}
