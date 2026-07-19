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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * 语义对象独立验证者（Maker/Checker 分离的 Checker 侧，η₅ 监督控制）。
 * <p>
 * 参考 Jimi 的 {@code GoalCommandHandler.verify} —— 执行者（Maker）生成产物后，由独立的验证者
 * （Checker）二次评估，避免"自己批改自己作业"的认知偏差。
 * </p>
 * <p>
 * 在 DataAgent 的 NL2Semantic2SQL 流水线中：
 * <ul>
 * <li><b>Maker（执行者）</b>：{@code SemanticParseNode} 用 LLM 从候选指标/维度中选择并填值，产出
 * {@link SemanticObject}。该 LLM 实例既负责理解用户意图，也负责挑选指标。</li>
 * <li><b>Checker（本服务，独立验证者）</b>：再次调用 LLM（与 Maker 解耦的调用，便于未来切换为不同实例/模型）， 站在"审查者"视角评估
 * {@code SemanticObject} 是否合理地回答了用户问题 —— 指标是否匹配、口径是否清晰、 是否遗漏关键过滤条件。只返回
 * {@code valid + reason}，不重新生成产物。</li>
 * </ul>
 * </p>
 * <p>
 * 这与 {@link SemanticValidator}（基于规则的完整性 Checker，η₂ 第二道防线，校验指标存在性、口径版本歧义） 互补：规则校验快但刚性，LLM
 * 验证慢但能捕捉语义层面的不合理（如"查去年收入却选了今年的时间范围"）。 两者串联：先规则（{@code SemanticValidator}）拦截结构性错误，再
 * LLM（本服务）把关语义合理性。
 * </p>
 *
 * @author dataagent
 */
@Service
@AllArgsConstructor
@Slf4j
public class SemanticVerificationService {

	/** LLM 调用服务（Checker 侧调用入口，与 Maker 的 LlmService 实例解耦，便于未来切换独立模型） */
	private final LlmService llmService;

	/** JSON 解析工具（容错解析 LLM 返回） */
	private final JsonParseUtil jsonParseUtil;

	/** Jackson 序列化器（将 SemanticObject 序列化为 JSON 注入 prompt） */
	private final ObjectMapper objectMapper;

	/**
	 * 独立 LLM 验证语义对象是否合理回答了用户问题（Checker 侧）。
	 * <p>
	 * 构建验证 prompt（注入用户问题 + 语义对象 JSON + 评估指令），调用 {@link LlmService#callUser(String)}
	 * 获取流式响应，同步聚合为完整文本后用 {@link JsonParseUtil#tryConvertToObject(String, Class)} 解析为
	 * {@link VerificationResult}。LLM 调用或解析异常时返回 {@link VerificationResult#fail(String)}，
	 * 不中断主流程（监督控制应 fail-open，避免验证器自身故障阻塞用户）。
	 * </p>
	 * @param semanticObject 待验证的语义对象（Maker 产物）
	 * @param userQuery 用户原始问题
	 * @return 验证结果（valid + reason）
	 */
	public VerificationResult verify(SemanticObject semanticObject, String userQuery) {
		if (semanticObject == null) {
			return VerificationResult.fail("语义对象为空，无法验证");
		}
		try {
			String semanticJson = objectMapper.writeValueAsString(semanticObject);
			String prompt = buildVerificationPrompt(userQuery, semanticJson);
			log.debug("语义验证 prompt：\n{}", prompt);

			// 调用 LLM 并同步聚合完整文本（block 模式，监督控制需拿到完整结论）
			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String resultText = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();

			if (resultText == null || resultText.isBlank()) {
				log.warn("语义验证 LLM 返回空内容，userQuery={}", userQuery);
				return VerificationResult.fail("LLM 返回空内容");
			}

			VerificationResult result = jsonParseUtil.tryConvertToObject(resultText, VerificationResult.class);
			if (result == null) {
				log.warn("语义验证结果解析失败，原始返回：{}", resultText);
				return VerificationResult.fail("LLM 返回解析失败: " + truncate(resultText));
			}
			log.info("语义验证完成：valid={}, reason={}", result.valid(), result.reason());
			return result;
		}
		catch (Exception e) {
			log.error("语义验证异常，userQuery={}, error={}", userQuery, e.getMessage(), e);
			return VerificationResult.fail("验证异常: " + e.getMessage());
		}
	}

	/**
	 * 构建验证 prompt：注入用户问题 + 语义对象 JSON + 评估指令。
	 * <p>
	 * 指令要求 LLM 评估：指标是否匹配、口径是否清晰、是否遗漏关键过滤。只返回 JSON
	 * {@code {"valid":true/false,"reason":"..."}}。
	 * </p>
	 */
	private String buildVerificationPrompt(String userQuery, String semanticJson) {
		return """
				你是一名严谨的语义对象审查者。请评估下方的语义对象是否合理地回答了用户问题。

				【用户问题】
				%s

				【语义对象 JSON】
				%s

				【评估要点】
				1. 选择的指标是否与用户问题匹配；
				2. 口径版本是否清晰（若涉及多口径）；
				3. 是否遗漏关键过滤条件（如时间范围、业务限定）；
				4. 分组维度是否合理。

				【输出要求】
				只返回 JSON，不要输出 Markdown 代码块标记或任何额外说明：
				{"valid":true/false,"reason":"简要说明判断依据"}
				""".formatted(userQuery, semanticJson);
	}

	/** 截断长文本用于日志/错误信息。 */
	private String truncate(String text) {
		if (text == null) {
			return "";
		}
		return text.length() > 200 ? text.substring(0, 200) + "..." : text;
	}

	/**
	 * 验证结果（Checker 产物）。
	 *
	 * @param valid 是否通过验证（true=语义对象合理，可进 BuildSQL）
	 * @param reason 判断依据（valid=false 时说明为何不合理，供反问或日志使用）
	 */
	public record VerificationResult(boolean valid, String reason) {

		/**
		 * 快速构造"通过"结果。
		 * @return valid=true 的结果
		 */
		public static VerificationResult ok() {
			return new VerificationResult(true, "语义对象合理");
		}

		/**
		 * 快速构造"不通过"结果。
		 * @param reason 不通过原因
		 * @return valid=false 的结果
		 */
		public static VerificationResult fail(String reason) {
			return new VerificationResult(false, reason);
		}

	}

}
