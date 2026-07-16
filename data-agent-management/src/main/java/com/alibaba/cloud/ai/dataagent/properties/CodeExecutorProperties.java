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

import com.alibaba.cloud.ai.dataagent.enums.CodePoolExecutorEnum;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.PROJECT_PROPERTIES_PREFIX;

/**
 * Python 代码执行器配置属性，绑定 {@code spring.ai.alibaba.data-agent.code-executor.*} 前缀。
 * <p>
 * 支持 {@code local}（本机直接执行）和 {@code docker}（容器隔离执行）两种模式。 生产环境强烈建议使用 docker，避免 LLM
 * 生成的代码污染宿主机。
 * </p>
 *
 * @author vlsmb
 * @since 2025/7/12
 */
@Getter
@Setter
@ConfigurationProperties(prefix = CodeExecutorProperties.CONFIG_PREFIX)
public class CodeExecutorProperties {

	public static final String CONFIG_PREFIX = PROJECT_PROPERTIES_PREFIX + ".code-executor";

	/**
	 * 代码容器池运行时实现方式：{@code local}（本机）/ {@code docker}（容器隔离）
	 */
	CodePoolExecutorEnum codePoolExecutor = CodePoolExecutorEnum.DOCKER;

	/**
	 * 执行服务主机地址；为 null 时使用默认地址
	 */
	String host = null;

	/**
	 * Docker 镜像名称；可替换为自带第三方依赖的自定义镜像
	 */
	String imageName = "continuumio/anaconda3:latest";

	/**
	 * 容器名称前缀
	 */
	String containerNamePrefix = "nl2sql-python-exec-";

	/**
	 * 任务阻塞队列大小
	 */
	Integer taskQueueSize = 5;

	/**
	 * 核心容器最大数量（常驻）
	 */
	Integer coreContainerNum = 2;

	/**
	 * 临时容器最大数量（弹性扩容）
	 */
	Integer tempContainerNum = 2;

	/**
	 * 线程池核心线程数
	 */
	Integer coreThreadSize = 5;

	/**
	 * 线程池最大线程数
	 */
	Integer maxThreadSize = 5;

	/**
	 * 临时容器存活时间（分钟）
	 */
	Integer tempContainerAliveTime = 5;

	/**
	 * 线程池任务存活时间（秒）
	 */
	Long keepThreadAliveTime = 60L;

	/**
	 * 线程池任务阻塞队列大小
	 */
	Integer threadQueueSize = 10;

	/**
	 * 容器内存上限（MB）
	 */
	Long limitMemory = 500L;

	/**
	 * 容器 CPU 核心数
	 */
	Long cpuCore = 1L;

	/**
	 * Python 代码执行超时时间
	 */
	String codeTimeout = "45s";

	/**
	 * 容器最大运行时长
	 */
	Long containerTimeout = 3000L;

	/**
	 * 容器网络模式（none 表示禁用网络，安全隔离）
	 */
	String networkMode = "none";

	/**
	 * Python 执行失败后的最大重试次数
	 */
	Integer pythonMaxTriesCount = 4;

}
