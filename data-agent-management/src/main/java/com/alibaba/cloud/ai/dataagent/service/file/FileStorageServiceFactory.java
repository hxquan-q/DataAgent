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
package com.alibaba.cloud.ai.dataagent.service.file;

import com.alibaba.cloud.ai.dataagent.properties.FileStorageProperties;
import com.alibaba.cloud.ai.dataagent.properties.OssStorageProperties;
import com.alibaba.cloud.ai.dataagent.service.file.impls.LocalFileStorageServiceImpl;
import com.alibaba.cloud.ai.dataagent.service.file.impls.OssFileStorageServiceImpl;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

/**
 * 文件存储服务工厂，实现 {@link FactoryBean} 根据配置创建本地存储或阿里云 OSS 存储的服务实例。
 */
@Component
@AllArgsConstructor
public class FileStorageServiceFactory implements FactoryBean<FileStorageService> {

	/** 文件存储配置属性 */
	private final FileStorageProperties properties;

	/** OSS 存储配置属性 */
	private final OssStorageProperties ossProperties;

	/**
	 * 根据配置创建对应的文件存储服务实例。
	 * @return 文件存储服务实例
	 */
	@Override
	public FileStorageService getObject() {
		if (FileStorageServiceEnum.OSS.equals(properties.getType())) {
			return new OssFileStorageServiceImpl(properties, ossProperties);
		}
		else {
			return new LocalFileStorageServiceImpl(properties);
		}
	}

	/**
	 * 返回工厂生产的对象类型。
	 * @return 文件存储服务接口类型
	 */
	@Override
	public Class<?> getObjectType() {
		return FileStorageService.class;
	}

}
