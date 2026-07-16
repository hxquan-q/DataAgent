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
package com.alibaba.cloud.ai.dataagent.service.agent;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.util.AesGcmCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端验证 {@code agent.api_key} 透明加解密（真实 H2）。重点验证 {@code updateApiKey} 的 @Param
 * typeHandler 路径与多 @Select 的 @ResultMap 解析（启动期校验）。
 */
@SpringBootTest
@ActiveProfiles("h2")
class AgentCryptoIntegrationTest {

	private static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes();

	@Autowired
	private AgentMapper agentMapper;

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
	void api_key_encrypted_at_rest_and_update_api_key_path() {
		LocalDateTime now = LocalDateTime.now();
		Agent agent = Agent.builder()
			.name("crypto-agent-it")
			.status("draft")
			.apiKey("sk-agent-secret-xyz")
			.apiKeyEnabled(1)
			.prompt("p")
			.category("c")
			.adminId(1L)
			.tags("t")
			.workflowMode("nl2sql")
			.createTime(now)
			.updateTime(now)
			.build();
		agentMapper.insert(agent);
		Long id = agent.getId();
		assertThat(id).isNotNull();

		try {
			// 落库密文
			String stored = jdbcTemplate.queryForObject("SELECT api_key FROM agent WHERE id = ?", String.class, id);
			assertThat(stored).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(stored).doesNotContain("sk-agent-secret");

			// 读回明文 + 其余列自动映射
			Agent read = agentMapper.findById(id);
			assertThat(read.getApiKey()).isEqualTo("sk-agent-secret-xyz");
			assertThat(read.getName()).isEqualTo("crypto-agent-it");
			assertThat(read.getStatus()).isEqualTo("draft");
			assertThat(read.getApiKeyEnabled()).isEqualTo(1);

			// updateApiKey（@Param + typeHandler 路径）→ 重加密
			agentMapper.updateApiKey(id, "sk-rotated-abc", 1);
			String stored2 = jdbcTemplate.queryForObject("SELECT api_key FROM agent WHERE id = ?", String.class, id);
			assertThat(stored2).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
			assertThat(agentMapper.findById(id).getApiKey()).isEqualTo("sk-rotated-abc");
		}
		finally {
			agentMapper.deleteById(id);
		}
	}

}
