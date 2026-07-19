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
package com.alibaba.cloud.ai.dataagent.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

/**
 * {@link MemoryWindowUtil} 单测：覆盖 retainLastN / selectToTrim / retainLastUserTurns 三个方法的
 * 正常路径、边界值（0、超出长度、null）与副本语义。
 *
 * @author xquan
 */
class MemoryWindowUtilTest {

	private static final Predicate<String> IS_USER = "user"::equals;

	// ==================== retainLastN ====================

	@Test
	void retainLastNReturnsTailSlice() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.retainLastN(items, 3)).containsExactly(3, 4, 5);
	}

	@Test
	void retainLastNWithZeroReturnsEmpty() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.retainLastN(items, 0)).isEmpty();
	}

	@Test
	void retainLastNWithNegativeReturnsEmpty() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.retainLastN(items, -2)).isEmpty();
	}

	@Test
	void retainLastNWithNLargerThanSizeReturnsFullCopy() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.retainLastN(items, 10)).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void retainLastNWithNullReturnsEmpty() {
		assertThat(MemoryWindowUtil.retainLastN(null, 3)).isEmpty();
	}

	@Test
	void retainLastNWithEmptyListReturnsEmpty() {
		assertThat(MemoryWindowUtil.retainLastN(new ArrayList<>(), 3)).isEmpty();
	}

	@Test
	void retainLastNReturnsDefensiveCopy() {
		List<Integer> items = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
		List<Integer> result = MemoryWindowUtil.retainLastN(items, 3);

		assertThat(result).containsExactly(3, 4, 5);
		// 修改原始列表不应影响已返回的结果
		items.add(6);
		assertThat(result).containsExactly(3, 4, 5);
	}

	// ==================== selectToTrim ====================

	@Test
	void selectToTrimReturnsLeadingPrefix() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.selectToTrim(items, 3)).containsExactly(1, 2);
	}

	@Test
	void selectToTrimWithNLargerThanSizeReturnsEmpty() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.selectToTrim(items, 10)).isEmpty();
	}

	@Test
	void selectToTrimWithZeroReturnsFullCopy() {
		List<Integer> items = Arrays.asList(1, 2, 3, 4, 5);

		assertThat(MemoryWindowUtil.selectToTrim(items, 0)).containsExactly(1, 2, 3, 4, 5);
	}

	@Test
	void selectToTrimWithNullReturnsEmpty() {
		assertThat(MemoryWindowUtil.selectToTrim(null, 3)).isEmpty();
	}

	@Test
	void selectToTrimWithEmptyListReturnsEmpty() {
		assertThat(MemoryWindowUtil.selectToTrim(new ArrayList<>(), 3)).isEmpty();
	}

	@Test
	void selectToTrimReturnsDefensiveCopy() {
		List<Integer> items = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
		List<Integer> result = MemoryWindowUtil.selectToTrim(items, 3);

		assertThat(result).containsExactly(1, 2);
		items.add(6);
		assertThat(result).containsExactly(1, 2);
	}

	// ==================== retainLastUserTurns ====================

	@Test
	void retainLastUserTurnsKeepsLastTwoUserTurns() {
		// [u1,a1,u2,a2,u3,a3] —— maxTurns=2 保留最近 2 个 user turn 及其后所有消息
		List<String> messages = Arrays.asList("user", "assistant", "user", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 2, IS_USER)).containsExactly("user", "assistant",
				"user", "assistant");
	}

	@Test
	void retainLastUserTurnsKeepsLastOneUserTurn() {
		List<String> messages = Arrays.asList("user", "assistant", "user", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 1, IS_USER)).containsExactly("user", "assistant");
	}

	@Test
	void retainLastUserTurnsWithZeroReturnsEmpty() {
		List<String> messages = Arrays.asList("user", "assistant", "user", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 0, IS_USER)).isEmpty();
	}

	@Test
	void retainLastUserTurnsWithNegativeReturnsEmpty() {
		List<String> messages = Arrays.asList("user", "assistant", "user", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, -1, IS_USER)).isEmpty();
	}

	@Test
	void retainLastUserTurnsWithNLargerThanActualReturnsFullCopy() {
		// 仅有 3 个 user turn，maxTurns=5 应返回全部
		List<String> messages = Arrays.asList("user", "assistant", "user", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 5, IS_USER)).containsExactly("user", "assistant",
				"user", "assistant", "user", "assistant");
	}

	@Test
	void retainLastUserTurnsWithNullReturnsEmpty() {
		assertThat(MemoryWindowUtil.retainLastUserTurns(null, 3, IS_USER)).isEmpty();
	}

	@Test
	void retainLastUserTurnsWithEmptyListReturnsEmpty() {
		assertThat(MemoryWindowUtil.retainLastUserTurns(new ArrayList<>(), 3, IS_USER)).isEmpty();
	}

	@Test
	void retainLastUserTurnsWithNullPredicateReturnsEmpty() {
		List<String> messages = Arrays.asList("user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 2, null)).isEmpty();
	}

	@Test
	void retainLastUserTurnsStartsWithAssistantKeepsHeadAssistant() {
		// 末尾 user 之前若紧跟 assistant 序列，保留起点后的 assistant 一并保留
		List<String> messages = Arrays.asList("assistant", "user", "assistant", "assistant", "user", "assistant");

		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 1, IS_USER)).containsExactly("user", "assistant");
		assertThat(MemoryWindowUtil.retainLastUserTurns(messages, 2, IS_USER)).containsExactly("user", "assistant",
				"assistant", "user", "assistant");
	}

	@Test
	void retainLastUserTurnsReturnsDefensiveCopy() {
		List<String> messages = new ArrayList<>(Arrays.asList("user", "assistant", "user", "assistant"));
		List<String> result = MemoryWindowUtil.retainLastUserTurns(messages, 1, IS_USER);

		assertThat(result).containsExactly("user", "assistant");
		messages.add("user");
		assertThat(result).containsExactly("user", "assistant");
	}

}
