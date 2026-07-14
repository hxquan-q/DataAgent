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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.registry;

import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据源类型处理器注册中心，在启动时自动收集所有 {@link DatasourceTypeHandler} 实现，
 * 并以类型名称为键建立映射，提供按类型查找处理器的统一入口。
 */
@Component
public class DatasourceTypeHandlerRegistry {

	/** 以规范化类型名称为键的处理器映射 */
	private final Map<String, DatasourceTypeHandler> handlerMap = new ConcurrentHashMap<>();

	/**
	 * 构造方法，通过 Spring 自动注入所有处理器实现并完成注册。
	 * @param handlers 所有数据源类型处理器实现列表
	 */
	public DatasourceTypeHandlerRegistry(List<DatasourceTypeHandler> handlers) {
		handlers.forEach(this::register);
	}

	/**
	 * 注册数据源类型处理器。
	 * @param handler 待注册的处理器
	 */
	public void register(DatasourceTypeHandler handler) {
		handlerMap.put(normalizeType(handler.typeName()), handler);
	}

	/**
	 * 判断指定类型是否已注册。
	 * @param type 数据源类型名称
	 * @return 是否已注册
	 */
	public boolean isRegistered(String type) {
		return handlerMap.containsKey(normalizeType(type));
	}

	/**
	 * 根据类型获取处理器，不存在时抛出异常。
	 * @param type 数据源类型名称
	 * @return 对应的数据源类型处理器
	 * @throws IllegalArgumentException 当类型为空白时抛出
	 * @throws IllegalStateException 当类型不受支持时抛出
	 */
	public DatasourceTypeHandler getRequired(String type) {
		if (!StringUtils.hasText(type)) {
			throw new IllegalArgumentException("Datasource type cannot be blank");
		}
		DatasourceTypeHandler handler = handlerMap.get(normalizeType(type));
		if (handler == null) {
			throw new IllegalStateException("Unsupported datasource type: " + type);
		}
		return handler;
	}

	/**
	 * 将类型名称规范化为小写并去除首尾空格。
	 * @param type 原始类型名称
	 * @return 规范化后的类型名称
	 */
	private String normalizeType(String type) {
		if (!StringUtils.hasText(type)) {
			return "";
		}
		return type.trim().toLowerCase(Locale.ROOT);
	}

}
