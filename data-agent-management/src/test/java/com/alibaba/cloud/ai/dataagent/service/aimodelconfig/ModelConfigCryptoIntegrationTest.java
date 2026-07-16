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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import com.alibaba.cloud.ai.dataagent.entity.ModelConfig;
import com.alibaba.cloud.ai.dataagent.enums.ModelType;
import com.alibaba.cloud.ai.dataagent.mapper.ModelConfigMapper;
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
 * 端到端验证 {@code model_config.api_key} / {@code proxy_password} 的透明加解密（真实 H2，非 mock）。
 * <p>
 * 断言三件事：① 落库为 {@code enc:v1:} 密文（rest 加密）；② 经 Mapper 读回为明文（TypeHandler 解密）；
 * ③ 其余 15 列在加了 {@code @Results} 后仍正常自动映射（证明 PARTIAL auto-mapping 未被破坏）。
 * </p>
 */
@SpringBootTest
@ActiveProfiles("h2")
class ModelConfigCryptoIntegrationTest {

	// 恰好 32 字节（AES-256）
	private static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes();

	@Autowired
	private ModelConfigMapper modelConfigMapper;

	private JdbcTemplate jdbcTemplate;

	@Autowired
	private void setJdbcTemplate(DataSource dataSource) {
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	@BeforeEach
	void enableCrypto() {
		// 测试内显式启用加密，不依赖 @PostConstruct 的属性注入顺序
		AesGcmCrypto.init(KEY32);
	}

	@AfterEach
	void disableCrypto() {
		AesGcmCrypto.init(null);
	}

	@Test
	void apiKey_encrypted_at_rest_and_decrypted_on_read() {
		ModelConfig cfg = newConfig("crypto-it", "sk-secret-12345", "proxy-pwd-99");
		modelConfigMapper.insert(cfg);
		Integer id = cfg.getId();
		assertThat(id).isNotNull();

		try {
			// ① 原始 DB 视角：密文
			String storedKey = jdbcTemplate.queryForObject("SELECT api_key FROM model_config WHERE id = ?",
					String.class, id);
			String storedProxy = jdbcTemplate.queryForObject("SELECT proxy_password FROM model_config WHERE id = ?",
					String.class, id);
			assertThat(storedKey).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(storedKey).doesNotContain("sk-secret");
			assertThat(storedProxy).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(storedProxy).doesNotContain("proxy-pwd");

			// ② Mapper 读回：明文（TypeHandler 解密）
			ModelConfig read = modelConfigMapper.findById(id);
			assertThat(read).isNotNull();
			assertThat(read.getApiKey()).isEqualTo("sk-secret-12345");
			assertThat(read.getProxyPassword()).isEqualTo("proxy-pwd-99");

			// ③ 其余列仍正常自动映射（@Results 仅声明 2 列，PARTIAL auto-mapping 覆盖其余）
			assertThat(read.getProvider()).isEqualTo("crypto-it");
			assertThat(read.getBaseUrl()).isEqualTo("https://example.com");
			assertThat(read.getModelName()).isEqualTo("grok-test");
			assertThat(read.getModelType()).isEqualTo(ModelType.CHAT);
			assertThat(read.getIsActive()).isTrue();
			assertThat(read.getProxyEnabled()).isTrue();
			assertThat(read.getProxyPort()).isEqualTo(8080);
		}
		finally {
			modelConfigMapper.deleteById(id);
		}
	}

	@Test
	void update_roundtrips_and_re_encrypts() {
		ModelConfig cfg = newConfig("crypto-upd", "sk-orig-aaa", null);
		modelConfigMapper.insert(cfg);
		Integer id = cfg.getId();
		try {
			// 改 apiKey 后更新
			ModelConfig read = modelConfigMapper.findById(id);
			read.setApiKey("sk-rotated-bbb");
			modelConfigMapper.updateById(read);

			String stored = jdbcTemplate.queryForObject("SELECT api_key FROM model_config WHERE id = ?", String.class,
					id);
			assertThat(stored).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(modelConfigMapper.findById(id).getApiKey()).isEqualTo("sk-rotated-bbb");
		}
		finally {
			modelConfigMapper.deleteById(id);
		}
	}

	private ModelConfig newConfig(String provider, String apiKey, String proxyPassword) {
		ModelConfig cfg = new ModelConfig();
		cfg.setProvider(provider);
		cfg.setBaseUrl("https://example.com");
		cfg.setApiKey(apiKey);
		cfg.setModelName("grok-test");
		cfg.setModelType(ModelType.CHAT);
		cfg.setTemperature(0.7);
		cfg.setIsActive(true);
		cfg.setMaxTokens(4096);
		cfg.setIsDeleted(0);
		cfg.setProxyEnabled(true);
		cfg.setProxyHost("127.0.0.1");
		cfg.setProxyPort(8080);
		cfg.setProxyUsername("u");
		cfg.setProxyPassword(proxyPassword);
		return cfg;
	}

}
