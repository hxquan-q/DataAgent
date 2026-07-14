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
package com.alibaba.cloud.ai.dataagent.prompt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板加载器，从文件系统加载提示词模板内容。
 *
 * @author zhangshenghang
 */
@Slf4j
public class PromptLoader {

	private static final String PROMPT_PATH_PREFIX = "prompts/";

	private static final ConcurrentHashMap<String, String> promptCache = new ConcurrentHashMap<>();

	/**
	 * 从文件系统加载提示词模板（带缓存）。
	 * @param promptName 提示词文件名（不含路径和扩展名）
	 * @return 提示词模板内容
	 */
	public static String loadPrompt(String promptName) {
		return promptCache.computeIfAbsent(promptName, name -> {
			String fileName = PROMPT_PATH_PREFIX + name + ".txt";
			// 使用本类的类加载器获取资源（避免jar包中无法获取资源）
			try (InputStream inputStream = PromptLoader.class.getClassLoader().getResourceAsStream(fileName)) {
				return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
			}
			catch (IOException e) {
				log.error("加载提示词失败！{}", e.getMessage(), e);
				throw new RuntimeException("加载提示词失败: " + name, e);
			}
		});
	}

	/**
	 * 清空提示词缓存。
	 */
	public static void clearCache() {
		promptCache.clear();
	}

	/**
	 * 获取当前缓存中的提示词数量。
	 * @return 缓存大小
	 */
	public static int getCacheSize() {
		return promptCache.size();
	}

}
