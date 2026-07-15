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

import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link JoinRouter} 单元测试，覆盖直连、2 跳桥接、无路径与 from==to 四类场景。
 *
 * @author dataagent
 */
class JoinRouterTest {

	@Test
	void findJoinPath_directConnectionReturnsEmptyBridgeList() {
		// given：orders 直接连 users（1 跳），无桥接表
		List<LogicalRelation> relations = List.of(relation("orders", "user_id", "users", "id"),
				relation("orders", "product_id", "products", "id"));

		// when：1 跳直达，路径上无中间表
		List<String> path = JoinRouter.findJoinPath("orders", "users", relations);

		// then
		assertThat(path).isEmpty();
	}

	@Test
	void findJoinPath_twoHopBridgeReturnsSingleBridgeTable() {
		// given：orders → users → regions，需经过 users 桥接
		List<LogicalRelation> relations = List.of(relation("orders", "user_id", "users", "id"),
				relation("users", "region_id", "regions", "id"), relation("products", "cat_id", "categories", "id"));

		// when：从 orders 到 regions 需经过 users
		List<String> path = JoinRouter.findJoinPath("orders", "regions", relations);

		// then
		assertThat(path).containsExactly("users");
	}

	@Test
	void findJoinPath_noPathReturnsEmpty() {
		// given：orders 与孤立的 logs 无关联
		List<LogicalRelation> relations = List.of(relation("orders", "user_id", "users", "id"),
				relation("users", "region_id", "regions", "id"));

		// when
		List<String> path = JoinRouter.findJoinPath("orders", "logs", relations);

		// then
		assertThat(path).isEmpty();
	}

	@Test
	void findJoinPath_threeHopExceedsMaxReturnsEmpty() {
		// given：a→b→c→d 共 3 跳，超出 MAX_HOPS=2，应返回空
		List<LogicalRelation> relations = List.of(relation("a", "b_id", "b", "id"), relation("b", "c_id", "c", "id"),
				relation("c", "d_id", "d", "id"));

		// when
		List<String> path = JoinRouter.findJoinPath("a", "d", relations);

		// then：3 跳超出上限，无路径
		assertThat(path).isEmpty();
	}

	@Test
	void findJoinPath_fromEqualsToReturnsEmpty() {
		// given
		List<LogicalRelation> relations = List.of(relation("orders", "user_id", "users", "id"));

		// when
		List<String> path = JoinRouter.findJoinPath("orders", "orders", relations);

		// then
		assertThat(path).isEmpty();
	}

	@Test
	void findJoinPath_nullOrEmptyRelationsReturnsEmpty() {
		assertThat(JoinRouter.findJoinPath("orders", "users", null)).isEmpty();
		assertThat(JoinRouter.findJoinPath("orders", "users", List.of())).isEmpty();
	}

	@Test
	void findJoinPath_nullTableReturnsEmpty() {
		List<LogicalRelation> relations = List.of(relation("orders", "user_id", "users", "id"));
		assertThat(JoinRouter.findJoinPath(null, "users", relations)).isEmpty();
		assertThat(JoinRouter.findJoinPath("orders", null, relations)).isEmpty();
	}

	private LogicalRelation relation(String sourceTable, String sourceColumn, String targetTable, String targetColumn) {
		return LogicalRelation.builder()
			.sourceTableName(sourceTable)
			.sourceColumnName(sourceColumn)
			.targetTableName(targetTable)
			.targetColumnName(targetColumn)
			.build();
	}

}
