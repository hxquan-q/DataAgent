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

import java.security.SecureRandom;

/**
 * API Key 工具类，提供 API Key 的生成与脱敏处理功能。
 * <p>
 * 生成的 API Key 以 "sk-" 作为前缀，并附带固定长度的随机字符串；脱敏处理仅保留末尾 4 位字符，其余替换为 "*"。
 * </p>
 */
public final class ApiKeyUtil {

	/** API Key 的固定前缀 */
	private static final String API_KEY_PREFIX = "sk-";

	/** 用于生成 API Key 随机部分的字符集合（大小写字母与数字） */
	private static final String API_KEY_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

	/** API Key 随机部分的长度 */
	private static final int API_KEY_LENGTH = 32;

	/** 线程安全的强随机数生成器 */
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private ApiKeyUtil() {
	}

	/**
	 * 生成一个新的 API Key。
	 * <p>
	 * 格式为 "sk-" 前缀加上 {@value #API_KEY_LENGTH} 位由字母和数字组成的随机字符串。
	 * </p>
	 * @return 新生成的 API Key 字符串
	 */
	public static String generate() {
		StringBuilder builder = new StringBuilder(API_KEY_PREFIX);
		// 循环追加随机字符，构造指定长度的随机部分
		for (int i = 0; i < API_KEY_LENGTH; i++) {
			int idx = SECURE_RANDOM.nextInt(API_KEY_CHARS.length());
			builder.append(API_KEY_CHARS.charAt(idx));
		}
		return builder.toString();
	}

	/**
	 * 对 API Key 进行脱敏处理，仅保留末尾 4 位字符。
	 * @param apiKey 原始 API Key，可为 null
	 * @return 脱敏后的字符串；当入参为空或长度不足时返回 "****"
	 */
	public static String mask(String apiKey) {
		// 入参为空或长度过短，直接返回全掩码
		if (apiKey == null || apiKey.length() <= 8) {
			return "****";
		}
		// 截取末尾 4 位作为可见部分
		String suffix = apiKey.substring(apiKey.length() - 4);
		return "****" + suffix;
	}

}
