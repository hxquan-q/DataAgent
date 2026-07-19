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
package com.alibaba.cloud.ai.dataagent.service.code.local;

import java.util.List;
import java.util.Optional;

/**
 * 可执行程序定位器接口，从有序候选列表中查找第一个可用的可执行程序。
 */
@FunctionalInterface
public interface ExecutableProgramLocator {

	/**
	 * 从候选程序名列表中查找第一个可用的可执行程序。
	 * @param programNames 候选程序名列表（按优先级排序）
	 * @return 第一个找到的可执行程序名，未找到时返回空
	 */
	Optional<String> findFirst(List<String> programNames);

}
