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
package com.alibaba.cloud.ai.dataagent.service.vectorstore;

import com.alibaba.cloud.ai.dataagent.properties.DataAgentProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SimpleVectorStore 初始化与持久化服务。
 *
 * <p>
 * 在应用启动时从本地文件加载向量数据，在应用关闭时将向量数据序列化到本地文件。 仅适用于 {@link SimpleVectorStore}（内存向量存储）。
 * </p>
 *
 * @author David Yu
 */
@Slf4j
@RequiredArgsConstructor
public class SimpleVectorStoreInitialization implements ApplicationRunner, DisposableBean {

	/** SimpleVectorStore 实例 */
	private final SimpleVectorStore vectorStore;

	/** DataAgent 配置属性 */
	private final DataAgentProperties properties;

	/**
	 * 从本地文件加载向量数据库。
	 */
	public void load() {
		File file = new File(properties.getVectorStore().getFilePath());

		if (!file.exists()) {
			log.info("No locally serialized vector database file was found.");
			return;
		}

		try {
			vectorStore.load(file);
		}
		catch (Throwable throwable) {
			log.error("Failed to load the locally serialized vector database file.", throwable);
		}
	}

	/**
	 * 将向量数据库序列化到本地文件。
	 */
	public void save() {
		log.info("Serialize the vector database to a local file.");
		Path path = Paths.get(properties.getVectorStore().getFilePath());

		try {
			Files.createDirectories(path.getParent());

			if (!Files.exists(path)) {
				Files.createFile(path);
			}

			vectorStore.save(path.toFile());
		}
		catch (Throwable t) {
			log.error("An exception occurred while serializing the vector database to a local file.", t);
		}
	}

	/**
	 * 应用启动时加载向量数据库。
	 * @param args 应用启动参数
	 */
	@Override
	public void run(ApplicationArguments args) throws Exception {
		this.load();
	}

	/**
	 * 应用关闭时保存向量数据库。
	 */
	@Override
	public void destroy() {
		this.save();
	}

}
