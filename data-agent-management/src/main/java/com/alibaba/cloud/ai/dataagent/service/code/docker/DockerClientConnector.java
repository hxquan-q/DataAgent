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

/**
 * Docker 客户端连接器接口，为单个 Docker 端点创建客户端（不包含守护进程探测）。
 */
@FunctionalInterface
public interface DockerClientConnector {

	/**
	 * 连接到指定的 Docker 主机。
	 * @param host Docker 主机地址
	 * @return Docker 客户端
	 */
	DockerClient connect(String host);

}
