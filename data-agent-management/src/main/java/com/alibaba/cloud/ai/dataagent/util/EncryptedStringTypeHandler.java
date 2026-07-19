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
package com.alibaba.cloud.ai.dataagent.util;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis String TypeHandler：写库前 {@link AesGcmCrypto#encrypt(String) 加密}，读库后
 * {@link AesGcmCrypto#decrypt(String) 解密}。对调用方<strong>透明</strong>——实体字段始终持有明文。
 * <p>
 * 对应 Tencent/WeKnora 在 GORM {@code Value()}/{@code Scan()} 钩子里的加解密。DataAgent 用 MyBatis（注解式
 * SQL），故移植件是 TypeHandler 而非 JPA {@code AttributeConverter}。在注解 Mapper 上以
 * {@code typeHandler=EncryptedStringTypeHandler.class} 挂到具体列（见 {@code ModelConfigMapper}）。
 * </p>
 */
@MappedTypes(String.class)
public class EncryptedStringTypeHandler extends BaseTypeHandler<String> {

	@Override
	public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
			throws SQLException {
		ps.setString(i, AesGcmCrypto.encrypt(parameter));
	}

	@Override
	public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
		return AesGcmCrypto.decrypt(rs.getString(columnName));
	}

	@Override
	public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
		return AesGcmCrypto.decrypt(rs.getString(columnIndex));
	}

	@Override
	public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
		return AesGcmCrypto.decrypt(cs.getString(columnIndex));
	}

}
