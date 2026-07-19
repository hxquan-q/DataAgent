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
package com.alibaba.cloud.ai.dataagent.service.code.local;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码执行超时时间解析器，解析紧凑的超时格式（如 "30s"、"5m"、"1h"）。
 */
public final class ExecutionTimeoutParser {

	/** 默认超时时间（60 秒） */
	public static final long DEFAULT_TIMEOUT_MILLIS = 60_000L;

	/** 超时格式正则：数字 + 单位（ms/s/m/h/d） */
	private static final Pattern TIMEOUT_PATTERN = Pattern.compile("(\\d+)(ms|[smhd])");

	/**
	 * 解析超时字符串为毫秒数。
	 * @param value 超时字符串（如 "30s"、"5m"），为空时返回默认值
	 * @return 超时毫秒数
	 */
	public long parse(String value) {
		if (value == null) {
			return DEFAULT_TIMEOUT_MILLIS;
		}
		Matcher matcher = TIMEOUT_PATTERN.matcher(value.trim().toLowerCase(Locale.ROOT));
		if (!matcher.matches()) {
			return DEFAULT_TIMEOUT_MILLIS;
		}

		try {
			long amount = Long.parseLong(matcher.group(1));
			return Math.multiplyExact(amount, multiplier(matcher.group(2)));
		}
		catch (ArithmeticException | NumberFormatException exception) {
			return DEFAULT_TIMEOUT_MILLIS;
		}
	}

	private long multiplier(String unit) {
		return switch (unit) {
			case "ms" -> 1L;
			case "s" -> 1_000L;
			case "m" -> 60_000L;
			case "h" -> 3_600_000L;
			case "d" -> 86_400_000L;
			default -> throw new IllegalArgumentException("Unsupported timeout unit: " + unit);
		};
	}

}
