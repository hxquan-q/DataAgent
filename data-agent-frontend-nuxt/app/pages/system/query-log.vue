/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

<template>
	<section class="page-shell">
		<KnowledgePageHeader
			title="查询证据链"
			subtitle="语义对象 → SQL → 结果 → 口径 证据链可回放"
		>
			<template #actions>
				<v-btn
					class="text-none"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadLogs"
				>
					刷新
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-3 pa-3">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-select
					v-model="filterAgentId"
					:items="agentItems"
					item-title="name"
					item-value="id"
					placeholder="全部智能体"
					prepend-inner-icon="mdi-robot"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					style="max-width: 220px"
					@update:model-value="onFilterChange"
				/>
				<v-select
					v-model="filterStatus"
					:items="statusOptions"
					item-title="label"
					item-value="value"
					placeholder="全部状态"
					prepend-inner-icon="mdi-flag-outline"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					style="max-width: 180px"
					@update:model-value="onFilterChange"
				/>
				<v-select
					v-model="filterFeedback"
					:items="feedbackOptions"
					item-title="label"
					item-value="value"
					placeholder="全部反馈"
					prepend-inner-icon="mdi-thumb-up-outline"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					style="max-width: 160px"
					@update:model-value="onFilterChange"
				/>
				<v-btn
					color="primary"
					prepend-icon="mdi-refresh"
					class="text-none px-6"
					elevation="0"
					:loading="loading"
					@click="loadLogs"
				>
					刷新
				</v-btn>
				<v-spacer />
				<v-chip
					v-if="total > 0"
					color="primary"
					variant="flat"
					class="font-weight-medium"
				>
					共 {{ total }} 条
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="logList"
				:page="pageNum"
				:items-per-page="pageSize"
				:server-items-length="total"
				item-value="id"
				hover
				:loading="loading"
				@update:page="onPageChange"
				@update:items-per-page="onPageSizeChange"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.createdTime="{ item }">
					{{ formatDateTime(item.createdTime) }}
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.userQuery="{ item }">
					<span
						class="d-inline-block text-truncate"
						style="max-width: 320px"
						:title="item.userQuery"
					>
						{{ item.userQuery || '—' }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-chip
						size="small"
						variant="flat"
						:color="statusColor(item.status)"
					>
						{{ statusLabel(item.status) }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.execTimeMs="{ item }">
					<span class="text-body-2">
						{{ item.execTimeMs != null ? `${item.execTimeMs} ms` : '—' }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.rowCount="{ item }">
					<span class="text-body-2">
						{{ item.rowCount != null ? item.rowCount : '—' }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.feedback="{ item }">
					<div class="d-flex ga-1">
						<v-btn
							size="small"
							variant="text"
							:color="item.feedback === 1 ? 'success' : 'grey-lighten-1'"
							icon="mdi-thumb-up-outline"
							:loading="feedbackIds.has(item.id)"
							@click="toggleFeedback(item, 1)"
						>
							<v-tooltip activator="parent" location="top">点赞</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							:color="item.feedback === 2 ? 'error' : 'grey-lighten-1'"
							icon="mdi-thumb-down-outline"
							:loading="feedbackIds.has(item.id)"
							@click="toggleFeedback(item, 2)"
						>
							<v-tooltip activator="parent" location="top">点踩</v-tooltip>
						</v-btn>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<v-btn
						size="small"
						variant="text"
						color="primary"
						icon="mdi-file-eye-outline"
						:loading="detailId === item.id"
						@click="openDetail(item)"
					>
						<v-tooltip activator="parent" location="top">查看详情</v-tooltip>
					</v-btn>
				</template>

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon
							icon="mdi-file-document-multiple-outline"
							size="64"
							color="primary"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">
							{{ !loading ? '暂无查询记录' : '加载中...' }}
						</p>
						<p class="text-body-2 text-disabled">
							证据链回放：语义对象 → SQL → 结果 → 口径版本
						</p>
					</div>
				</template>
			</v-data-table>
		</v-card>

		<!-- 详情弹窗 -->
		<v-dialog v-model="detailDialog" max-width="920" scrollable>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-5 pb-3">
					<v-icon
						icon="mdi-shield-link-variant-outline"
						color="primary"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-bold">查询证据链详情</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="detailDialog = false"
					/>
				</v-card-title>
				<v-divider />

				<v-card-text class="pa-5">
					<template v-if="detailLoading">
						<div class="d-flex justify-center align-center py-12">
							<v-progress-circular indeterminate color="primary" />
						</div>
					</template>
					<template v-else-if="detail">
						<!-- 原始查询 -->
						<div class="mb-6">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								用户查询
							</p>
							<v-card variant="tonal" color="primary" class="rounded pa-3">
								<span class="text-body-1">{{ detail.userQuery || '—' }}</span>
							</v-card>
						</div>

						<!-- 基本信息 -->
						<v-row class="mb-2">
							<v-col cols="6" md="3">
								<p class="text-caption text-grey-darken-1 mb-1">状态</p>
								<v-chip
									size="small"
									variant="flat"
									:color="statusColor(detail.status)"
								>
									{{ statusLabel(detail.status) }}
								</v-chip>
							</v-col>
							<v-col cols="6" md="3">
								<p class="text-caption text-grey-darken-1 mb-1">执行耗时</p>
								<span class="text-body-2">
									{{ detail.execTimeMs != null ? `${detail.execTimeMs} ms` : '—' }}
								</span>
							</v-col>
							<v-col cols="6" md="3">
								<p class="text-caption text-grey-darken-1 mb-1">结果行数</p>
								<span class="text-body-2">
									{{ detail.rowCount != null ? detail.rowCount : '—' }}
								</span>
							</v-col>
							<v-col cols="6" md="3">
								<p class="text-caption text-grey-darken-1 mb-1">创建时间</p>
								<span class="text-body-2">{{ formatDateTime(detail.createdTime) }}</span>
							</v-col>
						</v-row>

						<!-- Trace ID -->
						<div v-if="detail.traceId" class="mb-6">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								链路追踪 ID
							</p>
							<v-code tag="code" class="d-block pa-3 rounded text-body-2">
								{{ detail.traceId }}
							</v-code>
						</div>

						<!-- 语义对象 -->
						<div class="mb-6">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								语义对象（Semantic Object）
							</p>
							<pre
								v-if="prettySemanticObject"
								class="code-block"
							><code>{{ prettySemanticObject }}</code></pre>
							<span v-else class="text-body-2 text-disabled">无语义对象</span>
						</div>

						<!-- 生成的 SQL -->
						<div class="mb-6">
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								生成的 SQL
							</p>
							<pre
								v-if="detail.generatedSql"
								class="code-block"
							><code>{{ detail.generatedSql }}</code></pre>
							<span v-else class="text-body-2 text-disabled">无 SQL 记录</span>
						</div>

						<!-- 指标口径版本 -->
						<div>
							<p class="text-body-2 font-weight-medium text-grey-darken-2 mb-2">
								命中指标口径版本
							</p>
							<v-list
								v-if="detail.metricVersions && detail.metricVersions.length"
								density="compact"
								class="rounded-lg border"
							>
								<v-list-item
									v-for="(mv, idx) in detail.metricVersions"
									:key="mv.id ?? idx"
								>
									<v-list-item-title>
										<span class="font-weight-medium">
											{{ mv.metricName || mv.metricCode || '未命名指标' }}
										</span>
										<v-chip
											v-if="mv.version != null"
											size="x-small"
											class="ml-2"
											color="purple-lighten-4"
											variant="flat"
										>
											v{{ mv.version }}
										</v-chip>
									</v-list-item-title>
									<v-list-item-subtitle v-if="mv.sqlTemplate" class="text-wrap">
										<code class="text-caption">{{ mv.sqlTemplate }}</code>
									</v-list-item-subtitle>
									<v-list-item-subtitle v-if="mv.description" class="text-wrap pt-1">
										{{ mv.description }}
									</v-list-item-subtitle>
								</v-list-item>
							</v-list>
							<span v-else class="text-body-2 text-disabled">未命中指标口径版本</span>
						</div>
					</template>
					<template v-else>
						<div class="text-center text-medium-emphasis py-12">
							未找到详情数据
						</div>
					</template>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 d-flex justify-end">
					<v-btn
						variant="outlined"
						class="text-none px-6"
						@click="detailDialog = false"
					>
						关闭
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import queryLogService, {
	type QueryLog,
} from '~/services/queryLog/index';
import agentService from '~/services/agent/index';

const { $tip } = useNuxtApp();

// ——— 智能体下拉（过滤用） ———
const agentItems = ref<Array<{ id?: number; name?: string }>>([]);

// ——— 过滤条件 ———
const filterAgentId = ref<number | null>(null);
const filterStatus = ref<string | null>(null);
const filterFeedback = ref<number | null>(null);

const statusOptions = [
	{ label: '成功', value: 'SUCCESS' },
	{ label: '失败', value: 'FAIL' },
	{ label: '需澄清', value: 'CLARIFY' },
];
const feedbackOptions = [
	{ label: '已点赞', value: 1 },
	{ label: '已点踩', value: 2 },
];

// ——— 列表与分页 ———
const loading = ref(false);
const logList = ref<QueryLog[]>([]);
const pageNum = ref(1);
const pageSize = ref(20);
const total = ref(0);

// ——— 反馈中（避免重复点击） ———
const feedbackIds = ref<Set<number>>(new Set());

// ——— 详情 ———
const detailDialog = ref(false);
const detailLoading = ref(false);
const detailId = ref<number | null>(null);
const detail = ref<QueryLog | null>(null);

const headers = [
	{ title: '创建时间', key: 'createdTime', minWidth: '180px' },
	{ title: '用户查询', key: 'userQuery', minWidth: '260px' },
	{ title: '状态', key: 'status', width: '120px', sortable: false },
	{ title: '耗时', key: 'execTimeMs', width: '120px' },
	{ title: '行数', key: 'rowCount', width: '100px' },
	{ title: '反馈', key: 'feedback', width: '120px', sortable: false },
	{ title: '操作', key: 'actions', width: '100px', sortable: false },
];

const prettySemanticObject = computed(() => {
	const raw = detail.value?.semanticObject;
	if (!raw) return '';
	try {
		return JSON.stringify(JSON.parse(raw), null, 2);
	} catch {
		return raw;
	}
});

async function loadLogs() {
	loading.value = true;
	try {
		const result = await queryLogService.list({
			agentId: filterAgentId.value,
			status: filterStatus.value,
			feedback: filterFeedback.value,
			pageNum: pageNum.value,
			pageSize: pageSize.value,
		});
		logList.value = result.data;
		total.value = result.total;
	} catch {
		logList.value = [];
		total.value = 0;
		$tip('查询失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		loading.value = false;
	}
}

// 过滤条件变更：回到第一页重新加载
function onFilterChange() {
	pageNum.value = 1;
	loadLogs();
}

function onPageChange(page: number) {
	pageNum.value = page;
	loadLogs();
}

function onPageSizeChange(size: number) {
	pageSize.value = size;
	pageNum.value = 1;
	loadLogs();
}

async function toggleFeedback(item: QueryLog, target: 1 | 2) {
	if (!item.id) return;
	// 已是相同反馈则取消（置为 0）
	const next: 0 | 1 | 2 = item.feedback === target ? 0 : target;
	feedbackIds.value.add(item.id);
	try {
		const ok = await queryLogService.recordFeedback(item.id, next);
		if (ok) {
			item.feedback = next;
		} else {
			$tip('反馈记录失败', { color: 'error', icon: 'mdi-alert-circle' });
		}
	} catch {
		$tip('反馈记录失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		feedbackIds.value.delete(item.id);
	}
}

async function openDetail(item: QueryLog) {
	if (!item.id) return;
	detailId.value = item.id;
	detail.value = null;
	detailDialog.value = true;
	detailLoading.value = true;
	try {
		detail.value = await queryLogService.get(item.id);
	} catch {
		detail.value = null;
		$tip('详情加载失败', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		detailLoading.value = false;
		detailId.value = null;
	}
}

function statusColor(status?: string): string {
	switch ((status || '').toUpperCase()) {
		case 'SUCCESS':
			return 'success';
		case 'FAIL':
			return 'error';
		case 'CLARIFY':
			return 'warning';
		default:
			return 'grey';
	}
}

function statusLabel(status?: string): string {
	switch ((status || '').toUpperCase()) {
		case 'SUCCESS':
			return '成功';
		case 'FAIL':
			return '失败';
		case 'CLARIFY':
			return '需澄清';
		default:
			return status || '未知';
	}
}

function formatDateTime(dateTime?: string) {
	if (!dateTime) return '-';
	try {
		return new Date(dateTime).toLocaleString('zh-CN', {
			year: 'numeric',
			month: '2-digit',
			day: '2-digit',
			hour: '2-digit',
			minute: '2-digit',
			second: '2-digit',
			hour12: false,
		});
	} catch {
		return dateTime;
	}
}

onMounted(async () => {
	// 默认加载最近查询证据链 + 智能体下拉（无需预知会话 ID）
	try {
		agentItems.value = await agentService.list();
	} catch {
		agentItems.value = [];
	}
	await loadLogs();
});
</script>

<style scoped>
.code-block {
	background-color: var(--da-surface-soft);
	border: 1px solid var(--da-line-soft);
	border-radius: 8px;
	padding: 12px 16px;
	overflow-x: auto;
	font-family: 'JetBrains Mono', 'Fira Code', Consolas, monospace;
	font-size: 13px;
	line-height: 1.6;
	white-space: pre;
	color: var(--da-ink);
}

.code-block code {
	background: transparent;
	padding: 0;
}
</style>
