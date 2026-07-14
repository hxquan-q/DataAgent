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
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.command.PullImageResultCallback;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Docker 镜像管理器，确保配置的 Docker 镜像在本地可用，不存在时自动拉取。
 */
@Component
public class DockerImageManager {

	/** 拉取镜像回调工厂 */
	private final Supplier<PullImageResultCallback> callbackFactory;

	public DockerImageManager() {
		this(PullImageResultCallback::new);
	}

	DockerImageManager(Supplier<PullImageResultCallback> callbackFactory) {
		this.callbackFactory = Objects.requireNonNull(callbackFactory, "callbackFactory");
	}

	/**
	 * 确保指定镜像在本地可用，不存在时从远程仓库拉取。
	 * @param client Docker 客户端
	 * @param imageName 镜像名称
	 */
	public void ensureAvailable(DockerClient client, String imageName) {
		List<Image> images = client.listImagesCmd().withImageNameFilter(imageName).exec();
		boolean imageExists = images != null && images.stream()
			.map(Image::getRepoTags)
			.filter(Objects::nonNull)
			.flatMap(Arrays::stream)
			.anyMatch(imageName::equals);
		if (imageExists) {
			return;
		}

		PullImageResultCallback callback = callbackFactory.get();
		try {
			client.pullImageCmd(imageName).exec(callback).awaitCompletion();
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while pulling Docker image " + imageName, exception);
		}
	}

}
