/*
 * Copyright 2026 the original author or authors.
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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import com.alibaba.cloud.ai.dataagent.connector.pool.AbstractDBConnectionPool;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATABASE_NOT_EXIST_42000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.DATASOURCE_CONNECTION_FAILURE_08001;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.INSUFFICIENT_PRIVILEGE_42501;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.OTHERS;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.PASSWORD_ERROR_28000;
import static com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum.SUCCESS;

/**
 * Hive JDBC 连接池实现。
 * <p>
 * 基于 Druid 连接池，使用 HiveServer2 JDBC 驱动（org.apache.hive.jdbc.HiveDriver）。 自定义了 Hive
 * 专用的连接池参数（如 SELECT 1 心跳检测、更长的最大等待时间 60 秒等）， 并重写了 ping 方法通过执行 SELECT 1 测试连接可用性。
 * </p>
 */
@Slf4j
@Service("hiveJdbcConnectionPool")
public class HiveJdbcConnectionPool extends AbstractDBConnectionPool {

	/** Hive JDBC 驱动类名 */
	private static final String DRIVER = "org.apache.hive.jdbc.HiveDriver";

	/**
	 * 获取 Hive JDBC 驱动类名。
	 * @return 驱动类全限定名
	 */
	@Override
	public String getDriver() {
		return DRIVER;
	}

	/**
	 * 将 Hive SQL 异常的 sqlState 映射为对应的错误码枚举。
	 * @param sqlState SQL 异常状态码
	 * @return 对应的错误码枚举
	 */
	@Override
	public ErrorCodeEnum errorMapping(String sqlState) {
		if (sqlState == null) {
			return OTHERS;
		}

		ErrorCodeEnum ret = ErrorCodeEnum.fromCode(sqlState);
		if (ret != OTHERS) {
			return ret;
		}

		switch (sqlState) {
			case "08001":
			case "08S01":
				return DATASOURCE_CONNECTION_FAILURE_08001;
			case "28000":
				return PASSWORD_ERROR_28000;
			case "42000":
				return DATABASE_NOT_EXIST_42000;
			case "42501":
				return INSUFFICIENT_PRIVILEGE_42501;
			default:
				return OTHERS;
		}
	}

	/**
	 * 判断是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否为 Hive 类型
	 */
	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.HIVE.getTypeName().equals(type);
	}

	/**
	 * 获取连接池类型标识。
	 * @return Hive 连接池类型名称
	 */
	@Override
	public String getConnectionPoolType() {
		return "Hive_JDBC_Pool";
	}

	/**
	 * 创建 Hive 专用的 Druid 数据源。
	 * <p>
	 * 自定义配置包括：SELECT 1 心跳检测、更长的最大等待时间（60 秒）、空闲连接最小可回收时间（5 分钟）等。
	 * </p>
	 * @param url Hive 连接 URL
	 * @param username 用户名
	 * @param password 密码
	 * @return 创建好的数据源实例
	 * @throws Exception 数据源创建异常
	 */
	@Override
	public DataSource createdDataSource(String url, String username, String password) throws Exception {
		log.info("Creating Hive DataSource with custom configuration");
		String driver = getDriver();
		Map<String, String> props = new HiveDruidProperties(driver, url, username, password, "stat").toMap();
		return DruidDataSourceFactory.createDataSource(props);
	}

	/**
	 * Hive Druid 数据源配置属性封装类。
	 */
	private static final class HiveDruidProperties {

		/** JDBC 驱动类名 */
		private final String driver;

		/** 数据库连接 URL */
		private final String url;

		/** 用户名 */
		private final String username;

		/** 密码 */
		private final String password;

		/** Druid 过滤器配置 */
		private final String filters;

		/**
		 * 构造配置属性。
		 * @param driver 驱动类名
		 * @param url 连接 URL
		 * @param username 用户名
		 * @param password 密码
		 * @param filters 过滤器配置
		 */
		private HiveDruidProperties(String driver, String url, String username, String password, String filters) {
			this.driver = driver;
			this.url = url;
			this.username = username;
			this.password = password;
			this.filters = filters;
		}

		/**
		 * 将配置属性转换为 Druid 属性 Map。
		 * @return Druid 配置属性 Map
		 */
		private Map<String, String> toMap() {
			Map<String, String> props = new HashMap<>();
			props.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, this.driver);
			props.put(DruidDataSourceFactory.PROP_URL, this.url);
			props.put(DruidDataSourceFactory.PROP_USERNAME, this.username);
			props.put(DruidDataSourceFactory.PROP_PASSWORD, this.password);
			props.put(DruidDataSourceFactory.PROP_FILTERS, this.filters);
			props.put(DruidDataSourceFactory.PROP_INITIALSIZE, "5");
			props.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
			props.put(DruidDataSourceFactory.PROP_MAXACTIVE, "20");
			props.put(DruidDataSourceFactory.PROP_MAXWAIT, "60000");
			props.put(DruidDataSourceFactory.PROP_TIMEBETWEENEVICTIONRUNSMILLIS, "60000");
			props.put(DruidDataSourceFactory.PROP_MINEVICTABLEIDLETIMEMILLIS, "300000");
			props.put(DruidDataSourceFactory.PROP_VALIDATIONQUERY, "SELECT 1");
			props.put(DruidDataSourceFactory.PROP_TESTWHILEIDLE, "true");
			props.put(DruidDataSourceFactory.PROP_TESTONBORROW, "false");
			props.put(DruidDataSourceFactory.PROP_TESTONRETURN, "false");
			return props;
		}

	}

	/**
	 * 测试 Hive 数据库连接是否有效。
	 * <p>
	 * 通过从连接池获取连接并执行 SELECT 1 来验证连接可用性。
	 * </p>
	 * @param config 数据库配置信息
	 * @return 连接测试结果错误码
	 */
	@Override
	public ErrorCodeEnum ping(DbConfigBO config) {
		log.info("Hive ping method called, url: {}", config.getUrl());
		try (Connection connection = getConnection(config); Statement stmt = connection.createStatement()) {
			log.info("Hive connection obtained, executing SELECT 1");
			ResultSet rs = stmt.executeQuery("SELECT 1");
			if (rs.next()) {
				rs.close();
				return SUCCESS;
			}
			rs.close();
			return DATASOURCE_CONNECTION_FAILURE_08001;
		}
		catch (SQLException e) {
			log.error("Hive connection test failed, url:{}, state:{}, message:{}", config.getUrl(), e.getSQLState(),
					e.getMessage());
			return errorMapping(e.getSQLState());
		}
		catch (Exception e) {
			log.error("Hive connection test failed with unexpected error, url:{}, message:{}", config.getUrl(),
					e.getMessage());
			return DATASOURCE_CONNECTION_FAILURE_08001;
		}
	}

}
