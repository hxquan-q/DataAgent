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

import com.alibaba.cloud.ai.dataagent.entity.LogicalRelation;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 多表 JOIN 桥接路由器（NL2Semantic2SQL 跨表维度路由，η₂）。
 * <p>
 * 给定起点表 {@code fromTable} 与终点表 {@code toTable}，基于 {@code logical_relation} 配置构建无向邻接图， BFS
 * 搜索最短桥接路径（≤ {@value #MAX_HOPS} 跳），返回路径上的中间表名（不含起点与终点）。
 * </p>
 * <p>
 * 设计参考 QueryWeaver 的 {@code _find_connecting_tables}（allShortestPaths 找桥接表）， 此处简化为 BFS
 * 单条最短路径， 上限 {@value #MAX_HOPS} 跳以避免无界搜索。
 * </p>
 * <p>
 * 纯静态工具方法，无 Spring 依赖，便于单测与复用。
 * </p>
 *
 * @author dataagent
 */
public final class JoinRouter {

	/** 桥接路径最大跳数（含 from→bridge 与 bridge→to），上限 2 跳即「中间最多 1 张桥接表」。 */
	private static final int MAX_HOPS = 2;

	private JoinRouter() {
	}

	/**
	 * BFS 搜索 {@code fromTable} 到 {@code toTable} 的最短桥接路径。
	 * @param fromTable 起点表名（维度所在表）
	 * @param toTable 终点表名（指标 source_table）
	 * @param relations 逻辑外键关系（视为无向边）
	 * @return 路径上的中间桥接表名列表（不含 from/to 本身）；无路径、from==to 或入参非法时返回空列表
	 */
	public static List<String> findJoinPath(String fromTable, String toTable, List<LogicalRelation> relations) {
		if (fromTable == null || toTable == null || fromTable.equals(toTable)) {
			return Collections.emptyList();
		}
		if (relations == null || relations.isEmpty()) {
			return Collections.emptyList();
		}

		// 构建无向邻接表（表名 -> 邻接表名集合）
		Map<String, Set<String>> adjacency = new HashMap<>();
		for (LogicalRelation rel : relations) {
			String src = rel.getSourceTableName();
			String tgt = rel.getTargetTableName();
			if (src == null || tgt == null || src.equals(tgt)) {
				continue;
			}
			adjacency.computeIfAbsent(src, k -> new LinkedHashSet<>()).add(tgt);
			adjacency.computeIfAbsent(tgt, k -> new LinkedHashSet<>()).add(src);
		}
		if (!adjacency.containsKey(fromTable)) {
			return Collections.emptyList();
		}

		// BFS：记录每个节点的前驱，命中 toTable 后回溯
		Map<String, String> predecessor = new HashMap<>();
		Set<String> visited = new HashSet<>();
		Deque<String> queue = new ArrayDeque<>();
		queue.offer(fromTable);
		visited.add(fromTable);
		int hops = 0;

		while (!queue.isEmpty() && hops < MAX_HOPS) {
			int levelSize = queue.size();
			for (int i = 0; i < levelSize; i++) {
				String current = queue.poll();
				Objects.requireNonNull(current, "BFS 队列不应含 null");
				Set<String> neighbors = adjacency.getOrDefault(current, Collections.emptySet());
				for (String next : neighbors) {
					if (visited.contains(next)) {
						continue;
					}
					predecessor.put(next, current);
					if (toTable.equals(next)) {
						return reconstructPath(predecessor, fromTable, toTable);
					}
					visited.add(next);
					queue.offer(next);
				}
			}
			hops++;
		}
		return Collections.emptyList();
	}

	/**
	 * 回溯前驱链，返回桥接表列表（不含 from/to）。
	 * @param predecessor BFS 前驱映射
	 * @param fromTable 起点
	 * @param toTable 终点（命中点）
	 * @return 桥接表名列表（按 from→to 方向）
	 */
	private static List<String> reconstructPath(Map<String, String> predecessor, String fromTable, String toTable) {
		List<String> reversed = new ArrayList<>();
		String cursor = toTable;
		while (cursor != null && !cursor.equals(fromTable)) {
			reversed.add(cursor);
			cursor = predecessor.get(cursor);
		}
		// reversed 当前为 [toTable, ...bridge...]，去掉首元素 toTable，再反转为 from→to 方向的桥接序列
		if (reversed.isEmpty()) {
			return Collections.emptyList();
		}
		// reversed.get(0) == toTable，移除之
		reversed.remove(0);
		Collections.reverse(reversed);
		return reversed;
	}

}
