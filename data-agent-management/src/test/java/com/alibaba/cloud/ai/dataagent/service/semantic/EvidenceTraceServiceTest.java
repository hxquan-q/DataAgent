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
package com.alibaba.cloud.ai.dataagent.service.semantic;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * {@link EvidenceTraceService} 单元测试。
 * <p>
 * 验证语义对象 JSON 序列化委托与 null 透传行为：序列化产物须为合法 JSON 且可被 ObjectMapper 反解； 语义对象为 null 时下游
 * {@link QueryLogService#logQuery} 收到 null。
 * </p>
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class EvidenceTraceServiceTest {

	@Mock
	private QueryLogService queryLogService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@InjectMocks
	private EvidenceTraceService evidenceTraceService = new EvidenceTraceService(null, null);

	@Captor
	private ArgumentCaptor<String> semanticObjectJsonCaptor;

	@BeforeEach
	void setUp() {
		evidenceTraceService = new EvidenceTraceService(queryLogService, objectMapper);
	}

	@Test
	void trace_whenSemanticObjectProvided_shouldSerializeAndDelegate() throws Exception {
		// given: 一个可序列化的语义对象
		Map<String, Object> semanticObject = new LinkedHashMap<>();
		semanticObject.put("metric", "gmv");
		semanticObject.put("dimensions", java.util.List.of("region", "category"));
		semanticObject.put("value", 42);
		given(queryLogService.logQuery(eq("sess-1"), eq(7), eq(3), eq("查询 GMV"), any(String.class), eq("SELECT 1"),
				eq(120), eq(10), eq("SUCCESS"), eq("trace-1")))
			.willReturn(42L);

		// when
		Long id = evidenceTraceService.trace("sess-1", 7, 3, "查询 GMV", semanticObject, "SELECT 1", 120, 10, "SUCCESS",
				"trace-1");

		// then: 返回下游主键 ID
		assertThat(id).isEqualTo(42L);

		// then: 下游收到的 semanticObjectJson 是合法 JSON，且可解析回等价结构
		verify(queryLogService).logQuery(eq("sess-1"), eq(7), eq(3), eq("查询 GMV"), semanticObjectJsonCaptor.capture(),
				eq("SELECT 1"), eq(120), eq(10), eq("SUCCESS"), eq("trace-1"));
		String captured = semanticObjectJsonCaptor.getValue();
		assertThat(captured).isNotNull();
		assertThat(objectMapper.readTree(captured).path("metric").asText()).isEqualTo("gmv");
		assertThat(objectMapper.readValue(captured, Map.class)).containsEntry("value", 42);
	}

	@Test
	void trace_whenSemanticObjectIsNull_shouldPassNull() {
		// given
		given(queryLogService.logQuery(eq("sess-2"), eq(8), eq(4), eq("闲聊"), isNull(String.class), eq("SELECT 2"),
				eq(0), eq(0), eq("CLARIFY"), eq("trace-2")))
			.willReturn(99L);

		// when
		Long id = evidenceTraceService.trace("sess-2", 8, 4, "闲聊", null, "SELECT 2", 0, 0, "CLARIFY", "trace-2");

		// then: 返回下游主键 ID
		assertThat(id).isEqualTo(99L);

		// then: 下游收到 null
		verify(queryLogService).logQuery(eq("sess-2"), eq(8), eq(4), eq("闲聊"), isNull(String.class), eq("SELECT 2"),
				eq(0), eq(0), eq("CLARIFY"), eq("trace-2"));
	}

}
