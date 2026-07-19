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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link AesGcmCrypto} 单测：覆盖往返、nonce 随机性、幂等、关闭 passthrough、明文互操作、
 * 篡改/轮换 lenient、null/empty、错误密钥长度、Unicode。
 */
class AesGcmCryptoTest {

	// 恰好 32 字节（AES-256）
	private static final byte[] KEY32 = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

	@AfterEach
	void reset() {
		// 恢复 disabled 状态，避免污染同进程其它测试
		AesGcmCrypto.init(null);
	}

	@Test
	void encrypt_decrypt_roundtrip() {
		AesGcmCrypto.init(KEY32);
		String cipher = AesGcmCrypto.encrypt("sk-grok-4.5-secret");
		assertThat(cipher).startsWith(AesGcmCrypto.ENVELOPE_PREFIX);
		assertThat(cipher).isNotEqualTo("sk-grok-4.5-secret");
		assertThat(AesGcmCrypto.decrypt(cipher)).isEqualTo("sk-grok-4.5-secret");
	}

	@Test
	void encrypt_nonce_varies_non_deterministic() {
		AesGcmCrypto.init(KEY32);
		// 相同明文每次密文不同（随机 nonce）
		assertThat(AesGcmCrypto.encrypt("same")).isNotEqualTo(AesGcmCrypto.encrypt("same"));
	}

	@Test
	void encrypt_idempotent_on_ciphertext() {
		AesGcmCrypto.init(KEY32);
		String cipher = AesGcmCrypto.encrypt("plain");
		// 已是 enc:v1: → no-op，防重复加密
		assertThat(AesGcmCrypto.encrypt(cipher)).isEqualTo(cipher);
	}

	@Test
	void disabled_key_passthrough_plaintext() {
		AesGcmCrypto.init(null);
		assertThat(AesGcmCrypto.enabled()).isFalse();
		assertThat(AesGcmCrypto.encrypt("plain")).isEqualTo("plain");
		assertThat(AesGcmCrypto.decrypt("plain")).isEqualTo("plain");
	}

	@Test
	void disabled_key_returns_existing_ciphertext_asis() {
		AesGcmCrypto.init(null);
		// key 移除后遗留密文 → 原样返回（降级，不崩溃；使用方从 provider 得到清晰鉴权失败）
		assertThat(AesGcmCrypto.decrypt("enc:v1:AAAA")).isEqualTo("enc:v1:AAAA");
	}

	@Test
	void plaintext_interop_on_read() {
		AesGcmCrypto.init(KEY32);
		// 遗留明文行（无前缀）→ 即便已启用加密也原样读出（无需迁移）
		assertThat(AesGcmCrypto.decrypt("legacy-plaintext-key")).isEqualTo("legacy-plaintext-key");
	}

	@Test
	void tampered_ciphertext_returns_null_lenient() {
		AesGcmCrypto.init(KEY32);
		String cipher = AesGcmCrypto.encrypt("secret");
		// 解码后翻转首个密文字节（nonce 固定 12 字节，raw[12] 必为密文区，保证是有效位）
		// —— 直接改 base64 末字符不可靠（可能落在 base64url 无效填充位，解码后字节不变）
		byte[] raw = Base64.getUrlDecoder().decode(cipher.substring(AesGcmCrypto.ENVELOPE_PREFIX.length()));
		raw[12] ^= 0x01;
		String tampered = AesGcmCrypto.ENVELOPE_PREFIX
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		assertThat(AesGcmCrypto.decrypt(tampered)).isNull();
	}

	@Test
	void rotated_key_returns_null_lenient() {
		AesGcmCrypto.init(KEY32);
		String cipher = AesGcmCrypto.encrypt("secret");
		// 轮换到另一把 32 字节 key
		AesGcmCrypto.init("99999999999999999999999999999999".getBytes(StandardCharsets.UTF_8));
		assertThat(AesGcmCrypto.decrypt(cipher)).isNull();
	}

	@Test
	void null_and_empty_passthrough() {
		AesGcmCrypto.init(KEY32);
		assertThat(AesGcmCrypto.encrypt(null)).isNull();
		assertThat(AesGcmCrypto.encrypt("")).isEmpty();
		assertThat(AesGcmCrypto.decrypt(null)).isNull();
		assertThat(AesGcmCrypto.decrypt("")).isEmpty();
	}

	@Test
	void non_32_byte_key_disables() {
		boolean enabled = AesGcmCrypto.init("short".getBytes(StandardCharsets.UTF_8));
		assertThat(enabled).isFalse();
		assertThat(AesGcmCrypto.enabled()).isFalse();
	}

	@Test
	void unicode_roundtrip() {
		AesGcmCrypto.init(KEY32);
		String cipher = AesGcmCrypto.encrypt("密码🔑Ünicode");
		assertThat(AesGcmCrypto.decrypt(cipher)).isEqualTo("密码🔑Ünicode");
	}

}
