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
package com.alibaba.cloud.ai.dataagent.enums;

/**
 * 运行Python任务的容器池（实现枚举）
 *
 * @author vlsmb
 * @since 2025/7/28
 */
public enum CodePoolExecutorEnum {

	/** 基于 Docker 容器的执行器 */
	DOCKER,
	/** 基于 containerd 的容器执行器 */
	CONTAINERD,
	/** 基于 Kata Containers 的安全容器执行器 */
	KATA,
	/** AI 模拟执行器（非真实容器，用于测试/演示） */
	AI_SIMULATION,
	/** 本地进程执行器 */
	LOCAL;

}
