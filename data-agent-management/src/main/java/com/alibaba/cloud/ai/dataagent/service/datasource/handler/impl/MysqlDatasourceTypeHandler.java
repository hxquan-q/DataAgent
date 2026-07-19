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

import java.util.Locale;

/**
 * MySQL 数据源类型处理器，负责构建 MySQL JDBC 连接 URL 并规范化连接测试 URL。
 */
@Component
public class MysqlDatasourceTypeHandler implements DatasourceTypeHandler {

	/**
	 * 返回 MySQL 数据源类型名称。
	 * @return MySQL 类型名称
	 */
	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.MYSQL.getTypeName();
	}

	/**
	 * 构建 MySQL JDBC 连接 URL，包含字符编码、时区等参数。
	 * @param datasource 数据源实体
	 * @return MySQL JDBC 连接 URL
	 */
	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		return String.format(
				"jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf-8&zeroDateTimeBehavior=convertToNull&transformedBitIsBoolean=true&allowMultiQueries=true&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Shanghai",
				datasource.getHost(), datasource.getPort(), datasource.getDatabaseName());
	}

	/**
	 * 规范化连接测试 URL，确保包含时区和 SSL 参数。
	 * @param datasource 数据源实体
	 * @param url 待规范化的 URL
	 * @return 规范化后的 URL
	 */
	@Override
	public String normalizeTestUrl(Datasource datasource, String url) {
		String updated = url;
		String lowerUrl = updated.toLowerCase(Locale.ROOT);
		// 确保包含时区参数
		if (!lowerUrl.contains("servertimezone=")) {
			updated = appendParam(updated, "serverTimezone", "Asia/Shanghai");
			lowerUrl = updated.toLowerCase(Locale.ROOT);
		}
		// 确保包含 SSL 参数
		if (!lowerUrl.contains("usessl=")) {
			updated = appendParam(updated, "useSSL", "false");
		}
		return updated;
	}

	/**
	 * 向 URL 追加查询参数。
	 * @param url 原始 URL
	 * @param key 参数名
	 * @param value 参数值
	 * @return 追加参数后的 URL
	 */
	private String appendParam(String url, String key, String value) {
		return url + (url.contains("?") ? "&" : "?") + key + "=" + value;
	}

}
