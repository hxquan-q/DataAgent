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
package com.alibaba.cloud.ai.dataagent.config;

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import lombok.AllArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import java.nio.file.Paths;
import java.time.Duration;

/**
 * Web 配置类（WebFlux 版本）。
 * <p>
 * 主要负责将本地文件存储目录映射为静态资源访问路径， 使上传的文件可通过 {@code urlPrefix} 对外访问。
 * </p>
 */
@Configuration
@AllArgsConstructor
public class WebConfig implements WebFluxConfigurer {

	private final FileStorageProperties fileStorageProperties;

	/**
	 * 注册静态资源处理器：将 {@code urlPrefix/**} 映射到本地上传目录。
	 * <p>
	 * 设置 1 小时浏览器缓存，减少重复请求。
	 * </p>
	 * @param registry 资源处理器注册器
	 */
	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		String uploadDir = Paths.get(fileStorageProperties.getPath()).toAbsolutePath().toString();

		registry.addResourceHandler(fileStorageProperties.getUrlPrefix() + "/**")
			.addResourceLocations("file:" + uploadDir + "/")
			.setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));
	}

}
