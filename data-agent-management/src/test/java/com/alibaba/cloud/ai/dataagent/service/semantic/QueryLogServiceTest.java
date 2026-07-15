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

import com.alibaba.cloud.ai.dataagent.entity.QueryLog;
import com.alibaba.cloud.ai.dataagent.mapper.QueryLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.verify;

/**
 * {@link QueryLogService} 单元测试：验证便捷构建字段映射与主键回填。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class QueryLogServiceTest {

	@Mock
	private QueryLogMapper queryLogMapper;

	@InjectMocks
	private QueryLogService queryLogService;

	@Captor
	private ArgumentCaptor<QueryLog> queryLogCaptor;

	@Test
	void logQuery_shouldBuildFieldsAndInvokeInsert() {
		// given：Mapper insert 返回受影响行数 1（不影响断言，仅满足 stubbing）
		given(queryLogMapper.insert(org.mockito.ArgumentMatchers.any(QueryLog.class))).willReturn(1);

		// when：便捷写入
		Long id = queryLogService.logQuery("sess-001", 1, 10, "本月订单金额", "{\"metric\":\"order_amount\"}",
				"SELECT SUM(amount) FROM orders", 120, 1, "SUCCESS", "trace-xyz");

		// then：insert 被调用一次，捕获到的实体字段映射正确
		verify(queryLogMapper).insert(queryLogCaptor.capture());
		QueryLog captured = queryLogCaptor.getValue();
		assertThat(captured.getSessionId()).isEqualTo("sess-001");
		assertThat(captured.getAgentId()).isEqualTo(1);
		assertThat(captured.getDatasourceId()).isEqualTo(10);
		assertThat(captured.getUserQuery()).isEqualTo("本月订单金额");
		assertThat(captured.getSemanticObject()).isEqualTo("{\"metric\":\"order_amount\"}");
		assertThat(captured.getGeneratedSql()).isEqualTo("SELECT SUM(amount) FROM orders");
		assertThat(captured.getExecTimeMs()).isEqualTo(120);
		assertThat(captured.getRowCount()).isEqualTo(1);
		assertThat(captured.getStatus()).isEqualTo("SUCCESS");
		assertThat(captured.getTraceId()).isEqualTo("trace-xyz");
		// 便捷方法未设置的可选字段应为 null
		assertThat(captured.getMetricVersions()).isNull();
		assertThat(captured.getFeedback()).isNull();
		// 未触发主键回填，返回值应为 null
		assertThat(id).isNull();
	}

	@Test
	void log_shouldReturnBackfilledId() {
		// given：insert 触发 useGeneratedKeys 回填，将 id 置为 42L
		willAnswer(invocation -> {
			QueryLog arg = invocation.getArgument(0);
			arg.setId(42L);
			return 1;
		}).given(queryLogMapper).insert(org.mockito.ArgumentMatchers.any(QueryLog.class));

		// when：通过 log 直接传入实体
		QueryLog queryLog = QueryLog.builder().sessionId("sess-002").userQuery("GMV").status("SUCCESS").build();
		Long id = queryLogService.log(queryLog);

		// then：返回回填后的主键 ID
		assertThat(id).isEqualTo(42L);
		assertThat(queryLog.getId()).isEqualTo(42L);
	}

	@Test
	void getBySessionId_shouldDelegateToMapper() {
		// given：Mapper 返回两条日志
		QueryLog log1 = QueryLog.builder().id(1L).sessionId("sess-1").userQuery("Q1").build();
		QueryLog log2 = QueryLog.builder().id(2L).sessionId("sess-1").userQuery("Q2").build();
		given(queryLogMapper.selectBySessionId("sess-1")).willReturn(List.of(log1, log2));

		// when
		List<QueryLog> result = queryLogService.getBySessionId("sess-1");

		// then：委托 mapper，结果直接透传
		verify(queryLogMapper).selectBySessionId("sess-1");
		assertThat(result).hasSize(2);
		assertThat(result.get(0).getId()).isEqualTo(1L);
		assertThat(result.get(1).getUserQuery()).isEqualTo("Q2");
	}

	@Test
	void getById_shouldDelegateToMapper() {
		// given
		QueryLog entity = QueryLog.builder().id(99L).sessionId("sess-x").userQuery("GMV").build();
		given(queryLogMapper.selectById(99L)).willReturn(entity);

		// when
		QueryLog result = queryLogService.getById(99L);

		// then
		verify(queryLogMapper).selectById(99L);
		assertThat(result).isSameAs(entity);
		assertThat(result.getId()).isEqualTo(99L);
	}

	@Test
	void recordFeedback_shouldInvokeUpdateFeedback() {
		// given
		given(queryLogMapper.updateFeedback(7L, 1)).willReturn(1);

		// when
		queryLogService.recordFeedback(7L, 1);

		// then：参数透传到 mapper
		verify(queryLogMapper).updateFeedback(7L, 1);
	}

}
