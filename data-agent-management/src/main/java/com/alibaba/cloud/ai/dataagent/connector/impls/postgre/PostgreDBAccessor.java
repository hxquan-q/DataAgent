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
package com.alibaba.cloud.ai.dataagent.connector.impls.postgre;

import com.alibaba.cloud.ai.dataagent.connector.ddl.DdlFactory;
import com.alibaba.cloud.ai.dataagent.connector.accessor.AbstractAccessor;
import com.alibaba.cloud.ai.dataagent.connector.pool.DBConnectionPoolFactory;
import com.alibaba.cloud.ai.dataagent.enums.BizDataSourceTypeEnum;
import org.springframework.stereotype.Service;

/**
 * PostgreSQL 数据库访问器，支持 PostgreSQL 数据源的元数据查询和 SQL 执行。
 *
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Service("postgreAccessor")
public class PostgreDBAccessor extends AbstractAccessor {

	/** 访问器类型标识 */
	private final static String ACCESSOR_TYPE = "PostgreSQL_Accessor";

	/**
	 * 构造函数，注入 DDL 工厂和连接池工厂。
	 * @param ddlFactory DDL 执行器工厂
	 * @param poolFactory 连接池工厂
	 */
	protected PostgreDBAccessor(DdlFactory ddlFactory, DBConnectionPoolFactory poolFactory) {

		super(ddlFactory, poolFactory.getPoolByDbType(BizDataSourceTypeEnum.POSTGRESQL.getTypeName()));
	}

	/**
	 * 获取访问器类型标识。
	 * @return 访问器类型名称
	 */
	@Override
	public String getAccessorType() {
		return ACCESSOR_TYPE;
	}

	/**
	 * 判断是否支持指定的数据源类型。
	 * @param type 数据源类型名称
	 * @return 是否为 PostgreSQL 类型
	 */
	@Override
	public boolean supportedDataSourceType(String type) {
		return BizDataSourceTypeEnum.POSTGRESQL.getTypeName().equalsIgnoreCase(type);
	}

}
