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

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;

import java.sql.Connection;

/**
 * 数据库连接池接口，用于维护 DataAgent 所需的数据源连接信息。
 * <p>
 * 各数据库类型（MySQL、PostgreSQL、Oracle、H2、Hive、达梦、SQL Server 等）提供各自的实现。
 * </p>
 */

public interface DBConnectionPool extends AutoCloseable {

	/**
	 * 测试数据库连接是否有效。
	 * @param config 数据库配置信息
	 * @return 错误码枚举，表示连接测试结果
	 */
	ErrorCodeEnum ping(DbConfigBO config);

	/**
	 * 从连接池中获取数据库连接。
	 * @param config 数据库配置信息
	 * @return 数据库连接对象
	 */
	Connection getConnection(DbConfigBO config);

	/**
	 * 判断当前连接池是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否支持该数据源类型
	 */
	boolean supportedDataSourceType(String type);

	/**
	 * 获取当前连接池的类型标识。
	 * @return 连接池类型名称
	 */
	String getConnectionPoolType();

}
