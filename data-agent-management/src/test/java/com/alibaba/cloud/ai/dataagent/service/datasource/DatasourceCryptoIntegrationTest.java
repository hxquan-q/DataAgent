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
package com.alibaba.cloud.ai.dataagent.service.datasource;

import com.alibaba.cloud.ai.dataagent.entity.Datasource;
import com.alibaba.cloud.ai.dataagent.mapper.DatasourceMapper;
import com.alibaba.cloud.ai.dataagent.util.AesGcmCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证 {@code datasource.password} 透明加解密（真实 H2）。业务库密码是最高敏感凭据。
 */
@SpringBootTest
@ActiveProfiles("h2")
class DatasourceCryptoIntegrationTest {

	private static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes();

	@Autowired
	private DatasourceMapper datasourceMapper;

	private JdbcTemplate jdbcTemplate;

	@Autowired
	private void setJdbcTemplate(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@BeforeEach
	void enableCrypto() {
		AesGcmCrypto.init(KEY32);
	}

	@AfterEach
	void disableCrypto() {
		AesGcmCrypto.init(null);
	}

	@Test
	void password_encrypted_at_rest_and_decrypted_on_read() {
		Datasource ds = Datasource.builder()
			.name("crypto-ds-it")
			.type("mysql")
			.host("10.0.0.1")
			.port(3306)
			.databaseName("biz_db")
			.username("app_user")
			.password("biz-secret-pwd-42")
			.connectionUrl("jdbc:mysql://10.0.0.1:3306/biz_db")
			.status("1")
			.testStatus("ok")
			.creatorId(1L)
			.build();
		datasourceMapper.insert(ds);
		Integer id = ds.getId();
		assertThat(id).isNotNull();

		try {
			// 原始 DB 视角：密文
			String stored = jdbcTemplate.queryForObject("SELECT password FROM datasource WHERE id = ?", String.class, id);
			assertThat(stored).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(stored).doesNotContain("biz-secret");

			// Mapper 读回：明文 + 其余列自动映射
			Datasource read = datasourceMapper.selectById(id);
			assertThat(read).isNotNull();
			assertThat(read.getPassword()).isEqualTo("biz-secret-pwd-42");
			assertThat(read.getName()).isEqualTo("crypto-ds-it");
			assertThat(read.getType()).isEqualTo("mysql");
			assertThat(read.getHost()).isEqualTo("10.0.0.1");
			assertThat(read.getPort()).isEqualTo(3306);
			assertThat(read.getDatabaseName()).isEqualTo("biz_db");

			// 更新密码 → 重加密
			read.setPassword("rotated-pwd-99");
			datasourceMapper.updateById(read);
			String stored2 = jdbcTemplate.queryForObject("SELECT password FROM datasource WHERE id = ?", String.class,
					id);
			assertThat(stored2).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(datasourceMapper.selectById(id).getPassword()).isEqualTo("rotated-pwd-99");
		}
		finally {
			datasourceMapper.deleteById(id);
		}
	}

}
