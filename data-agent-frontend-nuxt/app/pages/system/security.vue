<!--
  Copyright 2026 the original author or authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!-- 安全状态：凭据加密（AES-256-GCM）启停状态 + 覆盖范围 + 启用指引。把 R12 加密能力暴露成运维可见性。 -->
<template>
	<div class="page-shell pa-4 pa-md-5">
		<KnowledgePageHeader title="安全状态" description="凭据静态加密（AES-256-GCM）状态与覆盖范围">
			<template #actions>
				<v-btn variant="text" size="small" prepend-icon="mdi-refresh" :loading="loading" @click="load">
					刷新
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-alert v-if="error" type="error" variant="tonal" class="mt-4" :text="error" />

		<v-row class="mt-2">
			<v-col cols="12" md="6">
				<v-card variant="flat" border class="pa-4 h-100">
					<div class="d-flex align-center mb-3">
						<v-icon :color="enabled ? 'success' : 'warning'" size="32" class="mr-2">
							{{ enabled ? 'mdi-shield-check' : 'mdi-shield-alert' }}
						</v-icon>
						<div>
							<div class="text-h6">{{ enabled ? '凭据已加密' : '凭据未加密（明文存储）' }}</div>
							<div class="text-caption text-medium-emphasis">
								主密钥：{{ status?.keyConfigured === 'configured' ? '已配置' : '未配置' }}
							</div>
						</div>
					</div>
					<v-divider class="my-3" />
					<v-row dense>
						<v-col cols="6">
							<div class="text-caption text-medium-emphasis">算法</div>
							<div class="text-body-2 font-weight-medium">{{ status?.algorithm }}</div>
						</v-col>
						<v-col cols="6">
							<div class="text-caption text-medium-emphasis">密文前缀</div>
							<div class="text-body-2 font-weight-medium">{{ status?.envelopePrefix }}</div>
						</v-col>
					</v-row>
				</v-card>
			</v-col>

			<v-col cols="12" md="6">
				<v-card variant="flat" border class="pa-4 h-100">
					<div class="text-subtitle-2 mb-2">
						<v-icon size="small" class="mr-1">mdi-database-lock</v-icon>
						加密覆盖范围
					</div>
					<v-chip v-for="c in coverageItems" :key="c" size="small" :color="enabled ? 'success' : 'default'"
						variant="tonal" class="ma-1">
						<v-icon start size="small">{{ enabled ? 'mdi-lock' : 'mdi-lock-open' }}</v-icon>
						{{ c }}
					</v-chip>
				</v-card>
			</v-col>
		</v-row>

		<v-card v-if="!enabled" variant="tonal" color="warning" class="mt-4 pa-4">
			<div class="text-subtitle-2 mb-2">
				<v-icon size="small" class="mr-1">mdi-key</v-icon>
				启用加密
			</div>
			<div class="text-body-2 mb-2">
				凭据加密为 opt-in，需注入 32 字节 AES 主密钥后重启生效。已存的明文行无需迁移（启用后新写入即加密，旧明文行原样读出）。
			</div>
			<v-code tag="pre" class="text-caption">export SYSTEM_AES_KEY=&lt;32-byte-key&gt;
# 或 application.yml: spring.ai.alibaba.data-agent.crypto.aes-key: &lt;32-byte-key&gt;</v-code>
		</v-card>
	</div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import securityService, { type CryptoStatus } from '~/services/security/index';

definePageMeta({ layout: 'default' });

const status = ref<CryptoStatus | null>(null);
const loading = ref(false);
const error = ref('');

const enabled = computed(() => status.value?.enabled ?? false);
const coverageItems = computed(() =>
	(status.value?.coverage ?? '').split('/').map(s => s.trim()).filter(Boolean),
);

async function load() {
	loading.value = true;
	error.value = '';
	try {
		status.value = await securityService.cryptoStatus();
	}
	catch (e: any) {
		error.value = e?.response?.data?.message || e?.message || '请求失败';
	}
	finally {
		loading.value = false;
	}
}

onMounted(load);
</script>
