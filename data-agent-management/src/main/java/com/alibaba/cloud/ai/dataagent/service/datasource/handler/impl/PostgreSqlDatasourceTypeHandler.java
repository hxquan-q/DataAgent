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
package com.alibaba.cloud.ai.dataagent.service.datasource.handler.impl;

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.service.datasource.handler.DatasourceTypeHandler;
import org.springframework.stereotype.Component;

/**
 * PostgreSQL 数据源类型处理器，负责构建 PostgreSQL JDBC 连接 URL， 支持以 "database|schema" 格式指定数据库名和
 * Schema。
 */
@Component
public class PostgreSqlDatasourceTypeHandler implements DatasourceTypeHandler {

	/**
	 * 返回 PostgreSQL 数据源类型名称。
	 * @return PostgreSQL 类型名称
	 */
	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.POSTGRESQL.getTypeName();
	}

	/**
	 * 构建 PostgreSQL JDBC 连接 URL。
	 * @param datasource 数据源实体
	 * @return PostgreSQL JDBC 连接 URL
	 */
	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		// 提取数据库名（格式为 "database|schema"，只取 database 部分）
		String databaseName = datasource.getDatabaseName();
		if (databaseName != null && databaseName.contains("|")) {
			databaseName = databaseName.split("\\|")[0];
		}
		return String.format(
				"jdbc:postgresql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai",
				datasource.getHost(), datasource.getPort(), databaseName);
	}

	/**
	 * 提取 PostgreSQL 的 Schema 名称。
	 * @param datasource 数据源实体
	 * @return Schema 名称（格式为 "database|schema" 时取 schema 部分）
	 */
	@Override
	public String extractSchemaName(Datasource datasource) {
		// 提取 schema 名（格式为 "database|schema"，取 schema 部分）
		String databaseName = datasource.getDatabaseName();
		if (databaseName != null && databaseName.contains("|")) {
			String[] parts = databaseName.split("\\|");
			return parts.length > 1 ? parts[1] : parts[0];
		}
		return databaseName;
	}

}
