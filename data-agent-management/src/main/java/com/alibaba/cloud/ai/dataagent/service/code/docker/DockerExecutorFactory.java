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

import com.alibaba.cloud.ai.dataagent.properties.CodeExecutorProperties;
import com.alibaba.cloud.ai.dataagent.service.code.docker.DockerClientFactory.DockerConnection;
import com.alibaba.cloud.ai.dataagent.service.code.impls.DockerCodePoolExecutorService;
import com.github.dockerjava.api.DockerClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

/**
 * Docker 代码执行器工厂的组合根，负责解析 Docker 主机、创建客户端并确保镜像可用。
 */
@Component
@RequiredArgsConstructor
public class DockerExecutorFactory {

	/** Docker 主机解析器 */
	private final DockerHostResolver hostResolver;

	/** Docker 客户端工厂 */
	private final DockerClientFactory clientFactory;

	/** Docker 镜像管理器 */
	private final DockerImageManager imageManager;

	/**
	 * 创建基于 Docker 的代码执行器服务实例。
	 * @param properties 代码执行器配置属性
	 * @return Docker 代码执行器服务
	 */
	public DockerCodePoolExecutorService create(CodeExecutorProperties properties) {
		List<String> candidateHosts = hostResolver.candidates(properties.getHost(), System.getProperty("os.name"));
		DockerConnection connection = clientFactory.connect(candidateHosts);
		DockerClient client = connection.client();
		try {
			imageManager.ensureAvailable(client, properties.getImageName());
			return new DockerCodePoolExecutorService(properties, client, hostResolver.isRemote(connection.host()));
		}
		catch (RuntimeException constructionFailure) {
			closeAfterFailedConstruction(client, constructionFailure);
			throw constructionFailure;
		}
	}

	private void closeAfterFailedConstruction(DockerClient client, RuntimeException constructionFailure) {
		try {
			client.close();
		}
		catch (IOException closeFailure) {
			constructionFailure.addSuppressed(closeFailure);
		}
	}

}
