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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接池工厂，根据数据库类型管理和获取对应的连接池实现。
 * <p>
 * Spring 启动时自动注入所有 {@link DBConnectionPool} 实现，并按连接池类型注册。
 * </p>
 */
@Component
public class DBConnectionPoolFactory {

	/** 连接池映射表，键为连接池类型 */
	private final Map<String, DBConnectionPool> poolMap = new ConcurrentHashMap<>();

	/**
	 * 构造函数，由 Spring 自动注入所有连接池实现并注册。
	 * @param pools 所有连接池实现列表
	 */
	public DBConnectionPoolFactory(List<DBConnectionPool> pools) {
		pools.forEach(this::register);
	}

	/**
	 * 注册一个数据库连接池。
	 * @param pool 待注册的连接池
	 */
	public void register(DBConnectionPool pool) {
		poolMap.put(pool.getConnectionPoolType(), pool);
	}

	/**
	 * 判断指定类型的连接池是否已注册。
	 * @param type 数据库类型
	 * @return 是否已注册
	 */
	public boolean isRegistered(String type) {
		return poolMap.containsKey(type);
	}

	/**
	 * 根据数据库类型获取对应的连接池。
	 * <p>
	 * 先按类型精确匹配，未找到则遍历所有连接池通过 supportedDataSourceType 匹配。
	 * </p>
	 * @param type 数据库类型
	 * @return 对应的连接池，未找到返回 null
	 */
	public DBConnectionPool getPoolByType(String type) {
		DBConnectionPool direct = poolMap.get(type);
		if (direct != null) {
			return direct;
		}
		return poolMap.values().stream().filter(p -> p.supportedDataSourceType(type)).findFirst().orElse(null);
	}

	/**
	 * 根据数据库类型获取对应的连接池。
	 * <p>
	 * 遍历所有连接池通过 supportedDataSourceType 匹配，未找到则抛出异常。
	 * </p>
	 * @param type 数据库类型
	 * @return 对应的连接池
	 * @throws IllegalStateException 未找到对应类型的连接池
	 */
	// todo: 写一层缓存
	public DBConnectionPool getPoolByDbType(String type) {
		return poolMap.values()
			.stream()
			.filter(p -> p.supportedDataSourceType(type))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("No DB connection pool found for type: " + type));
	}

}
