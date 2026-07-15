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
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import com.alibaba.cloud.ai.dataagent.entity.SqlExample;
import com.alibaba.cloud.ai.dataagent.mapper.SqlExampleMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link SqlExampleRecallHelper} 单元测试，覆盖格式化、上限截断、空结果、null 入参与异常降级五个场景。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SqlExampleRecallHelperTest {

	@Mock
	private SqlExampleMapper sqlExampleMapper;

	@InjectMocks
	private SqlExampleRecallHelper recallHelper;

	@Test
	void recallFewShot_returnsFormattedString() {
		// given
		Integer agentId = 1;
		given(sqlExampleMapper.selectReviewedByAgentId(agentId)).willReturn(List.of(
				SqlExample.builder()
					.question("各省份的销售额排名")
					.sqlText("SELECT province, SUM(amount) FROM orders GROUP BY province")
					.build(),
				SqlExample.builder()
					.question("最近7天新增用户数")
					.sqlText("SELECT COUNT(*) FROM users WHERE created_at >= NOW() - INTERVAL 7 DAY")
					.build()));

		// when
		String result = recallHelper.recallFewShot(agentId);

		// then
		assertThat(result).startsWith("[SQL 样例 few-shot]");
		assertThat(result).contains("问题:", "SQL:");
		assertThat(result).contains("各省份的销售额排名", "SELECT province, SUM(amount) FROM orders GROUP BY province");
		assertThat(result).contains("最近7天新增用户数",
				"SELECT COUNT(*) FROM users WHERE created_at >= NOW() - INTERVAL 7 DAY");
	}

	@Test
	void recallFewShot_respectsMaxLimit() {
		// given：MAX_FEW_SHOT_EXAMPLES = 5，mock 返回 10 条，应只取前 5 条
		Integer agentId = 2;
		List<SqlExample> many = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			many.add(SqlExample.builder().question("Q" + i).sqlText("SELECT " + i).build());
		}
		given(sqlExampleMapper.selectReviewedByAgentId(agentId)).willReturn(many);

		// when
		String result = recallHelper.recallFewShot(agentId);

		// then：只包含前 5 条（Q0~Q4），不含 Q5~Q9
		assertThat(result).contains("Q0", "Q1", "Q2", "Q3", "Q4");
		assertThat(result).doesNotContain("Q5", "Q6", "Q7", "Q8", "Q9");
		// "问题:" 出现 5 次
		assertThat(countOccurrences(result, "问题:")).isEqualTo(5);
	}

	@Test
	void recallFewShot_emptyReturnsEmpty() {
		// given
		Integer agentId = 3;
		given(sqlExampleMapper.selectReviewedByAgentId(agentId)).willReturn(List.of());

		// when
		String result = recallHelper.recallFewShot(agentId);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	void recallFewShot_nullAgentReturnsEmpty() {
		// when
		String result = recallHelper.recallFewShot(null);

		// then：返回空串且不触达 mapper
		assertThat(result).isEmpty();
		then(sqlExampleMapper).should(never()).selectReviewedByAgentId(null);
		then(sqlExampleMapper).shouldHaveNoInteractions();
	}

	@Test
	void recallFewShot_mapperThrowsReturnsEmpty() {
		// given：mapper 抛异常，验证降级为空串而非向上抛出
		Integer agentId = 4;
		given(sqlExampleMapper.selectReviewedByAgentId(agentId)).willThrow(new RuntimeException("DB down"));

		// when
		String result = recallHelper.recallFewShot(agentId);

		// then
		assertThat(result).isEmpty();
	}

	private static int countOccurrences(String text, String substring) {
		int count = 0;
		int idx = 0;
		while ((idx = text.indexOf(substring, idx)) != -1) {
			count++;
			idx += substring.length();
		}
		return count;
	}

}
