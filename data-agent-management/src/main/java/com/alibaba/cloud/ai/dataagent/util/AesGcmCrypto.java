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

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM 可逆凭据加密工具（参考 Tencent/WeKnora {@code internal/utils/crypto.go}）。
 * <p>
 * 用于对入库凭据（模型 API Key、数据源密码、代理密码等）做透明加解密。密文带 {@link #ENVELOPE_PREFIX}
 * 前缀，可瞬间区分密文与遗留明文——因此<strong>启用无需数据迁移</strong>：未加前缀的旧行原样读出。
 * </p>
 * <p>
 * <strong>迁移安全</strong>：未注入合法主密钥（nil/非 32 字节）时加密关闭，encrypt/decrypt 均为
 * passthrough——CI 不配 key 即不启用，既有 135+ 测试零影响。主密钥由 {@link CryptoKeyInitializer} 启动注入。
 * </p>
 */
public final class AesGcmCrypto {

	/** 密文信封前缀，任何代码可据此区分密文与遗留明文（明文互操作，无需迁移）。 */

	public static final String ENVELOPE_PREFIX = "enc:v1:";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int NONCE_LEN = 12; // 96-bit GCM nonce（IV）

	private static final int TAG_LEN_BITS = 128; // GCM 认证标签长度

	private static final int KEY_LEN = 32; // AES-256

	private static final SecureRandom RANDOM = new SecureRandom();

	/** 主密钥；为 null 表示加密关闭（passthrough，迁移安全）。由 CryptoKeyInitializer 注入。 */

	private static volatile byte[] key;

	private AesGcmCrypto() {
	}

	/**
	 * 注入主密钥；null 或非 32 字节 → 关闭加密（passthrough）。
	 * @param masterKey 32 字节 AES-256 主密钥
	 * @return 是否成功启用加密
	 */
	public static synchronized boolean init(byte[] masterKey) {
		if (masterKey == null || masterKey.length != KEY_LEN) {
			key = null;
			return false;
		}
		key = masterKey.clone();
		return true;
	}

	/** 当前是否已启用加密（已注入合法主密钥）。 */

	public static boolean enabled() {
		return key != null;
	}

	/** 是否为密文（以信封前缀开头）。 */
	public static boolean isEncrypted(String value) {
		return value != null && value.startsWith(ENVELOPE_PREFIX);
	}

	/**
	 * 加密。规则：未启用 / 入参 null 或空 / 已是密文 → 原样返回（passthrough + 幂等）。
	 * @param plain 明文凭据
	 * @return 密文（{@code enc:v1:base64url(nonce‖ct+tag)}）或原值
	 */
	public static String encrypt(String plain) {
		if (plain == null || plain.isEmpty()) {
			return plain;
		}
		if (!enabled() || isEncrypted(plain)) {
			return plain;
		}
		try {
			byte[] nonce = new byte[NONCE_LEN];
			RANDOM.nextBytes(nonce);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN_BITS, nonce));
			byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
			byte[] out = new byte[nonce.length + ct.length];
			System.arraycopy(nonce, 0, out, 0, nonce.length);
			System.arraycopy(ct, 0, out, nonce.length, ct.length);
			return ENVELOPE_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out);
		}
		catch (Exception e) {
			// 凭据加密失败不可静默吞掉——fail-loud，交由调用方处理，避免明文意外落库。
			throw new IllegalStateException("AES-GCM encrypt failed", e);
		}
	}

	/**
	 * 解密。规则：未启用 / 入参 null 或空 / 非密文(明文互操作) → 原样返回； GCM 鉴权失败（密钥轮换/篡改/损坏）
	 * → 返回 {@code null}（lenient，防列表/启动链路级联崩溃）。
	 * @param stored 库中存储值
	 * @return 明文，或原值，或 null（密文损坏时）
	 */
	public static String decrypt(String stored) {
		if (stored == null || stored.isEmpty() || !isEncrypted(stored) || !enabled()) {
			return stored;
		}
		try {
			byte[] all = Base64.getUrlDecoder().decode(stored.substring(ENVELOPE_PREFIX.length()));
			byte[] nonce = new byte[NONCE_LEN];
			byte[] ct = new byte[all.length - NONCE_LEN];
			System.arraycopy(all, 0, nonce, 0, NONCE_LEN);
			System.arraycopy(all, NONCE_LEN, ct, 0, ct.length);
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_LEN_BITS, nonce));
			return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
		}
		catch (Exception e) {
			// ponytail: lenient 解密——密钥轮换/篡改/损坏 返回 null 而非抛异常。
			// 使用方（DynamicModelFactory）拿到 null key 会从 provider 得到清晰鉴权失败，优于拿到密文垃圾。
			// 若未来需要严格模式（要使用凭据的场景），再加 decryptStrict。
			return null;
		}
	}

}
