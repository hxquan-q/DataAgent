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

import com.alibaba.cloud.ai.dataagent.entity.SemanticAlias;
import com.alibaba.cloud.ai.dataagent.mapper.SemanticAliasMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;

/**
 * {@link SemanticAliasService} 单元测试。 重点验证：写操作后触发 {@code clearCache}、resolve 委托
 * loader、查询正确委托 mapper。
 *
 * @author dataagent
 */
@ExtendWith(MockitoExtension.class)
class SemanticAliasServiceTest {

	@Mock
	private SemanticAliasMapper semanticAliasMapper;

	@Mock
	private SemanticLayerLoader semanticLayerLoader;

	@InjectMocks
	private SemanticAliasService semanticAliasService;

	@Test
	void create_shouldInsertAndClearCache_andReturnId() {
		// given
		SemanticAlias alias = SemanticAlias.builder()
			.agentId(1)
			.aliasText("上月GMV")
			.targetType("METRIC")
			.targetCode("m_gmv")
			.build();
		given(semanticAliasMapper.insert(alias)).willAnswer(invocation -> {
			((SemanticAlias) invocation.getArgument(0)).setId(300L);
			return 1;
		});

		// when
		Long id = semanticAliasService.create(alias);

		// then
		assertThat(id).isEqualTo(300L);
		verify(semanticAliasMapper).insert(alias);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void update_shouldCallUpdateByIdAndClearCache() {
		// given
		SemanticAlias alias = SemanticAlias.builder().id(5L).targetCode("m_gmv_new").build();

		// when
		semanticAliasService.update(alias);

		// then
		verify(semanticAliasMapper).updateById(alias);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void delete_shouldCallDeleteByIdAndClearCache() {
		// given
		Long id = 9L;

		// when
		semanticAliasService.delete(id);

		// then
		verify(semanticAliasMapper).deleteById(id);
		verify(semanticLayerLoader).clearCache();
	}

	@Test
	void resolve_shouldDelegateToLoader() {
		// given
		Integer agentId = 1;
		String text = "上月GMV";
		SemanticAlias expected = SemanticAlias.builder().id(1L).agentId(agentId).aliasText(text).build();
		given(semanticLayerLoader.resolveAlias(agentId, text)).willReturn(expected);

		// when
		SemanticAlias result = semanticAliasService.resolve(agentId, text);

		// then
		assertThat(result).isSameAs(expected);
		verify(semanticLayerLoader).resolveAlias(agentId, text);
	}

	@Test
	void listByAgentId_shouldDelegateToMapper() {
		// given
		Integer agentId = 1;
		List<SemanticAlias> expected = List
			.of(SemanticAlias.builder().id(1L).agentId(agentId).aliasText("上月GMV").build());
		given(semanticAliasMapper.selectByAgentId(agentId)).willReturn(expected);

		// when
		List<SemanticAlias> result = semanticAliasService.listByAgentId(agentId);

		// then
		assertThat(result).isSameAs(expected);
		verify(semanticAliasMapper).selectByAgentId(agentId);
	}

}
