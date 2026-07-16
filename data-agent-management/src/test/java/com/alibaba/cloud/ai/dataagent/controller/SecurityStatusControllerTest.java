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
package com.alibaba.cloud.ai.dataagent.controller;

import com.alibaba.cloud.ai.dataagent.util.AesGcmCrypto;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SecurityStatusController} 单测——加密状态随主密钥注入翻转（纯逻辑，无需 Spring）。
 */
class SecurityStatusControllerTest {

	private static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

	private final SecurityStatusController controller = new SecurityStatusController();

	@AfterEach
	void reset() {
		AesGcmCrypto.init(null);
	}

	@Test
	void disabled_when_no_key() {
		AesGcmCrypto.init(null);
		var status = controller.cryptoStatus().getData();
		assertThat(status.enabled()).isFalse();
		assertThat(status.keyConfigured()).isEqualTo("not-configured");
		assertThat(status.algorithm()).isEqualTo("AES-256-GCM");
	}

	@Test
	void enabled_when_key_configured() {
		AesGcmCrypto.init(KEY32);
		var status = controller.cryptoStatus().getData();
		assertThat(status.enabled()).isTrue();
		assertThat(status.keyConfigured()).isEqualTo("configured");
		assertThat(status.envelopePrefix()).isEqualTo(AesGcmCrypto.ENVELOPE_PREFIX);
		assertThat(status.coverage()).contains("datasource.password", "agent.api_key");
	}

}
