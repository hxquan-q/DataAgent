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
package com.alibaba.cloud.ai.dataagent.service.code.docker;

import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Docker 客户端工厂，依次尝试候选主机列表，连接到第一个可用的 Docker 端点。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DockerClientFactory {

	/** Docker 客户端连接器 */
	private final DockerClientConnector connector;

	/**
	 * 创建连接到第一个可用候选主机的 Docker 客户端。
	 * @param candidateHosts 候选 Docker 主机列表
	 * @return Docker 客户端
	 */
	public DockerClient create(List<String> candidateHosts) {
		return connect(candidateHosts).client();
	}

	/**
	 * 连接到第一个可用的 Docker 端点。
	 * @param candidateHosts 候选 Docker 主机列表
	 * @return Docker 连接信息（客户端 + 主机地址）
	 * @throws IllegalArgumentException 当候选主机列表为空时抛出
	 * @throws IllegalStateException 当所有候选主机都无法连接时抛出
	 */
	public DockerConnection connect(List<String> candidateHosts) {
		if (candidateHosts == null || candidateHosts.isEmpty()) {
			throw new IllegalArgumentException("At least one Docker host candidate is required");
		}

		RuntimeException lastFailure = null;
		for (String host : candidateHosts) {
			DockerClient client = null;
			try {
				client = connector.connect(host);
				client.pingCmd().exec();
				log.info("Connected to Docker using {}", host);
				return new DockerConnection(client, host);
			}
			catch (RuntimeException exception) {
				lastFailure = exception;
				closeFailedClient(client, exception);
				log.warn("Could not connect to Docker using {}: {}", host, exception.getMessage());
			}
		}

		throw new IllegalStateException("Failed to connect to Docker. Attempted hosts: " + candidateHosts, lastFailure);
	}

	public record DockerConnection(DockerClient client, String host) {
	}

	private void closeFailedClient(DockerClient client, RuntimeException connectionFailure) {
		if (client == null) {
			return;
		}
		try {
			client.close();
		}
		catch (IOException closeFailure) {
			connectionFailure.addSuppressed(closeFailure);
		}
	}

}
