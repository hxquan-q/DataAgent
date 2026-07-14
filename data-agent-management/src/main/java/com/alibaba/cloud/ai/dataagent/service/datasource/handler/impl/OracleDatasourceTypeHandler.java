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
 * Oracle 数据源类型处理器，负责构建 Oracle JDBC 连接 URL，
 * 支持以 "serviceName|schemaName" 格式指定服务名和 Schema。
 */
@Component
public class OracleDatasourceTypeHandler implements DatasourceTypeHandler {

	/**
	 * 返回 Oracle 数据源类型名称。
	 * @return Oracle 类型名称
	 */
	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.ORACLE.getTypeName();
	}

	/**
	 * 构建 Oracle JDBC 连接 URL，格式为 jdbc:oracle:thin:@host:port/serviceName。
	 * @param datasource 数据源实体
	 * @return Oracle JDBC 连接 URL
	 */
	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		// Oracle JDBC URL 格式：jdbc:oracle:thin:@host:port/serviceName
		return String.format("jdbc:oracle:thin:@%s:%d/%s", datasource.getHost(), datasource.getPort(),
				datasource.getDatabaseName());
	}

	/**
	 * Oracle 不需要额外参数用于基本连接测试，直接返回原始 URL。
	 * @param datasource 数据源实体
	 * @param url 待规范化的 URL
	 * @return 原始 URL
	 */
	@Override
	public String normalizeTestUrl(Datasource datasource, String url) {
		return url;
	}

	/**
	 * 提取 Oracle 的 Schema 名称。
	 * <p>
	 * Oracle 的 Schema 存储在 databaseName 中，格式为 "serviceName|schemaName"，
	 * 取分隔符后的 schema 部分；未指定时返回 null，由方言层使用用户名作为 Schema。
	 * </p>
	 * @param datasource 数据源实体
	 * @return Schema 名称，未指定时返回 null
	 */
	@Override
	public String extractSchemaName(Datasource datasource) {
		// Oracle 的 Schema 存储在 databaseName 中，格式为 "serviceName|schemaName"
		// 提取分隔符 | 之后的 schema 部分
		String databaseName = datasource.getDatabaseName();
		if (databaseName != null && databaseName.contains("|")) {
			String[] parts = databaseName.split("\\|");
			if (parts.length == 2) {
				return parts[1]; // 返回 schema 名称
			}
		}
		// 未指定 schema 时返回 null，让 OracleJdbcDdl.getSchema() 使用用户名
		return null;
	}

}
