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

import com.alibaba.cloud.ai.dataagent.dto.prompt.QueryEnhanceOutputDTO;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.document.Document;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.QUERY_ENHANCE_NODE_OUTPUT;

/**
 * 全局状态（OverAllState）类型安全访问工具类。
 * <p>
 * 提供按类型从全局状态中获取字符串、列表、对象等值的统一入口，并封装 HashMap 到目标类型的反序列化逻辑， 避免在各节点重复编写类型转换样板代码。
 * </p>
 *
 * @author zhangshenghang
 */
public class StateUtil {

	/** 用于将 HashMap 等中间结构转换为目标类型的 ObjectMapper */
	private static final ObjectMapper OBJECT_MAPPER = JsonUtil.getObjectMapper();

	/**
	 * 安全获取字符串类型的状态值。
	 * @param state 全局状态
	 * @param key 状态键
	 * @return 字符串值
	 * @throws IllegalStateException 当指定键不存在时抛出
	 */
	public static String getStringValue(OverAllState state, String key) {
		return state.value(key)
			.map(String.class::cast)
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * 安全获取字符串类型的状态值，键不存在时返回默认值。
	 * @param state 全局状态
	 * @param key 状态键
	 * @param defaultValue 默认值
	 * @return 字符串值，键不存在时返回默认值
	 */
	public static String getStringValue(OverAllState state, String key, String defaultValue) {
		return state.value(key).map(String.class::cast).orElse(defaultValue);
	}

	/**
	 * 安全获取列表类型的状态值。
	 * @param <T> 列表元素类型
	 * @param state 全局状态
	 * @param key 状态键
	 * @return 列表值
	 * @throws IllegalStateException 当指定键不存在时抛出
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> getListValue(OverAllState state, String key) {
		return state.value(key)
			.map(v -> (List<T>) v)
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * 安全获取对象类型的状态值。
	 * <p>
	 * 当存储值为 HashMap 时，会通过 JSON 转换为目标类型。
	 * </p>
	 * @param <T> 目标类型
	 * @param state 全局状态
	 * @param key 状态键
	 * @param type 目标类型 Class 对象
	 * @return 目标类型对象
	 * @throws IllegalStateException 当指定键不存在时抛出
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type) {
		return state.value(key)
			.map(value -> deserializeIfNeeded(value, type))
			.orElseThrow(() -> new IllegalStateException("State key not found: " + key));
	}

	/**
	 * 安全获取对象类型的状态值，键不存在时返回默认值。
	 * @param <T> 目标类型
	 * @param state 全局状态
	 * @param key 状态键
	 * @param type 目标类型 Class 对象
	 * @param defaultValue 默认值
	 * @return 目标类型对象，键不存在时返回默认值
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, T defaultValue) {
		return state.value(key).map(value -> deserializeIfNeeded(value, type)).orElse(defaultValue);
	}

	/**
	 * 在需要时将值反序列化为目标类型。
	 * <p>
	 * 若值已是目标类型则直接返回；若是 HashMap 且目标类型非 HashMap，则通过 JSON 进行转换。
	 * </p>
	 * @param <T> 目标类型
	 * @param value 原始值
	 * @param type 目标类型 Class 对象
	 * @return 转换后的目标类型对象
	 */
	private static <T> T deserializeIfNeeded(Object value, Class<T> type) {
		// 类型已匹配，直接返回
		if (type.isInstance(value)) {
			return type.cast(value);
		}

		// 若为 HashMap 但需要的是复杂对象，使用 JSON 转换
		if (value instanceof HashMap && !type.equals(HashMap.class)) {
			return OBJECT_MAPPER.convertValue(value, type);
		}

		return type.cast(value);
	}

	/**
	 * 安全获取对象类型的状态值，键不存在时由供应者提供默认值。
	 * @param <T> 目标类型
	 * @param state 全局状态
	 * @param key 状态键
	 * @param type 目标类型 Class 对象
	 * @param defaultSupplier 默认值供应者
	 * @return 目标类型对象，键不存在时返回供应者提供的值
	 */
	public static <T> T getObjectValue(OverAllState state, String key, Class<T> type, Supplier<T> defaultSupplier) {
		return state.value(key).map(type::cast).orElseGet(defaultSupplier);
	}

	/**
	 * 判断指定键的状态值是否存在。
	 * <p>
	 * 对于字符串类型，空字符串同样视为不存在。
	 * </p>
	 * @param state 全局状态
	 * @param key 状态键
	 * @return 存在且非空返回 true，否则返回 false
	 */
	public static boolean hasValue(OverAllState state, String key) {
		Optional<Object> value = state.value(key);
		if (value.isPresent()) {
			// 字符串类型额外判断是否为空串
			if (value.get() instanceof String content) {
				return StringUtils.isNotEmpty(content);
			}
			return true;
		}
		return false;
	}

	/**
	 * 获取 {@link Document} 列表类型的状态值。
	 * @param state 全局状态
	 * @param key 状态键
	 * @return Document 列表
	 */
	public static List<Document> getDocumentList(OverAllState state, String key) {
		return getListValue(state, key);
	}

	/**
	 * 从查询增强节点输出中获取规范化查询（canonical query）。
	 * @param state 全局状态
	 * @return 规范化查询字符串
	 */
	public static String getCanonicalQuery(OverAllState state) {
		QueryEnhanceOutputDTO queryEnhanceOutputDTO = getObjectValue(state, QUERY_ENHANCE_NODE_OUTPUT,
				QueryEnhanceOutputDTO.class);
		// 获取 canonical_query 字段
		return queryEnhanceOutputDTO.getCanonicalQuery();
	}

}
