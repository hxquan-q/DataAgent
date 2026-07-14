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
package com.alibaba.cloud.ai.dataagent.aop;

import com.alibaba.cloud.ai.graph.OverAllState;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

/**
 * 工作流节点入口日志切面。
 * <p>
 * 通过 AOP 拦截所有工作流节点的 {@code apply} 方法，在方法执行前记录节点入口日志，
 * 便于调试和追踪工作流执行流程。
 */
@Aspect
@Component
@Slf4j
public class NodeEntryLoggingAspect {

	/**
	 * 定义切点：匹配工作流 node 包下所有接收 {@link OverAllState} 参数的 {@code apply} 方法。
	 */
	@Pointcut("execution(* com.alibaba.cloud.ai.dataagent.workflow.node..*.apply(com.alibaba.cloud.ai.graph.OverAllState))")
	public void nodeEntry() {
	}

	/**
	 * 在节点 apply 方法执行前记录入口日志。
	 * @param joinPoint 连接点，包含目标类和方法参数信息
	 */
	@Before("nodeEntry()")
	public void logNodeEntry(JoinPoint joinPoint) {
		String className = joinPoint.getTarget().getClass().getSimpleName();
		log.info("Entering {} node", className);

		// 获取方法参数并打印状态信息
		Object[] args = joinPoint.getArgs();
		if (args != null && args.length > 0 && args[0] instanceof OverAllState state) {
			log.debug("State: {}", state);
		}
	}

}
