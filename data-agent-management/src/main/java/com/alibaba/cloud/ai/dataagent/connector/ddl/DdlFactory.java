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
package com.alibaba.cloud.ai.dataagent.connector.ddl;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DDL 执行器工厂，根据数据库配置和类型管理获取对应的 DDL 执行器实现。
 * <p>
 * Spring 启动时自动注入所有 {@link Ddl} 实现并按 DDL 类型注册。
 * </p>
 */
@Component
public class DdlFactory {

	/** DDL 执行器映射表，键为 DDL 类型（协议@方言格式） */
	private final Map<String, Ddl> ddlExecutorSet = new ConcurrentHashMap<>();

	/**
	 * 构造函数，由 Spring 自动注入所有 DDL 执行器实现并注册。
	 * @param ddls 所有 DDL 执行器实现列表
	 */
	public DdlFactory(List<Ddl> ddls) {
		ddls.forEach(this::registry);
	}

	/**
	 * 注册一个 DDL 执行器。
	 * @param ddlExecutor 待注册的 DDL 执行器
	 */
	public void registry(Ddl ddlExecutor) {
		ddlExecutorSet.put(ddlExecutor.getDdlType(), ddlExecutor);
	}

	/**
	 * 判断指定类型的 DDL 执行器是否已注册。
	 * @param type DDL 类型
	 * @return 是否已注册
	 */
	public boolean isRegistered(String type) {
		return ddlExecutorSet.containsKey(type);
	}

	/**
	 * 根据数据库配置获取对应的 DDL 执行器。
	 * @param dbConfig 数据库配置信息
	 * @return 对应的 DDL 执行器
	 * @throws RuntimeException 未知数据库类型
	 */
	public Ddl getDdlExecutorByDbConfig(DbConfigBO dbConfig) {
		BizDataSourceTypeEnum type = BizDataSourceTypeEnum.fromTypeName(dbConfig.getDialectType());
		if (type == null) {
			throw new RuntimeException("unknown db type");
		}
		return getDdlExecutorByDbType(type);
	}

	/**
	 * 根据数据源类型枚举获取对应的 DDL 执行器。
	 * @param type 数据源类型枚举
	 * @return 对应的 DDL 执行器
	 * @throws IllegalStateException 未找到匹配的 DDL 执行器
	 */
	// todo: 写一层缓存
	public Ddl getDdlExecutorByDbType(BizDataSourceTypeEnum type) {
		return ddlExecutorSet.values()
			.stream()
			.filter(d -> d.supportedDataSourceType(type))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no ddl executor found for " + type));
	}

	/**
	 * 根据类型名称获取对应的 DDL 执行器。
	 * @param type DDL 类型名称
	 * @return 对应的 DDL 执行器，未找到返回 null
	 */
	public Ddl getDdlExecutorByType(String type) {
		return ddlExecutorSet.get(type);
	}

}
