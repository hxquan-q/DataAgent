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
package com.alibaba.cloud.ai.dataagent.connector.impls.sqlserver;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.*;

/**
 * SQL Server JDBC 连接池实现。
 * <p>
 * 基于 Druid 连接池，使用 Microsoft SQL Server JDBC 驱动（com.microsoft.sqlserver.jdbc.SQLServerDriver），
 * 支持连接测试（ping）和错误码映射。
 * </p>
 *
 * @author zihen
 * @date 2025/12/14 17:34
 */
@Service("sqlServerJdbcConnectionPool")
public class SqlServerJdbcConnectionPool extends AbstractDBConnectionPool {

	/**
	 * 获取 SQL Server JDBC 驱动类名。
	 * @return 驱动类全限定名
	 */
	@Override
	public String getDriver() {
		return "com.microsoft.sqlserver.jdbc.SQLServerDriver";
	}

	/**
	 * 将 SQL Server SQL 异常的 sqlState 映射为对应的错误码枚举。
	 * @param sqlState SQL 异常状态码
	 * @return 对应的错误码枚举
	 */
	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != null) {
			return ret;
		}
		return switch (sqlState) {
			case "08S01" -> DATASOURCE_CONNECTION_FAILURE_08S01;
			case "28000" -> PASSWORD_ERROR_28000;
			case "S0001" -> DATABASE_NOT_EXIST_42000;
			case "42000" -> DATABASE_NOT_EXIST_42000;
			default -> OTHERS;
		};
	}

	/**
	 * 判断是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否为 SQL Server 类型
	 */
	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.SQL_SERVER.getTypeName().equalsIgnoreCase(type);
	}

	/**
	 * 获取连接池类型标识。
	 * @return SQL Server 类型名称
	 */
	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.SQL_SERVER.getTypeName();
	}

}
