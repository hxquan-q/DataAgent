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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 访问器工厂，根据数据库配置和类型管理获取对应的数据访问器实现。
 * <p>
 * Spring 启动时自动注入所有 {@link Accessor} 实现并注册。
 * </p>
 *
 * @author vlsmb
 * @since 2025/9/27
 */
@Component
public class AccessorFactory {

	/** 访问器映射表，键为访问器类型 */
	private final Map<String, Accessor> accessorMap = new ConcurrentHashMap<>();

	/**
	 * 构造函数，由 Spring 自动注入所有访问器实现并注册。
	 * @param accessors 所有访问器实现列表
	 */
	public AccessorFactory(List<Accessor> accessors) {
		accessors.forEach(this::register);
	}

	/**
	 * 注册一个数据访问器。
	 * @param accessor 待注册的访问器
	 */
	public void register(Accessor accessor) {
		accessorMap.put(accessor.getAccessorType(), accessor);
	}

	/**
	 * 判断指定类型的访问器是否已注册。
	 * @param type 数据源类型
	 * @return 是否已注册
	 */
	public boolean isRegistered(String type) {
		return accessorMap.containsKey(type);
	}

	/**
	 * 根据数据库配置获取对应的访问器。
	 * @param dbConfig 数据库配置信息
	 * @return 对应的访问器
	 * @throws IllegalArgumentException 数据库配置为空
	 * @throws IllegalStateException 未找到匹配的方言访问器
	 */
	public Accessor getAccessorByDbConfig(DbConfigBO dbConfig) {
		if (dbConfig == null) {
			throw new IllegalArgumentException("dbConfig cannot be null");
		}
		BizDataSourceTypeEnum typeEnum = Arrays.stream(BizDataSourceTypeEnum.values())
			.filter(e -> e.getDialect().equalsIgnoreCase(dbConfig.getDialectType()))
			.filter(e -> e.getProtocol().equalsIgnoreCase(dbConfig.getConnectionType()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException(
					"no accessor registered for dialect: " + dbConfig.getDialectType()));
		return getAccessorByDbTypeEnum(typeEnum);
	}

	/**
	 * 根据数据源类型枚举获取对应的访问器。
	 * @param typeEnum 数据源类型枚举
	 * @return 对应的访问器
	 * @throws IllegalStateException 未找到匹配的访问器
	 */
	// todo: 写一层缓存
	public Accessor getAccessorByDbTypeEnum(BizDataSourceTypeEnum typeEnum) {
		return accessorMap.values()
			.stream()
			.filter(a -> a.supportedDataSourceType(typeEnum.getTypeName()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no accessor registered for dialect: " + typeEnum));
	}

	/**
	 * 根据类型名称获取对应的访问器。
	 * @param type 访问器类型名称
	 * @return 对应的访问器，未找到返回 null
	 */
	public Accessor getAccessorByType(String type) {
		return accessorMap.get(type);
	}

}
