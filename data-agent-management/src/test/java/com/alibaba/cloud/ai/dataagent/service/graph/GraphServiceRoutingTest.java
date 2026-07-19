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
package com.alibaba.cloud.ai.dataagent.service.graph;

import com.alibaba.cloud.ai.dataagent.entity.Agent;
import com.alibaba.cloud.ai.dataagent.mapper.AgentMapper;
import com.alibaba.cloud.ai.dataagent.service.graph.Context.MultiTurnContextManager;
import com.alibaba.cloud.ai.dataagent.service.langfuse.LangfuseService;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.StateGraph;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.alibaba.cloud.ai.dataagent.constant.Constant.NL2SQL_GRAPH_NAME;
import static com.alibaba.cloud.ai.dataagent.constant.Constant.NL2SQL_SEMANTIC_GRAPH_NAME;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link GraphServiceImpl#resolveGraph(String)} 路由测试（v0.2 语义层闭环验证，η₅ fail-open）。
 * <p>
 * 断言按 Agent 的 {@code workflow_mode} 正确选图，且所有异常/缺省路径一律降级到默认 NL2SQL 图， 保证既有用户体验零回归。
 * </p>
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GraphServiceRoutingTest {

	@Mock
	private AgentMapper agentMapper;

	@Mock
	private MultiTurnContextManager multiTurnContextManager;

	@Mock
	private LangfuseService langfuseReporter;

	private GraphServiceImpl graphService;

	private CompiledGraph defaultGraph;

	private CompiledGraph semanticGraph;

	private ExecutorService executor;

	@BeforeEach
	void setUp() throws Exception {
		executor = Executors.newSingleThreadExecutor();
		defaultGraph = mock(CompiledGraph.class);
		semanticGraph = mock(CompiledGraph.class);
		StateGraph defaultStateGraph = mock(StateGraph.class);
		StateGraph semanticStateGraph = mock(StateGraph.class);
		when(defaultStateGraph.compile(any())).thenReturn(defaultGraph);
		when(semanticStateGraph.compile(any())).thenReturn(semanticGraph);

		graphService = new GraphServiceImpl(defaultStateGraph, semanticStateGraph, agentMapper, executor,
				multiTurnContextManager, langfuseReporter);
	}

	@Test
	void resolveGraph_semanticAgent_returnsSemanticGraph() {
		Agent semanticAgent = Agent.builder().id(7L).workflowMode("semantic").build();
		when(agentMapper.findById(7L)).thenReturn(semanticAgent);

		CompiledGraph resolved = graphService.resolveGraph("7");

		assertSame(semanticGraph, resolved, "semantic 模式 Agent 必须路由到语义层图");
	}

	@Test
	void resolveGraph_defaultAgent_returnsDefaultGraph() {
		Agent nl2sqlAgent = Agent.builder().id(1L).workflowMode("nl2sql").build();
		when(agentMapper.findById(1L)).thenReturn(nl2sqlAgent);

		CompiledGraph resolved = graphService.resolveGraph("1");

		assertSame(defaultGraph, resolved, "nl2sql 模式 Agent 必须路由到默认图");
	}

	@Test
	void resolveGraph_nullWorkflowMode_returnsDefaultGraph() {
		// 旧数据迁移场景：workflow_mode 为 null（DB DEFAULT 兜底前）
		Agent legacyAgent = Agent.builder().id(2L).workflowMode(null).build();
		when(agentMapper.findById(2L)).thenReturn(legacyAgent);

		CompiledGraph resolved = graphService.resolveGraph("2");

		assertSame(defaultGraph, resolved, "workflow_mode 为 null 时必须 fail-open 到默认图");
	}

	@Test
	void resolveGraph_unknownWorkflowMode_returnsDefaultGraph() {
		Agent weirdAgent = Agent.builder().id(3L).workflowMode("something-else").build();
		when(agentMapper.findById(3L)).thenReturn(weirdAgent);

		CompiledGraph resolved = graphService.resolveGraph("3");

		assertSame(defaultGraph, resolved, "未知 workflow_mode 必须降级到默认图");
	}

	@Test
	void resolveGraph_agentNotFound_returnsDefaultGraph() {
		when(agentMapper.findById(99L)).thenReturn(null);

		CompiledGraph resolved = graphService.resolveGraph("99");

		assertSame(defaultGraph, resolved, "Agent 不存在时必须降级到默认图");
	}

	@Test
	void resolveGraph_nonNumericAgentId_returnsDefaultGraph() {
		// AgentMapper 不应被调用（NumberFormatException 在 Long.valueOf 阶段被捕获）
		CompiledGraph resolved = graphService.resolveGraph("not-a-number");

		assertSame(defaultGraph, resolved, "非数字 agentId 必须降级到默认图");
	}

	@Test
	void resolveGraph_blankAgentId_returnsDefaultGraph() {
		CompiledGraph resolved = graphService.resolveGraph("");

		assertSame(defaultGraph, resolved, "空 agentId 必须降级到默认图");
	}

	@Test
	void resolveGraph_mapperThrows_returnsDefaultGraph() {
		// DB 异常场景：fail-open，不阻断请求
		when(agentMapper.findById(5L)).thenThrow(new RuntimeException("DB down"));

		CompiledGraph resolved = graphService.resolveGraph("5");

		assertSame(defaultGraph, resolved, "AgentMapper 异常时必须 fail-open 到默认图");
	}

	@Test
	void bothGraphsAreCompiledAndCached() {
		Map<String, CompiledGraph> view = graphService.getCompiledGraphsView();

		assertSame(defaultGraph, view.get(NL2SQL_GRAPH_NAME), "默认图必须已编译并缓存");
		assertSame(semanticGraph, view.get(NL2SQL_SEMANTIC_GRAPH_NAME), "语义层图必须已编译并缓存");
	}

}
