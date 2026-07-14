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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler;

import com.alibaba.cloud.ai.dataagent.enums.DbAccessTypeEnum;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import org.springframework.util.StringUtils;

/**
 * 数据源类型处理器接口，为不同类型的数据库（MySQL、PostgreSQL、Oracle 等）提供
 * 连接 URL 构建、连接配置转换、Schema 提取等差异化处理能力。
 *
 * <p>
 * 每种数据库类型实现该接口，通过 {@link #typeName()} 标识自身类型，
 * 并可覆写默认方法以适配特定数据库的连接方式。
 * </p>
 */
public interface DatasourceTypeHandler {

	/**
	 * 获取该处理器支持的数据源类型名称。
	 * @return 数据源类型名称
	 */
	String typeName();

	/**
	 * 获取数据库连接方式，默认为 JDBC。
	 * @return 连接方式编码
	 */
	default String connectionType() {
		return DbAccessTypeEnum.JDBC.getCode();
	}

	/**
	 * 获取数据库方言类型，默认与类型名称相同。
	 * @return 方言类型名称
	 */
	default String dialectType() {
		return typeName();
	}

	/**
	 * 判断该处理器是否支持指定的数据源类型。
	 * @param type 数据源类型名称（不区分大小写）
	 * @return 是否支持该类型
	 */
	default boolean supports(String type) {
		return typeName().equalsIgnoreCase(type);
	}

	/**
	 * 检查数据源是否包含必要的连接字段（主机、端口、数据库名）。
	 * @param datasource 数据源实体
	 * @return 是否包含必要的连接字段
	 */
	default boolean hasRequiredConnectionFields(Datasource datasource) {
		return datasource.getHost() != null && datasource.getPort() != null && datasource.getDatabaseName() != null;
	}

	/**
	 * 构建数据库连接 URL，默认返回数据源中已存储的连接 URL。
	 * @param datasource 数据源实体
	 * @return 连接 URL
	 */
	default String buildConnectionUrl(Datasource datasource) {
		return datasource.getConnectionUrl();
	}

	/**
	 * 解析连接 URL，优先使用已存在的 URL，不存在时通过构建方法生成。
	 * @param datasource 数据源实体
	 * @return 解析后的连接 URL
	 */
	default String resolveConnectionUrl(Datasource datasource) {
		String existing = datasource.getConnectionUrl();
		if (StringUtils.hasText(existing)) {
			return existing;
		}
		return buildConnectionUrl(datasource);
	}

	/**
	 * 提取数据源对应的 Schema 名称，默认返回数据库名。
	 * @param datasource 数据源实体
	 * @return Schema 名称
	 */
	default String extractSchemaName(Datasource datasource) {
		return datasource.getDatabaseName();
	}

	/**
	 * 将数据源实体转换为数据库配置对象。
	 * @param datasource 数据源实体
	 * @return 数据库配置对象
	 */
	default DbConfigBO toDbConfig(Datasource datasource) {
		DbConfigBO config = new DbConfigBO();
		config.setUrl(resolveConnectionUrl(datasource));
		config.setUsername(datasource.getUsername());
		config.setPassword(datasource.getPassword());
		config.setConnectionType(connectionType());
		config.setDialectType(dialectType());
		config.setSchema(extractSchemaName(datasource));
		return config;
	}

	/**
	 * 对连接测试用的 URL 进行规范化处理，默认直接返回原始 URL。
	 * @param datasource 数据源实体
	 * @param url 待规范化的 URL
	 * @return 规范化后的 URL
	 */
	default String normalizeTestUrl(Datasource datasource, String url) {
		return url;
	}

}
