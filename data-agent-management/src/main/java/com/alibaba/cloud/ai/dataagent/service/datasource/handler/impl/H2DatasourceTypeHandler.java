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
 * H2 内存数据库类型处理器，负责构建 H2 JDBC 连接 URL（内存模式、MySQL 兼容模式）。
 */
@Component
public class H2DatasourceTypeHandler implements DatasourceTypeHandler {

	/**
	 * 返回 H2 数据源类型名称。
	 * @return H2 类型名称
	 */
	@Override
	public String typeName() {
		return BizDataSourceTypeEnum.H2.getTypeName();
	}

	/**
	 * 构建 H2 JDBC 连接 URL，使用内存模式并兼容 MySQL 方言。
	 * @param datasource 数据源实体
	 * @return H2 JDBC 连接 URL
	 */
	@Override
	public String buildConnectionUrl(Datasource datasource) {
		if (!hasRequiredConnectionFields(datasource)) {
			return datasource.getConnectionUrl();
		}
		return String.format(
				"jdbc:h2:mem:%s;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=true;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE",
				datasource.getDatabaseName());
	}

}
