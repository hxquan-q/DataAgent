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

import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;

/**
 * DDL 执行器接口，定义数据库元数据查询的通用契约。
 * <p>
 * 各数据库类型（MySQL、PostgreSQL、Oracle、H2、Hive、达梦、SQL Server 等）提供各自的实现。
 * </p>
 */
public interface Ddl {

	/**
	 * 获取当前 DDL 执行器对应的数据源类型枚举。
	 * @return 数据源类型枚举
	 */
	BizDataSourceTypeEnum getDataSourceType();

	/**
	 * 判断当前 DDL 执行器是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否支持该数据源类型
	 */
	default boolean supportedDataSourceType(String type) {
		return getDataSourceType().getTypeName().equals(type);
	}

	/**
	 * 判断当前 DDL 执行器是否支持指定的数据源类型枚举。
	 * @param type 数据源类型枚举
	 * @return 是否支持该数据源类型
	 */
	default boolean supportedDataSourceType(BizDataSourceTypeEnum type) {
		return getDataSourceType().equals(type);
	}

	/**
	 * 获取 DDL 执行器类型标识（协议@方言格式）。
	 * @return DDL 类型标识字符串
	 */
	default String getDdlType() {
		return getDataSourceType().getProtocol() + "@" + getDataSourceType().getDialect();
	}

}
