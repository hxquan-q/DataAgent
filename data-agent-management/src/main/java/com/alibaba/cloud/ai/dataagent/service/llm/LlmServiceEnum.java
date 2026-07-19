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
package com.alibaba.cloud.ai.dataagent.service.llm;

/**
 * LLM 服务类型枚举，区分阻塞式调用和流式调用两种模式。
 */
public enum LlmServiceEnum {

	/** 阻塞式调用模式，等待完整响应后返回 */
	BLOCK,
	/** 流式调用模式，逐步返回响应内容 */
	STREAM

}
