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
package com.alibaba.cloud.ai.dataagent.properties;

import com.alibaba.cloud.ai.dataagent.constant.Constant;
import com.alibaba.cloud.ai.dataagent.service.file.FileStorageServiceEnum;
import java.nio.file.Path;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 文件存储相关配置属性，绑定 {@code spring.ai.alibaba.data-agent.file.*} 前缀。
 * <p>
 * 支持 {@code local}（本地磁盘）和 {@code oss}（阿里云 OSS）两种存储方式。
 * 通过 {@code type} 字段切换，OSS 相关配置见 {@link OssStorageProperties}。
 * </p>
 */
@Getter
@Setter
@EnableConfigurationProperties({ OssStorageProperties.class })
@ConfigurationProperties(prefix = Constant.PROJECT_PROPERTIES_PREFIX + ".file")
public class FileStorageProperties {

	/**
	 * 存储类型：{@code local}（本地存储）、{@code oss}（阿里云OSS）
	 */
	private FileStorageServiceEnum type = FileStorageServiceEnum.LOCAL;

	/**
	 * 对象存储路径前缀（通用配置，对OSS和本地存储都适用）。
	 */
	private String pathPrefix = "";

	/**
	 * 本地上传目录路径。
	 * <p>
	 * 注意：不要使用 {@code ./uploads} 形式，Windows 下路径拼接会解析失败。
	 * </p>
	 */
	private String path = "./uploads";

	/**
	 * 对外暴露的访问前缀。
	 */
	private String urlPrefix = "/uploads";

	/**
	 * 头像图片大小上限（字节）。默认 2MB。
	 */
	private long imageSize = 2L * 1024 * 1024;

	/**
	 * 获取本地保存路径，并规范化（去除 {@code .}、{@code ..} 等冗余段）。
	 * @return 本地保存根路径
	 */
	public Path getLocalBasePath() {
		return Path.of(path).normalize();
	}

}
