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

import com.alibaba.cloud.ai.dataagent.dto.schema.SchemaDTO;
import com.alibaba.cloud.ai.dataagent.enums.TextType;
import com.alibaba.cloud.ai.dataagent.dto.planner.Plan;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;
import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.prompt.PromptHelper;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.ChatResponseUtil;
import com.alibaba.cloud.ai.dataagent.util.FluxUtil;
import com.alibaba.cloud.ai.dataagent.util.StateUtil;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.*;

/**
 * 计划生成节点，位于可行性评估之后、计划执行之前。
 *
 * <p>
 * 该节点根据规范化查询、Schema 和证据信息，通过大模型生成执行计划（{@link Plan}）。 执行计划定义了后续每一步需要使用的工具（SQL 生成、Python
 * 生成、报告生成等）。 支持两种模式：
 * <ul>
 * <li>纯 NL2SQL 模式：直接生成单步 SQL 计划</li>
 * <li>完整规划模式：生成多步骤执行计划，并可基于用户反馈重新生成</li>
 * </ul>
 * </p>
 *
 * @author zhangshenghang
 * @see FeasibilityAssessmentNode
 * @see PlanExecutorNode
 */
@Slf4j
@Component
@AllArgsConstructor
public class PlannerNode implements NodeAction {

	private final LlmService llmService;

	/**
	 * 执行计划生成逻辑。
	 * <p>
	 * 根据是否为纯 NL2SQL 模式选择不同的生成策略，最终将执行计划写入状态。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 包含执行计划的 Map，key 为 {@value PLANNER_NODE_OUTPUT}
	 * @throws Exception 调用大模型时可能抛出的异常
	 */
	@Override
	public Map<String, Object> apply(OverAllState state) throws Exception {
		// 判断是否为纯 NL2SQL 模式
		Boolean onlyNl2sql = state.value(IS_ONLY_NL2SQL, false);

		// 根据模式选择不同的生成策略
		Flux<ChatResponse> flux = onlyNl2sql ? handleNl2SqlOnly() : handlePlanGenerate(state);

		// 包装 JSON 标记
		Flux<ChatResponse> chatResponseFlux = Flux.concat(
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getStartSign())), flux,
				Flux.just(ChatResponseUtil.createPureResponse(TextType.JSON.getEndSign())));
		Flux<GraphResponse<StreamingOutput>> generator = FluxUtil.createStreamingGeneratorWithMessages(this.getClass(),
				state, v -> Map.of(PLANNER_NODE_OUTPUT, v.substring(TextType.JSON.getStartSign().length(),
						v.length() - TextType.JSON.getEndSign().length())),
				chatResponseFlux);

		return Map.of(PLANNER_NODE_OUTPUT, generator);
	}

	/**
	 * 处理完整计划生成。
	 * <p>
	 * 根据规范化查询、Schema、语义模型和证据信息，调用大模型生成多步骤执行计划。 若存在校验错误（用户反馈），则基于反馈重新生成。
	 * </p>
	 * @param state 工作流全局状态
	 * @return 计划生成的流式响应
	 */
	private Flux<ChatResponse> handlePlanGenerate(OverAllState state) {
		// 获取查询增强节点的输出
		String canonicalQuery = StateUtil.getCanonicalQuery(state);
		log.info("使用处理后的查询进行计划生成: {}", canonicalQuery);

		// 检查是否为修复模式（存在用户反馈导致的校验错误）
		String validationError = StateUtil.getStringValue(state, PLAN_VALIDATION_ERROR, null);
		if (validationError != null) {
			log.info("基于用户反馈重新生成计划: {}", validationError);
		}
		else {
			log.info("生成初始计划");
		}

		// 构建提示参数：语义模型、Schema
		String semanticModel = (String) state.value(GENEGRATED_SEMANTIC_MODEL_PROMPT).orElse("");
		SchemaDTO schemaDTO = StateUtil.getObjectValue(state, TABLE_RELATION_OUTPUT, SchemaDTO.class);
		String schemaStr = PromptHelper.buildMixMacSqlDbPrompt(schemaDTO, true);

		// 构建用户提示
		String userPrompt = buildUserPrompt(canonicalQuery, validationError, state);
		String evidence = StateUtil.getStringValue(state, EVIDENCE);

		// 构建模板参数并渲染提示词
		BeanOutputConverter<Plan> beanOutputConverter = new BeanOutputConverter<>(Plan.class);
		Map<String, Object> params = Map.of("user_question", userPrompt, "schema", schemaStr, "evidence", evidence,
				"semantic_model", semanticModel, "plan_validation_error", formatValidationError(validationError),
				"format", beanOutputConverter.getFormat());
		// 生成计划提示词
		String plannerPrompt = PromptConstant.getPlannerPromptTemplate().render(params);
		log.debug("计划生成提示词如下 \n{}\n", plannerPrompt);

		// 调用大模型生成计划
		return llmService.callUser(plannerPrompt);
	}

	/**
	 * 处理纯 NL2SQL 模式，直接返回单步 SQL 计划。
	 * @return 单步 SQL 计划的流式响应
	 */
	private Flux<ChatResponse> handleNl2SqlOnly() {
		return Flux.just(ChatResponseUtil.createPureResponse(Plan.nl2SqlPlan()));
	}

	/**
	 * 构建用户提示，若存在校验错误则附加用户反馈和被拒绝的计划。
	 * @param input 用户输入
	 * @param validationError 校验错误（用户反馈）
	 * @param state 工作流全局状态
	 * @return 构建完成的用户提示字符串
	 */
	private String buildUserPrompt(String input, String validationError, OverAllState state) {
		if (validationError == null) {
			return input;
		}

		// 附加用户反馈、原始问题和被拒绝的旧计划
		String previousPlan = StateUtil.getStringValue(state, PLANNER_NODE_OUTPUT, "");
		return String.format(
				"重要提示：用户拒绝了之前的计划，反馈内容：\"%s\"\n\n" + "原始问题：%s\n\n" + "被拒绝的旧计划：\n%s\n\n" + "关键要求：请根据用户反馈（\"%s\"）生成新的计划",
				validationError, input, previousPlan, validationError);
	}

	/**
	 * 格式化校验错误信息，用于强调用户反馈的重要性。
	 * @param validationError 校验错误
	 * @return 格式化后的错误信息字符串
	 */
	private String formatValidationError(String validationError) {
		return validationError != null ? String.format("**用户反馈（关键）**: %s\n\n**必须融合此反馈。**", validationError) : "";
	}

}
