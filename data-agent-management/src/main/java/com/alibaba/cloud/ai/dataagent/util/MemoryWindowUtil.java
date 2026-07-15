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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * 多轮对话滑窗记忆管理工具类。
 *
 * <p>
 * 提供按条数与按用户轮次的两种滑窗裁剪策略，用于在构建 MultiTurnContextManager 时 对历史消息列表进行有界化处理，缓解多轮上下文膨胀问题（对应大
 * prompt 痛点）。
 * </p>
 *
 * <p>
 * 设计参考 GustoBot 的 <code>_select_messages_to_remove</code>：从列表末尾向前扫描，
 * 以"用户轮次"为切片单位保留最近若干轮及其后所有消息，旧的前缀整体移除。
 * </p>
 *
 * <p>
 * 纯静态逻辑、无 Spring 依赖；所有方法均为 null 安全且返回不可变或独立副本， 修改原始列表不会影响返回结果。
 * </p>
 */
public final class MemoryWindowUtil {

	private MemoryWindowUtil() {
	}

	/**
	 * 返回列表最后 n 条元素的副本。
	 *
	 * <p>
	 * 边界处理：
	 * </p>
	 * <ul>
	 * <li>{@code items == null} → 返回空列表；</li>
	 * <li>{@code n <= 0} → 返回空列表；</li>
	 * <li>{@code n >= size} → 返回全部元素的副本。</li>
	 * </ul>
	 *
	 * <p>
	 * 返回结果为独立副本，对原始列表的后续修改不会影响返回值。
	 * </p>
	 * @param items 原始列表，允许为 null
	 * @param n 需要保留的尾部元素数量
	 * @param <T> 元素类型
	 * @return 包含最后 n 条元素的新列表（只读副本）
	 */
	public static <T> List<T> retainLastN(List<T> items, int n) {
		if (items == null || items.isEmpty() || n <= 0) {
			return Collections.emptyList();
		}
		int size = items.size();
		if (n >= size) {
			return new ArrayList<>(items);
		}
		return new ArrayList<>(items.subList(size - n, size));
	}

	/**
	 * 返回需要被移除的前缀子列表（即前 {@code size - n} 条）。
	 *
	 * <p>
	 * 边界处理：
	 * </p>
	 * <ul>
	 * <li>{@code items == null} → 返回空列表；</li>
	 * <li>{@code n >= size} → 返回空列表（无需裁剪）；</li>
	 * <li>{@code n <= 0} → 返回全部元素的副本（全部可移除）。</li>
	 * </ul>
	 *
	 * <p>
	 * 返回结果为独立副本，对原始列表的后续修改不会影响返回值。
	 * </p>
	 * @param items 原始列表，允许为 null
	 * @param n 需要在尾部保留的元素数量
	 * @param <T> 元素类型
	 * @return 需要移除的前缀元素列表（只读副本）
	 */
	public static <T> List<T> selectToTrim(List<T> items, int n) {
		if (items == null || items.isEmpty()) {
			return Collections.emptyList();
		}
		int size = items.size();
		if (n >= size) {
			return Collections.emptyList();
		}
		if (n <= 0) {
			return new ArrayList<>(items);
		}
		return new ArrayList<>(items.subList(0, size - n));
	}

	/**
	 * 按用户轮次进行滑窗裁剪：保留最近 {@code maxTurns} 个用户轮次及其后的所有消息。
	 *
	 * <p>
	 * 算法：从列表末尾向前扫描，累计匹配 {@code isUserTurn} 的元素数量， 当累计数等于 {@code maxTurns} 时，记录当前位置作为保留起点，
	 * 返回该位置（含）到末尾的所有元素副本。
	 * </p>
	 *
	 * <p>
	 * 边界处理：
	 * </p>
	 * <ul>
	 * <li>{@code messages == null} → 返回空列表；</li>
	 * <li>{@code maxTurns <= 0} → 返回空列表；</li>
	 * <li>实际用户轮次不足 {@code maxTurns} → 返回全部元素的副本。</li>
	 * </ul>
	 *
	 * <p>
	 * 语义说明：本方法以"保留的 user turn 总数"作为 {@code maxTurns} 的含义， 即返回窗口包含最后 {@code maxTurns} 个
	 * user 消息及其后的全部 assistant 消息。 这与 GustoBot 中
	 * {@code humans_to_keep = MEMORY_TURN_LIMIT - 1}（排除当前进行中的轮次）
	 * 的语义略有差异；本方法更适用于"历史会话窗口构建"场景， 由调用方（MultiTurnContextManager）决定是否额外预留当前轮次。
	 * </p>
	 *
	 * <p>
	 * 返回结果为独立副本，对原始列表的后续修改不会影响返回值。
	 * </p>
	 * @param messages 原始消息列表，允许为 null
	 * @param maxTurns 需要保留的最大用户轮次数
	 * @param isUserTurn 判定某条消息是否为"用户轮次"的断言（例如 {@code m -> "user".equals(m.role)}）
	 * @param <T> 消息类型
	 * @return 包含最近 {@code maxTurns} 个用户轮次及其后续消息的新列表（只读副本）
	 */
	public static <T> List<T> retainLastUserTurns(List<T> messages, int maxTurns, Predicate<T> isUserTurn) {
		if (messages == null || messages.isEmpty() || maxTurns <= 0 || isUserTurn == null) {
			return Collections.emptyList();
		}
		int size = messages.size();
		int humansSeen = 0;
		int keepFrom = 0;
		for (int index = size - 1; index >= 0; index--) {
			T message = messages.get(index);
			if (isUserTurn.test(message)) {
				humansSeen++;
				if (humansSeen == maxTurns) {
					keepFrom = index;
					break;
				}
			}
		}
		// 未扫到 maxTurns 个 user turn → 保留全部
		if (humansSeen < maxTurns) {
			return new ArrayList<>(messages);
		}
		return new ArrayList<>(messages.subList(keepFrom, size));
	}

}
