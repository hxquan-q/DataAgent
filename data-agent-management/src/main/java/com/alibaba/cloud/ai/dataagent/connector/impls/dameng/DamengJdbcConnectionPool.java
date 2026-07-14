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
package com.alibaba.cloud.ai.dataagent.connector.impls.dameng;

import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import org.springframework.stereotype.Service;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;

/**
 * 达梦（Dameng）JDBC 连接池实现。
 * <p>
 * 基于 Druid 连接池，使用达梦 JDBC 驱动（dm.jdbc.driver.DmDriver），支持连接测试（ping）和错误码映射。
 * 达梦驱动不兼容 Druid 的 wall 过滤器，仅启用 stat 过滤器。
 * </p>
 */
@Service("damengJdbcConnectionPool")
public class DamengJdbcConnectionPool extends AbstractDBConnectionPool {

	/** 达梦 JDBC 驱动类名 */
	private static final String DRIVER = "dm.jdbc.driver.DmDriver";

	/**
	 * 获取达梦 JDBC 驱动类名。
	 * @return 驱动类全限定名
	 */
	@Override
	public String getDriver() {
		return DRIVER;
	}

	/**
	 * 将达梦 SQL 异常的 sqlState 映射为对应的错误码枚举。
	 * @param sqlState SQL 异常状态码
	 * @return 对应的错误码枚举
	 */
	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != null && ret != OTHERS) {
			return ret;
		}
		return OTHERS;
	}

	/**
	 * 判断是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否为达梦类型
	 */
	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.DAMENG.getTypeName().equals(type);
	}

	/**
	 * 获取连接池类型标识。
	 * @return 达梦类型名称
	 */
	@Override
	public String getConnectionPoolType() {
		return BizDataSourceTypeEnum.DAMENG.getTypeName();
	}

}
