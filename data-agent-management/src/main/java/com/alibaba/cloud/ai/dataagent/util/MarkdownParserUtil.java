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

/**
 * Markdown 解析工具类。
 * <p>
 * 提供从 Markdown 文本中提取代码块内容的能力，常用于从 LLM 返回中剥离 Markdown 包装， 得到纯净的代码/JSON 文本。
 * </p>
 */
public class MarkdownParserUtil {

	/**
	 * 提取 Markdown 代码块中的文本，并将换行符统一替换为空格。
	 * <p>
	 * 兼容 {@code \r\n}、{@code \n}、{@code \r} 等换行符，便于后续按单行处理。
	 * </p>
	 * @param markdownCode 包含代码块的 Markdown 文本
	 * @return 提取并清洗后的文本；若不存在代码块则返回原文本
	 */
	public static String extractText(String markdownCode) {
		// 先提取代码块中的纯文本，再将各类换行符替换为空格
		String code = extractRawText(markdownCode);
		// 正确处理多种换行符：\r\n、\n、\r，同时保持与 NewLineParser.format() 的兼容性
		return code.replaceAll("\r\n", " ").replaceAll("\n", " ").replaceAll("\r", " ");
	}

	/**
	 * 提取 Markdown 代码块中的原始文本（保留换行符）。
	 * <p>
	 * 解析逻辑：识别以三个及以上反引号作为起始和结束的代码块，跳过起始行可能存在的语言标识， 返回代码块内部内容；若未找到代码块，则原样返回入参。
	 * </p>
	 * @param markdownCode 包含代码块的 Markdown 文本
	 * @return 代码块内部文本；若不存在代码块或缺少结束分隔符时返回相应剩余文本
	 */
	public static String extractRawText(String markdownCode) {
		// 查找代码块起始位置（3 个或更多连续反引号）
		int startIndex = -1;
		int delimiterLength = 0;

		for (int i = 0; i <= markdownCode.length() - 3; i++) {
			if (markdownCode.substring(i, i + 3).equals("```")) {
				startIndex = i;
				delimiterLength = 3;
				// 统计起始位置之后连续的反引号数量，确定分隔符长度
				while (i + delimiterLength < markdownCode.length() && markdownCode.charAt(i + delimiterLength) == '`') {
					delimiterLength++;
				}
				break;
			}
		}

		// 未找到代码块，原样返回
		if (startIndex == -1) {
			return markdownCode; // No code block found
		}

		// 跳过起始分隔符以及可选的语言标识行
		int contentStart = startIndex + delimiterLength;
		while (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) != '\n') {
			contentStart++;
		}
		if (contentStart < markdownCode.length() && markdownCode.charAt(contentStart) == '\n') {
			contentStart++; // 跳过语言标识行后的换行
		}

		// 查找与起始分隔符等长的结束分隔符
		String closingDelimiter = "`".repeat(delimiterLength);
		int endIndex = markdownCode.indexOf(closingDelimiter, contentStart);

		// 未找到结束分隔符，则返回从内容起始到字符串末尾
		if (endIndex == -1) {
			return markdownCode.substring(contentStart);
		}

		// 截取两个分隔符之间的内容
		return markdownCode.substring(contentStart, endIndex);
	}

}
