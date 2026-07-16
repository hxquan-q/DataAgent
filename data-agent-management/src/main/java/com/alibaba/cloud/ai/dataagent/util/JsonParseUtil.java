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
package com.alibaba.cloud.ai.dataagent.util;

import com.alibaba.cloud.ai.dataagent.prompt.PromptConstant;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * JSON 解析工具类，支持自动修复格式错误的 JSON。
 * <p>
 * 当原始 JSON 解析失败时，会调用 LLM 对 JSON 进行修复，最多重试 {@value #MAX_RETRY_COUNT} 次；
 * 解析前会自动剥离思考过程（{@code </think>} 标签之前的内容）以及 Markdown 代码块。
 * </p>
 */
@Slf4j
@Component
@AllArgsConstructor
public class JsonParseUtil {

	private LlmService llmService;

	/** LLM 修复 JSON 的最大重试次数 */
	private static final int MAX_RETRY_COUNT = 3;

	/** 思考过程的结束标签 */
	private static final String THINK_END_TAG = "</think>";

	/**
	 * 尝试将 JSON 字符串转换为指定类型的对象。
	 * <p>
	 * 解析失败时会调用 LLM 修复 JSON 并重试，最多重试 {@value #MAX_RETRY_COUNT} 次。
	 * </p>
	 * @param <T> 目标类型
	 * @param json JSON 字符串
	 * @param clazz 目标类型的 Class 对象
	 * @return 转换后的对象
	 * @throws IllegalArgumentException 当入参非法或在多次修复后仍无法解析时抛出
	 */
	public <T> T tryConvertToObject(String json, Class<T> clazz) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(clazz, "Target class cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, clazz));
	}

	/**
	 * 尝试将 JSON 字符串转换为指定类型，支持通过 {@link TypeReference} 描述复杂泛型类型（如 {@code List<String>}）。
	 * @param <T> 目标类型
	 * @param json JSON 字符串
	 * @param typeReference 类型引用
	 * @return 转换后的对象
	 * @throws IllegalArgumentException 当入参非法或在多次修复后仍无法解析时抛出
	 */
	public <T> T tryConvertToObject(String json, TypeReference<T> typeReference) {
		Assert.hasText(json, "Input JSON string cannot be null or empty");
		Assert.notNull(typeReference, "TypeReference cannot be null");

		return tryConvertToObjectInternal(json, (mapper, currentJson) -> mapper.readValue(currentJson, typeReference));
	}

	/**
	 * 内部通用解析方法：先尝试直接解析，失败后调用 LLM 修复并重试。
	 * @param <T> 目标类型
	 * @param json JSON 字符串
	 * @param parser 具体的解析函数
	 * @return 转换后的对象
	 * @throws IllegalArgumentException 当多次修复后仍无法解析时抛出
	 */
	private <T> T tryConvertToObjectInternal(String json, JsonParserFunction<T> parser) {
		log.info("Trying to convert JSON to object: {}", json);
		// 先剥离思考过程标签
		String currentJson = removeThinkTags(json);
		Exception lastException = null;
		ObjectMapper objectMapper = JsonUtil.getObjectMapper();

		// 第一次尝试直接解析
		try {
			return parser.parse(objectMapper, currentJson);
		}
		catch (JsonProcessingException e) {
			log.warn("Initial parsing failed, preparing to call LLM: {}", e.getMessage());
		}

		// 直接解析失败，进入 LLM 修复重试流程
		for (int i = 0; i < MAX_RETRY_COUNT; i++) {
			try {
				currentJson = callLlmToFix(currentJson,
						lastException != null ? lastException.getMessage() : "Unknown error");

				return parser.parse(objectMapper, currentJson);
			}
			catch (JsonProcessingException e) {
				lastException = e;
				log.warn("Still failed after {} fix attempt: {}", i + 1, e.getMessage());

				// 最后一次重试失败时记录完整上下文，便于排查
				if (i == MAX_RETRY_COUNT - 1) {
					log.error("Finally failed after {} fix attempts", MAX_RETRY_COUNT);
					log.warn("Last fix result: {}", currentJson);
				}
			}
		}

		throw new IllegalArgumentException(
				String.format("Failed to parse JSON after %d LLM fix attempts", MAX_RETRY_COUNT), lastException);
	}

	/**
	 * 用于 JSON 解析的函数式接口。
	 *
	 * @param <T> 解析结果类型
	 */
	@FunctionalInterface
	private interface JsonParserFunction<T> {

		T parse(ObjectMapper mapper, String json) throws JsonProcessingException;

	}

	/**
	 * 调用 LLM 修复格式错误的 JSON。
	 * <p>
	 * 修复流程：渲染修复 Prompt -> 调用 LLM -> 收集返回 -> 剥离思考标签 -> 提取 Markdown 代码块中的纯 JSON。
	 * </p>
	 * @param json 待修复的 JSON 字符串
	 * @param errorMessage 解析失败的错误信息
	 * @return 修复后的 JSON 字符串；若 LLM 调用异常则返回原始 JSON
	 */
	private String callLlmToFix(String json, String errorMessage) {
		try {
			String prompt = PromptConstant.getJsonFixPromptTemplate()
				.render(Map.of("json_string", truncateForPrompt(json, 4_000), "error_message", truncateForPrompt(errorMessage, 1_000)));

			Flux<ChatResponse> responseFlux = llmService.callUser(prompt);
			String fixedJson = llmService.toStringFlux(responseFlux)
				.collect(StringBuilder::new, StringBuilder::append)
				.map(StringBuilder::toString)
				.block();

			// 检查fixedJson是否为null
			if (fixedJson == null) {
				log.warn("LLM fix returned null, using original JSON");
				return json;
			}

			log.debug("LLM original return content: {}", fixedJson);

			// 移除think标签
			String cleanedJson = removeThinkTags(fixedJson);
			log.debug("Content after removing think tags: {}", cleanedJson);

			// 提取可能输出在Markdown代码块中的内容
			cleanedJson = MarkdownParserUtil.extractRawText(cleanedJson);
			log.debug("Content after extracting Markdown code blocks: {}", cleanedJson);

			// 确保返回的JSON不为null
			return cleanedJson != null ? cleanedJson : json;
		}
		catch (Exception e) {
			log.error("Exception occurred while calling LLM fix service", e);
			return json;
		}
	}

	/**
	 * 移除 </think> 标签及其之前的所有内容 逻辑：找到最后一个 </think> 结束标签，只保留它之后的部分
	 */
	private String removeThinkTags(String text) {
		if (text == null || text.isEmpty()) {
			return text;
		}

		// 1. 查找最后一个结束标签的位置
		int lastEndTagIndex = text.lastIndexOf(THINK_END_TAG);

		if (lastEndTagIndex != -1) {
			log.debug("Found </think> tag, index position: {}", lastEndTagIndex);

			// 2. 计算截取点：结束标签的位置 + 标签本身的长度
			int contentStartIndex = lastEndTagIndex + THINK_END_TAG.length();

			// 3. 截取该点之后的所有内容
			String finalResult = text.substring(contentStartIndex).trim();

			log.debug("Content after truncating think tags: {}", finalResult);

			return finalResult;
		}

		// 如果没找到结束标签，说明可能没有思考过程，直接返回原文本（去除首尾空格）
		log.debug("Think end tag not found, returning original text");
		return text.trim();
	}


	/** R20: 限制 JSON 修复提示词体积。 */
	private static String truncateForPrompt(String s, int max) {
		if (s == null) {
			return "";
		}
		if (max <= 0 || s.length() <= max) {
			return s;
		}
		return s.substring(0, max) + "…";
	}


}
