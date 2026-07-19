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

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 启动时注入 AES 主密钥到 {@link AesGcmCrypto}。
 * <p>
 * 密钥来源：{@code spring.ai.alibaba.data-agent.crypto.aes-key}，回退环境变量 {@code SYSTEM_AES_KEY}
 * （与 Tencent/WeKnora 兼容）。未配置 / 非 32 字节 → 加密关闭（passthrough，迁移安全；CI 不配 key 即不启用）。
 * </p>
 */
@Component
public class CryptoKeyInitializer {

	private static final Logger log = LoggerFactory.getLogger(CryptoKeyInitializer.class);

	@Value("${spring.ai.alibaba.data-agent.crypto.aes-key:${SYSTEM_AES_KEY:}}")
	private String aesKey;

	@PostConstruct
	public void init() {
		if (aesKey == null || aesKey.isEmpty()) {
			log.warn("AES master key not set (spring.ai.alibaba.data-agent.crypto.aes-key / SYSTEM_AES_KEY); "
					+ "credential encryption DISABLED (passthrough). Set a 32-byte key to enable.");
			AesGcmCrypto.init(null);
			return;
		}
		byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
		boolean enabled = AesGcmCrypto.init(raw);
		if (!enabled) {
			log.warn("AES master key must be exactly 32 bytes (got {}); credential encryption DISABLED.", raw.length);
		}
	}

}
