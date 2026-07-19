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
package com.alibaba.cloud.ai.dataagent.connector.pool;

import java.util.Objects;

/**
 * 数据库连接获取的重试策略，定义最大重试次数和退避等待行为。
 */
public final class ConnectionRetryPolicy {

	/** 基础退避延迟（毫秒），实际延迟为基础延迟乘以失败次数 */
	private static final long BASE_DELAY_MILLIS = 1_000L;

	/** 最大重试次数 */
	private final int maxAttempts;

	/** 线程睡眠器，用于退避等待 */
	private final Sleeper sleeper;

	/**
	 * 构造重试策略。
	 * @param maxAttempts 最大重试次数（至少为 1）
	 * @param sleeper 线程睡眠器
	 */
	public ConnectionRetryPolicy(int maxAttempts, Sleeper sleeper) {
		if (maxAttempts < 1) {
			throw new IllegalArgumentException("maxAttempts must be at least 1");
		}
		this.maxAttempts = maxAttempts;
		this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
	}

	/**
	 * 创建默认的重试策略（最大重试 3 次，使用 Thread.sleep 退避）。
	 * @return 默认重试策略实例
	 */
	public static ConnectionRetryPolicy defaults() {
		return new ConnectionRetryPolicy(3, Thread::sleep);
	}

	/**
	 * 获取最大重试次数。
	 * @return 最大重试次数
	 */
	public int maxAttempts() {
		return maxAttempts;
	}

	/**
	 * 在连接获取失败后执行退避等待，等待时间随失败次数线性递增。
	 * @param failedAttempt 已失败的尝试次数
	 * @throws InterruptedException 如果线程在等待期间被中断
	 */
	public void pauseAfterFailure(int failedAttempt) throws InterruptedException {
		sleeper.sleep(Math.multiplyExact(BASE_DELAY_MILLIS, failedAttempt));
	}

	/**
	 * 线程睡眠器函数式接口，用于在退避等待时使线程休眠。
	 */
	@FunctionalInterface
	public interface Sleeper {

		/**
		 * 使线程休眠指定毫秒数。
		 * @param millis 休眠毫秒数
		 * @throws InterruptedException 如果线程在休眠期间被中断
		 */
		void sleep(long millis) throws InterruptedException;

	}

}
