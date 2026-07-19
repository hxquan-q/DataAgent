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

import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.service.code.CodePoolExecutorService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.JsonUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
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
 * Python 代码执行节点，位于 Python 代码生成之后、结果分析之前。
 *
 * <p>
 * 该节点接收生成的 Python 代码，在代码池中执行，并获取运行结果。 支持最大重试次数控制和降级兜底策略：当代码执行失败且超过最大重试次数时，
 * 启动降级模式以保障流程继续执行。
 * </p>
 *
 * @author vlsmb
 * @since 2025/7/29
 */
@Slf4j
@Component
public class PythonExecuteNode implements NodeAction {

	private final CodePoolExecutorService codePoolExecutor;

	private final ObjectMapper objectMapper;

	private final JsonParseUtil jsonParseUtil;

	private final CodeExecutorProperties codeExecutorProperties;

	public PythonExecuteNode(CodePoolExecutorService codePoolExecutor, JsonParseUtil jsonParseUtil,
			CodeExecutorProperties codeExecutorProperties) {
		this.codePoolExecutor = codePoolExecutor;
		this.objectMapper = JsonUtil.getObjectMapper();
		this.jsonParseUtil = jsonParseUtil;
		this.codeExecutorProperties = codeExecutorProperties;
	}

	/**
	 * 执行 Python 代码逻辑。
	 * <p>
	 * 从状态中获取 Python 代码和 SQL 结果，构建任务请求并在代码池中执行。 执行成功则解析标准输出；执行失败则根据重试次数决定重新生成或启用降级模式。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含 Python 执行结果的 Map，key 为 {@value PYTHON_EXECUTE_NODE_OUTPUT}
	 * @throws Exception 执行代码时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {

		try {
			// 获取上下文：Python 代码和 SQL 结果
			String pythonCode = StateUtil.getStringValue(state, PYTHON_GENERATE_NODE_OUTPUT);
			List<Map<String, String>> sqlResults = StateUtil.hasValue(state, SQL_RESULT_LIST_MEMORY)
					? StateUtil.getListValue(state, SQL_RESULT_LIST_MEMORY) : new ArrayList<>();

			// 检查重试次数
			int triesCount = StateUtil.getObjectValue(state, PYTHON_TRIES_COUNT, Integer.class, 0);

			// 构建代码执行任务请求
			CodePoolExecutorService.TaskRequest taskRequest = new CodePoolExecutorService.TaskRequest(pythonCode,
					objectMapper.writeValueAsString(sqlResults), null);

			// 在代码池中执行 Python 代码
			CodePoolExecutorService.TaskResponse taskResponse = this.codePoolExecutor.runTask(taskRequest);
			if (!taskResponse.isSuccess()) {
				// 执行失败，构建错误信息
				String errorMsg = "Python 执行失败！\n标准输出: " + taskResponse.stdOut() + "\n标准错误: " + taskResponse.stdErr()
						+ "\n异常信息: " + taskResponse.exceptionMsg();
				log.error(errorMsg);

				// 检查是否超过最大重试次数
				if (triesCount >= codeExecutorProperties.getPythonMaxTriesCount()) {
					log.error("Python 执行失败且已超过最大重试次数（已尝试次数：{}），启动降级兜底逻辑。错误信息: {}", triesCount, errorMsg);

					// 降级模式输出
					String fallbackOutput = "{}";

					Flux<ChatResponse> fallbackDisplayFlux = Flux.create(emitter -> {
						emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
						emitter.next(ChatResponseUtil.createResponse("Python代码执行失败已超过最大重试次数，采用降级策略继续处理。"));
						emitter.complete();
					});

					Flux<GraphResponse<StreamingOutput>> fallbackGenerator = FluxUtil
						.createStreamingGeneratorWithMessages(this.getClass(), state,
								v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackOutput, PYTHON_IS_SUCCESS, false,
										PYTHON_FALLBACK_MODE, true),
								fallbackDisplayFlux);

					return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, fallbackGenerator);
				}

				throw new RuntimeException(errorMsg);
			}

			// Python 输出的 JSON 字符串可能包含 Unicode 转义形式，需要解析回汉字
			String stdout = taskResponse.stdOut();
			Object value = jsonParseUtil.tryConvertToObject(stdout, Object.class);
			if (value != null) {
				stdout = objectMapper.writeValueAsString(value);
			}
			String finalStdout = stdout;

			log.info("Python 执行成功！标准输出: {}", finalStdout);

			// 创建展示流，仅用于提升用户体验
			Flux<ChatResponse> displayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createResponse("标准输出："));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign()));
				emitter.next(ChatResponseUtil.createResponse(finalStdout));
				emitter.next(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign()));
				emitter.next(ChatResponseUtil.createResponse("Python代码执行成功！"));
				emitter.complete();
			});

			// 使用工具类创建生成器，返回预先计算好的业务结果
			Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(
					this.getClass(), state,
					v -> Map.of(PYTHON_EXECUTE_NODE_OUTPUT, finalStdout, PYTHON_IS_SUCCESS, true), displayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
		catch (Exception e) {
			String errorMessage = e.getMessage();
			log.error("Python 执行异常: {}", errorMessage);

			// 准备错误结果
			Map<String, Object> errorResult = Map.of(PYTHON_EXECUTE_NODE_OUTPUT, errorMessage, PYTHON_IS_SUCCESS,
					false);

			// 创建错误展示流
			Flux<ChatResponse> errorDisplayFlux = Flux.create(emitter -> {
				emitter.next(ChatResponseUtil.createResponse("开始执行Python代码..."));
				emitter.next(ChatResponseUtil.createResponse("Python代码执行失败: " + errorMessage));
				emitter.complete();
			});

			// 使用工具类创建错误生成器
			var generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(), state, v -> errorResult,
					errorDisplayFlux);

			return Map.of(PYTHON_EXECUTE_NODE_OUTPUT, generator);
		}
	}

}
