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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.alibaba.cloud.ai.dataagent.entity.SqlExample;
import com.alibaba.cloud.ai.dataagent.mapper.SqlExampleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SqlExampleService} 单元测试，覆盖去重跳过、新增入库与哈希稳定性三个场景。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SqlExampleServiceTest {

	@Mock
	private SqlExampleMapper sqlExampleMapper;

	@InjectMocks
	private SqlExampleService sqlExampleService;

	@Test
	void ingest_已存在则跳过且不插入() {
		// given
		Integer agentId = 1;
		String sql = "SELECT * FROM users";
		SqlExample existed = SqlExample.builder().id(10L).sqlHash("any").build();
		given(sqlExampleMapper.selectByHash(any(), any())).willReturn(existed);

		// when
		boolean result = sqlExampleService.ingest("查询用户", sql, agentId, 2, "mysql");

		// then
		assertThat(result).isFalse();
		then(sqlExampleMapper).shouldHaveNoMoreInteractions();
	}

	@Test
	void ingest_新增则插入并填充默认字段() {
		// given
		Integer agentId = 1;
		Integer datasourceId = 2;
		String sql = "SELECT id, name FROM orders WHERE status = 1";
		given(sqlExampleMapper.selectByHash(any(), any())).willReturn(null);
		given(sqlExampleMapper.insert(any(SqlExample.class))).willReturn(1);

		// when
		boolean result = sqlExampleService.ingest("查询订单", sql, agentId, datasourceId, "mysql");

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<SqlExample> captor = ArgumentCaptor.forClass(SqlExample.class);
		then(sqlExampleMapper).should().insert(captor.capture());
		SqlExample saved = captor.getValue();
		assertThat(saved.getSqlHash()).isNotBlank();
		assertThat(saved.getSource()).isEqualTo("AUTO");
		assertThat(saved.getReviewed()).isZero();
		assertThat(saved.getAgentId()).isEqualTo(agentId);
		assertThat(saved.getDatasourceId()).isEqualTo(datasourceId);
		assertThat(saved.getSqlText()).isEqualTo(sql);
	}

	@Test
	void ingest_同SQL两次首次插入第二次跳过() {
		// given
		Integer agentId = 1;
		String sql = "  SELECT COUNT(*) FROM products  ";
		// 首次：不存在
		given(sqlExampleMapper.selectByHash(any(), any())).willReturn(null).willReturn(SqlExample.builder().build());
		given(sqlExampleMapper.insert(any(SqlExample.class))).willReturn(1);

		// when
		boolean first = sqlExampleService.ingest("统计商品", sql, agentId, 2, "mysql");
		boolean second = sqlExampleService.ingest("统计商品", sql.trim().toUpperCase(), agentId, 2, "mysql");

		// then
		assertThat(first).isTrue();
		assertThat(second).isFalse();
		then(sqlExampleMapper).should().insert(any(SqlExample.class));
	}

}
