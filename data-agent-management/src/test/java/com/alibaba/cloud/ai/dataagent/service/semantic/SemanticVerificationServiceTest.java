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

import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject;
import com.alibaba.cloud.ai.dataagent.dto.semantic.SemanticObject.MetricRef;
import com.alibaba.cloud.ai.dataagent.service.llm.LlmService;
import com.alibaba.cloud.ai.dataagent.service.semantic.SemanticVerificationService.VerificationResult;
import com.alibaba.cloud.ai.dataagent.util.JsonParseUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link SemanticVerificationService} 单测：验证 Maker/Checker 分离的 Checker 侧行为。
 * <p>
 * 全程 mock {@link LlmService}，不依赖真实 LLM。重点验证：
 * <ul>
 * <li>正常路径：LLM 返回合法 JSON → 解析为 {@code valid=true} 的结果。</li>
 * <li>fail-open：LLM 抛异常或返回空内容时，不向调用方抛异常，而是返回 {@code valid=false}。</li>
 * <li>解析失败：{@link JsonParseUtil} 返回 null 时，结果为 {@code valid=false}。</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Mock 策略</b>：{@code callUser} 返回 {@code Flux<ChatResponse>}，而 {@code toStringFlux} 是
 * {@link LlmService} 接口的 default 方法。测试通过 stub {@code llmService.toStringFlux(anyFlux)}
 * 直接返回 预期字符串流，避免构造 {@link ChatResponse} 实例（Spring AI 的 ChatResponse 构造链较重）。
 * </p>
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SemanticVerificationServiceTest {

	@Mock
	private LlmService llmService;

	@Mock
	private JsonParseUtil jsonParseUtil;

	/** ObjectMapper 用真实实例（@InjectMocks 对 final 字段注入 null，需在 setUp 手动覆盖）。 */
	@InjectMocks
	private SemanticVerificationService service;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private SemanticObject semanticObject;

	@BeforeEach
	void setUp() {
		// @AllArgsConstructor 构造顺序：LlmService, JsonParseUtil, ObjectMapper
		service = new SemanticVerificationService(llmService, jsonParseUtil, objectMapper);

		MetricRef metricRef = new MetricRef();
		metricRef.setMetricCode("order_amount");
		metricRef.setVerCode(null);
		semanticObject = new SemanticObject();
		semanticObject.setMetrics(List.of(metricRef));
	}

	/**
	 * 正常路径：LLM 返回 {@code {"valid":true,"reason":"指标匹配"}}，JsonParseUtil 解析为 ok()。
	 * <p>
	 * 断言 verify() 返回 valid=true。同时验证 callUser 被调用（说明 prompt 已正常构建并发出）。
	 * </p>
	 */
	@Test
	void verify_returnsValidWhenLlmSaysValid() {
		String llmJson = """
				{"valid":true,"reason":"指标匹配"}""";
		given(llmService.callUser(anyString())).willReturn(Flux.empty());
		// toStringFlux 是 default 方法，直接 stub 返回预期字符串流，绕过 ChatResponse 构造
		given(llmService.toStringFlux(any())).willReturn(Flux.just(llmJson));
		given(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).willReturn(VerificationResult.ok());

		VerificationResult result = service.verify(semanticObject, "上月订单金额");

		assertThat(result).isNotNull();
		assertThat(result.valid()).isTrue();
		assertThat(result.reason()).isEqualTo("语义对象合理");
		verify(llmService).callUser(anyString());
	}

	/**
	 * fail-open：callUser 抛 RuntimeException（模拟网关超时/模型不可用）。
	 * <p>
	 * 断言 verify() 不抛异常、返回非 null 且 valid=false。这是 fail-open 监督控制最有价值的断言 —— 验证器自身故障不能阻塞主流程。
	 * </p>
	 */
	@Test
	void verify_failOpenWhenLlmThrows() {
		given(llmService.callUser(anyString())).willThrow(new RuntimeException("model gateway timeout"));

		VerificationResult result = service.verify(semanticObject, "上月订单金额");

		assertThat(result).isNotNull();
		assertThat(result.valid()).isFalse();
		// fail-open 不应继续走解析分支
		verify(jsonParseUtil, never()).tryConvertToObject(anyString(), any(Class.class));
	}

	/**
	 * 空内容路径：callUser 返回空 Flux，toStringFlux 聚合后为空白。
	 * <p>
	 * 断言 verify() 返回 valid=false（"LLM 返回空内容" 分支），同样 fail-open 不抛异常。
	 * </p>
	 */
	@Test
	void verify_failOpenWhenLlmReturnsEmpty() {
		given(llmService.callUser(anyString())).willReturn(Flux.empty());
		given(llmService.toStringFlux(any())).willReturn(Flux.empty());

		VerificationResult result = service.verify(semanticObject, "上月订单金额");

		assertThat(result).isNotNull();
		assertThat(result.valid()).isFalse();
		verify(jsonParseUtil, never()).tryConvertToObject(anyString(), any(Class.class));
	}

	/**
	 * 解析失败路径：LLM 返回了非空文本，但 JsonParseUtil 返回 null（无法解析）。
	 * <p>
	 * 断言 verify() 返回 valid=false，reason 含"解析失败"。
	 * </p>
	 */
	@Test
	void verify_returnsInvalidWhenParseReturnsNull() {
		String llmGarbage = "我无法判断";
		given(llmService.callUser(anyString())).willReturn(Flux.empty());
		given(llmService.toStringFlux(any())).willReturn(Flux.just(llmGarbage));
		given(jsonParseUtil.tryConvertToObject(anyString(), any(Class.class))).willReturn(null);

		VerificationResult result = service.verify(semanticObject, "上月订单金额");

		assertThat(result).isNotNull();
		assertThat(result.valid()).isFalse();
		assertThat(result.reason()).contains("解析失败");
	}

	/**
	 * null 入参守卫：semanticObject 为 null 时直接返回 fail，不调用 LLM。
	 */
	@Test
	void verify_returnsFailWhenSemanticObjectNull() {
		VerificationResult result = service.verify(null, "上月订单金额");

		assertThat(result).isNotNull();
		assertThat(result.valid()).isFalse();
		verify(llmService, never()).callUser(anyString());
	}

}
