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
 * Hive 数据源类型处理器，负责构建 Hive JDBC 连接 URL。
 */
@Component
public class HiveDatasourceTypeHandler implements DatasourceTypeHandler {

	/**
	 * 返回 Hive 数据源类型名称。
	 * @return Hive 类型名称
	 */
	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.HIVE.getTypeName();
	}

	/**
	 * 构建 Hive JDBC 连接 URL，格式为 jdbc:hive2://host:port/database。
	 * @param datasource 数据源实体
	 * @return Hive JDBC 连接 URL
	 */
	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}

		return String.format("jdbc:hive2://%s:%d/%s", datasource.getHost(), datasource.getPort(),
				datasource.getDatabaseName());
	}

	/**
	 * Hive 连接测试 URL 不需要额外处理，直接返回原始 URL。
	 * @param datasource 数据源实体
	 * @param url 待规范化的 URL
	 * @return 原始 URL
	 */
	@Override
	public String normalizeTestUrl(Datasource datasource, String url) {
		return url;
	}

}
