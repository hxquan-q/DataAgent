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
package com.alibaba.cloud.ai.dataagent.connector.accessor;

import com.alibaba.cloud.ai.dataagent.connector.ddl.AbstractJdbcDdl;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPool;
import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.SqlExecutor;
import com.alibaba.cloud.ai.dataagent.bo.schema.ColumnInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.DatabaseInfoBO;
import com.alibaba.cloud.ai.dataagent.connector.DbQueryParameter;
import com.alibaba.cloud.ai.dataagent.bo.schema.ForeignKeyInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.ResultSetBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.SchemaInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.schema.TableInfoBO;
import com.alibaba.cloud.ai.dataagent.bo.DbConfigBO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.util.List;

/**
 * 访问器抽象实现类。
 * <p>
 * 通过 DDL 执行器和连接池实现数据库元数据查询和 SQL 执行。
 * 利用反射式方法分发（accessDb）将各查询操作委托给对应的 DDL 执行器。
 * </p>
 *
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */
@Slf4j
@AllArgsConstructor
public abstract class AbstractAccessor implements Accessor {

	private final DdlFactory ddlFactory;

	private final DBConnectionPool dbConnectionPool;

	/**
	 * 访问数据库并执行指定的方法。
	 * <p>
	 * 通过方法名分发到对应的 DDL 执行器方法，支持 showDatabases、showSchemas、showTables、
	 * fetchTables、showColumns、showForeignKeys、sampleColumn、scanTable、executeSqlAndReturnObject 等操作。
	 * </p>
	 * @param dbConfig 数据库配置信息
	 * @param method 方法名称
	 * @param param 查询参数
	 * @return 结果对象
	 * @throws Exception 数据库访问异常
	 */
	public <T> T accessDb(DbConfigBO dbConfig, String method, DbQueryParameter param) throws Exception {

		try (Connection connection = getConnection(dbConfig)) {

			AbstractJdbcDdl ddlExecutor = (AbstractJdbcDdl) ddlFactory.getDdlExecutorByDbConfig(dbConfig);

			// 根据方法名分发到对应的 DDL 执行方法
			switch (method) {
				case "showDatabases":
					return (T) ddlExecutor.showDatabases(connection);
				case "showSchemas":
					return (T) ddlExecutor.showSchemas(connection);
				case "showTables":
					return (T) ddlExecutor.showTables(connection, param.getSchema(), param.getTablePattern());
				case "fetchTables":
					return (T) ddlExecutor.fetchTables(connection, param.getSchema(), param.getTables());
				case "showColumns":
					return (T) ddlExecutor.showColumns(connection, param.getSchema(), param.getTable());
				case "showForeignKeys":
					return (T) ddlExecutor.showForeignKeys(connection, param.getSchema(), param.getTables());
				case "sampleColumn":
					return (T) ddlExecutor.sampleColumn(connection, param.getSchema(), param.getTable(),
							param.getColumn());
				case "scanTable":
					return (T) ddlExecutor.scanTable(connection, param.getSchema(), param.getTable());
				case "executeSqlAndReturnObject":
					return (T) SqlExecutor.executeSqlAndReturnObject(connection, param.getSchema(), param.getSql());
				default:
					throw new UnsupportedOperationException("Unknown method: " + method);
			}
		}
		catch (Exception e) {

			log.error("Error accessing database with method: {}, reason: {}", method, e.getMessage());
			throw e;
		}
	}

	/** {@inheritDoc} */
	public List<DatabaseInfoBO> showDatabases(DbConfigBO dbConfig) throws Exception {
		return accessDb(dbConfig, "showDatabases", null);
	}

	/** {@inheritDoc} */
	public List<SchemaInfoBO> showSchemas(DbConfigBO dbConfig) throws Exception {
		return accessDb(dbConfig, "showSchemas", null);
	}

	/** {@inheritDoc} */
	public List<TableInfoBO> showTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showTables", param);
	}

	/** {@inheritDoc} */
	public List<TableInfoBO> fetchTables(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "fetchTables", param);
	}

	/** {@inheritDoc} */
	public List<ColumnInfoBO> showColumns(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showColumns", param);
	}

	/** {@inheritDoc} */
	public List<ForeignKeyInfoBO> showForeignKeys(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "showForeignKeys", param);
	}

	/** {@inheritDoc} */
	public List<String> sampleColumn(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "sampleColumn", param);
	}

	/** {@inheritDoc} */
	public ResultSetBO scanTable(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "scanTable", param);
	}

	/** {@inheritDoc} */
	public ResultSetBO executeSqlAndReturnObject(DbConfigBO dbConfig, DbQueryParameter param) throws Exception {
		return accessDb(dbConfig, "executeSqlAndReturnObject", param);
	}

	/**
	 * 从连接池中获取数据库连接。
	 * @param config 数据库配置信息
	 * @return 数据库连接
	 */
	public Connection getConnection(DbConfigBO config) {
		return this.dbConnectionPool.getConnection(config);
	}

}
