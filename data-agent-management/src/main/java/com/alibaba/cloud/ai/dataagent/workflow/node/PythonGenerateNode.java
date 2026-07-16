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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.dto.planner.ExecutionStep;
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.MarkdownParserUtil;
import com.alibaba.cloud.ai.dataagent.util.PlanProcessUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * Python 代码生成节点，位于 SQL 执行之后、Python 执行之前。
 *
 * <p>
 * 该节点根据 Schema、SQL 查询结果和当前执行步骤的要求，调用大模型生成 Python 代码。
 * 若上一次生成的代码执行失败，会将失败信息和错误内容反馈给大模型以重新生成。
 * </p>
 *
 * @author vlsmb
 * @since 2025/7/30
 */
@Slf4j
@Component
public class PythonGenerateNode implements NodeAction {

	/** 范例数据采样数量，用于限制提示词中包含的数据量 */
	private static final int SAMPLE_DATA_NUMBER = 5;

	private final ObjectMapper objectMapper;

	private final CodeExecutorProperties codeExecutorProperties;

	private final LlmService llmService;

	public PythonGenerateNode(CodeExecutorProperties codeExecutorProperties, LlmService llmService) {
		this.codeExecutorProperties = codeExecutorProperties;
		this.llmService = llmService;
		this.objectMapper = new ObjectMapper().setSerializationInclusion(JsonInclude.Include.NON_NULL);
	}

	/**
	 * 执行 Python 代码生成逻辑。
	 * <p>
	 * 获取 Schema、SQL 结果和执行步骤参数，构建系统提示词并调用大模型生成 Python 代码。 若上次代码运行失败，将错误信息附加到用户提示中。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含生成的 Python 代码的 Map，key 为 {@value PYTHON_GENERATE_NODE_OUTPUT}
	 * @throws Exception 调用大模型或序列化时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		// 获取上下文：Schema、SQL 结果和上次执行状态
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
				? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();
		boolean codeRunSuccess = StateUtil.getObjectValue(state, PYTHON_IS_SUCCESS, Boolean.class, true);
		int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

		// 构建用户提示
		String userPrompt = StateUtil.getCanonicalQuery(state);
		if (!codeRunSuccess) {
			// 上次生成的 Python 代码运行失败，将错误信息反馈给大模型
			String lastCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			String lastError = StateUtil.getStringValue(state, PYTHON_EXECUTE_NODE_OUTPUT);
			userPrompt += String.format("""
					上次尝试生成的Python代码运行失败，请你重新生成符合要求的Python代码。
					【上次生成代码】
					```python
					%s
					```
					【运行错误信息】
					```
					%s
					```
					""", lastCode, lastError);
		}

		// 获取当前执行步骤及工具参数
		ExecutionStep executionStep = PlanProcessUtil.getCurrentExecutionStep(state);
		ExecutionStep.ToolParameters toolParameters = executionStep.getToolParameters();

		// 加载 Python 代码生成模板并渲染系统提示词
		String systemPrompt = PromptConstant.getPythonGeneratorPromptTemplate()
			.render(Map.of("python_memory", codeExecutorProperties.getLimitMemory().toString(), "python_timeout",
					codeExecutorProperties.getCodeTimeout(), "database_schema",
					// R14: 使用与 SQL 节点一致的紧凑 schema 文本，避免全量 JSON 撑爆 python 生成 prompt
					PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true), "sample_input",
					limitJson(objectMapper.writeValueAsString(sqlResults.stream().limit(SAMPLE_DATA_NUMBER).toList()), MAX_SAMPLE_JSON_CHARS),
					"plan_description",
					limitJson(objectMapper.writeValueAsString(toolParameters), MAX_PLAN_JSON_CHARS)));

		// 调用大模型生成 Python 代码
		Flux<ChatResponse> pythonGenerateFlux = llmService.call(systemPrompt, userPrompt);

		// 创建流式生成器，收集大模型输出并写入结果
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, aiResponse -> {
					// 部分大模型仍会输出 Markdown 标记（尽管提示词中已强调不要输出）
					aiResponse = aiResponse.substring(TextType.PYTHON.getStartSign().length(),
							aiResponse.length() - TextType.PYTHON.getEndSign().length());
					aiResponse = MarkdownParserUtil.extractRawText(aiResponse);
					log.info("生成的 Python 代码: {}", aiResponse);
					return Map.of(PYTHON_GENERATE_NODE_OUTPUT, aiResponse, PYTHON_TRIES_COUNT, triesCount + 1);
				},
				Flux.concat(Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getStartSign())),
						pythonGenerateFlux,
						Flux.just(ChatResponseUtil.createPureResponse(TextType.PYTHON.getEndSign()))));

		return Map.of(PYTHON_GENERATE_NODE_OUTPUT, generator);
	}


	private static final int MAX_PLAN_JSON_CHARS = 2_000;

	private static final int MAX_SAMPLE_JSON_CHARS = 3_000;

	static String limitJson(String json, int maxChars) {
		if (json == null) {
			return "";
		}
		if (maxChars <= 0 || json.length() <= maxChars) {
			return json;
		}
		return json.substring(0, maxChars) + "…";
	}


}
