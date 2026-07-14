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
import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.DruidDataSourceFactory;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import com.alibaba.cloud.ai.dataagent.enums.ErrorCodeEnum;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接池抽象实现类。
 * <p>
 * 基于 Druid 连接池实现，提供数据源缓存、连接获取重试、连接测试（ping）等通用功能。
 * 子类需要实现 {@link #getDriver()} 和 {@link #errorMapping(String)} 方法以适配不同数据库类型。
 * </p>
 */
@Slf4j
public abstract class AbstractDBConnectionPool implements DBConnectionPool {

	/**
	 * 数据源缓存，确保每套连接配置只创建一次 DataSource，避免重复创建。
	 */
	private static final ConcurrentHashMap<String, DataSource> DATA_SOURCE_CACHE = new ConcurrentHashMap<>();

	/** 连接获取重试策略 */
	private final ConnectionRetryPolicy retryPolicy;

	/**
	 * 使用默认重试策略构造连接池。
	 */
	protected AbstractDBConnectionPool() {
		this(ConnectionRetryPolicy.defaults());
	}

	/**
	 * 使用指定重试策略构造连接池。
	 * @param retryPolicy 重试策略
	 */
	protected AbstractDBConnectionPool(ConnectionRetryPolicy retryPolicy) {
		this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
	}

	/**
	 * 获取当前数据库类型的 JDBC 驱动类名。
	 * @return JDBC 驱动类全限定名
	 */
	public abstract String getDriver();

	/**
	 * 将 SQL 异常的 sqlState 映射为对应的错误码枚举。
	 * @param sqlState SQL 异常的 sqlState 状态码
	 * @return 对应的错误码枚举
	 */
	public abstract ErrorCodeEnum errorMapping(String sqlState);

	/**
	 * 获取查询指定 schema 是否存在的 SQL 语句。
	 * @param schema schema 名称
	 * @return 查询 SQL 语句
	 */
	protected String getSelectSchemaSQL(String schema) {
		return String.format("SELECT count(*) FROM information_schema.schemata WHERE schema_name = '%s'", schema);
	}

	/**
	 * 测试数据库连接是否有效。
	 * <p>
	 * 对于非 H2 数据库要求密码非空；对 PostgreSQL 方言的数据库额外校验 schema 是否存在。
	 * </p>
	 * @param config 数据库配置信息
	 * @return 连接测试结果错误码
	 */
	public ErrorCodeEnum ping(DbConfigBO config) {
		String jdbcUrl = config.getUrl();
		// H2 内嵌数据库允许空密码，其他数据库类型必须配置密码
		boolean isH2 = "h2".equalsIgnoreCase(config.getConnectionType());
		if (!isH2 && (config.getPassword() == null || config.getPassword().isEmpty())) {
			log.error("test db connection skipped: password is empty, url:{}", jdbcUrl);
			return ErrorCodeEnum.PASSWORD_EMPTY;
		}
		try (Connection connection = DriverManager.getConnection(jdbcUrl, config.getUsername(), config.getPassword());
				Statement stmt = connection.createStatement();) {
			// PostgreSQL 方言数据库需要额外校验 schema 是否存在
			if (BizDataSourceTypeEnum.isPgDialect(config.getConnectionType())) {
				ResultSet rs = stmt.executeQuery(getSelectSchemaSQL(config.getSchema()));
				if (rs.next()) {
					int count = rs.getInt(1);
					rs.close();
					if (count == 0) {
						log.info("the specified schema '{}' does not exist.", config.getSchema());
						return ErrorCodeEnum.SCHEMA_NOT_EXIST_3D070;
					}
				}
				rs.close();
			}
			return ErrorCodeEnum.SUCCESS;
		}
		catch (SQLException e) {
			log.error("test db connection error, url:{}, state:{}, message:{}", jdbcUrl, e.getSQLState(),
					e.getMessage());
			return errorMapping(e.getSQLState());
		}
	}

	/**
	 * 从连接池中获取数据库连接。
	 * <p>
	 * 基于连接参数生成缓存键，确保同一配置只创建一个 DataSource。
	 * 获取失败时按照重试策略进行退避重试。
	 * </p>
	 * @param config 数据库配置信息
	 * @return 数据库连接对象
	 */
	public Connection getConnection(DbConfigBO config) {

		String jdbcUrl = config.getUrl();
		int maxAttempts = retryPolicy.maxAttempts();

		for (int attempt = 1; attempt <= maxAttempts; attempt++) {
			try {
				// 基于连接参数生成缓存键
				String cacheKey = generateCacheKey(jdbcUrl, config.getUsername(), config.getPassword());

				// 使用 computeIfAbsent 保证线程安全，避免重复创建 DataSource
				DataSource dataSource = DATA_SOURCE_CACHE.computeIfAbsent(cacheKey, key -> {
					try {
						log.debug("Creating new DataSource for key: {}", key);
						return createdDataSource(jdbcUrl, config.getUsername(), config.getPassword());
					}
					catch (Exception e) {
						log.error("Failed to create DataSource for key: {}", key, e);
						throw new RuntimeException("Failed to create DataSource", e);
					}
				});

				// 记录连接池状态
				if (dataSource instanceof DruidDataSource druidDataSource) {
					log.debug("Connection pool status - Active: {}, Idle: {}, Total: {}, WaitCount: {}",
							druidDataSource.getActiveCount(), druidDataSource.getPoolingCount(),
							druidDataSource.getActiveCount() + druidDataSource.getPoolingCount(),
							druidDataSource.getWaitThreadCount());
				}

				return dataSource.getConnection();
			}
			catch (Exception e) {
				log.warn("Attempt {} to get database connection failed: {}", attempt, e.getMessage());

				if (attempt == maxAttempts) {
					log.error("Failed to get database connection after {} attempts, URL: {}", maxAttempts, jdbcUrl, e);
					throw new RuntimeException("Failed to get database connection after " + maxAttempts + " attempts",
							e);
				}

				// 失败后按递增退避策略等待重试
				try {
					retryPolicy.pauseAfterFailure(attempt);
				}
				catch (InterruptedException interruptedException) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Interrupted while retrying database connection",
							interruptedException);
				}
			}
		}
		return null;
	}

	/**
	 * 根据连接参数生成缓存键。
	 * @param url 数据库连接 URL
	 * @param username 数据库用户名
	 * @param password 数据库密码
	 * @return 缓存键字符串
	 */
	private String generateCacheKey(String url, String username, String password) {
		return url + "|" + username + "|" + Objects.hashCode(password);
	}

	/**
	 * 关闭并清空所有缓存的数据源。
	 */
	@Override
	public void close() {
		DATA_SOURCE_CACHE.values().forEach(dataSource -> {
			if (dataSource instanceof DruidDataSource) {
				((DruidDataSource) dataSource).close();
			}
		});
		DATA_SOURCE_CACHE.clear();
		log.info("DataSource cache cleared");
	}

	/**
	 * 创建 Druid 数据源实例。
	 * <p>
	 * 配置初始连接数 5、最小空闲 5、最大活跃 20、最大等待 10 秒。
	 * 对于达梦数据库禁用 wall 过滤器（仅保留 stat）。
	 * </p>
	 * @param url 数据库连接 URL
	 * @param username 数据库用户名
	 * @param password 数据库密码
	 * @return 创建好的数据源实例
	 * @throws Exception 数据源创建异常
	 */
	public DataSource createdDataSource(String url, String username, String password) throws Exception {

		String driver = getDriver();

		// 默认启用 wall 和 stat 过滤器；达梦数据库驱动不兼容 wall 过滤器
		String filters = "wall,stat";
		if (driver != null && driver.toLowerCase().contains("dm.jdbc.driver.dmdriver")) {
			filters = "stat";
		}

		java.util.Map<String, String> props = new java.util.HashMap<>();
		props.put(DruidDataSourceFactory.PROP_DRIVERCLASSNAME, driver);
		props.put(DruidDataSourceFactory.PROP_URL, url);
		props.put(DruidDataSourceFactory.PROP_USERNAME, username);
		props.put(DruidDataSourceFactory.PROP_PASSWORD, password);
		props.put(DruidDataSourceFactory.PROP_INITIALSIZE, "5");
		props.put(DruidDataSourceFactory.PROP_MINIDLE, "5");
		props.put(DruidDataSourceFactory.PROP_MAXACTIVE, "20");
		props.put(DruidDataSourceFactory.PROP_MAXWAIT, "10000");
		props.put(DruidDataSourceFactory.PROP_TIMEBETWEENEVICTIONRUNSMILLIS, "60000");
		props.put(DruidDataSourceFactory.PROP_FILTERS, filters);

		DruidDataSource dataSource = (DruidDataSource) DruidDataSourceFactory.createDataSource(props);
		dataSource.setBreakAfterAcquireFailure(Boolean.TRUE);
		dataSource.setConnectionErrorRetryAttempts(2);

		// 记录数据源创建信息
		log.info(
				"Created new DataSource with optimized parameters - InitialSize: 5, MinIdle: 5, MaxActive: 20, MaxWait: 10000ms");

		return dataSource;
	}

}
