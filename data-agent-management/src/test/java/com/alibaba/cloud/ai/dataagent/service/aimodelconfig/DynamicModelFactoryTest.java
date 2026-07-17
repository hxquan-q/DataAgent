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
package com.alibaba.cloud.ai.dataagent.service.aimodelconfig;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class DynamicModelFactoryTest {

	@Test
	void resolveMaxTokens_defaultsAndClamps() {
		assertEquals(1536, DynamicModelFactory.resolveMaxTokens(null));
		assertEquals(1536, DynamicModelFactory.resolveMaxTokens(0));
		assertEquals(1536, DynamicModelFactory.resolveMaxTokens(-1));
		assertEquals(2048, DynamicModelFactory.resolveMaxTokens(2048));
		assertEquals(8192, DynamicModelFactory.resolveMaxTokens(100_000));
	}

}
