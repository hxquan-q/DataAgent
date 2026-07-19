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
package com.alibaba.cloud.ai.dataagent.connector.impls.hive;

import com.alibaba.cloud.ai.dataagent.connector.accessor.AbstractAccessor;
import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Service;

/**
 * Hive 数据库访问器，支持 Hive 数据源的元数据查询和 SQL 执行。
 * <p>
 * 通过 JDBC 连接 Hive 数据仓库，执行 SHOW DATABASES、SHOW TABLES、DESCRIBE 等命令获取元数据。
 * </p>
 */
@Service("hiveAccessor")
public class HiveDBAccessor extends AbstractAccessor {

	/** 访问器类型标识 */
	private final static String ACCESSOR_TYPE = "Hive_Accessor";

	/**
	 * 构造函数，注入 DDL 工厂和连接池工厂。
	 * @param ddlFactory DDL 执行器工厂
	 * @param poolFactory 连接池工厂
	 */
	protected HiveDBAccessor(DdlFactory ddlFactory, DBConnectionPoolFactory poolFactory) {
		super(ddlFactory, poolFactory.getPoolByDbType(BizDataSourceTypeEnum.HIVE.getTypeName()));
	}

	/** {@inheritDoc} */
	@Override
	public String getAccessorType() {
		return ACCESSOR_TYPE;
	}

	/** {@inheritDoc} */
	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.HIVE.getTypeName().equalsIgnoreCase(type);
	}

}
