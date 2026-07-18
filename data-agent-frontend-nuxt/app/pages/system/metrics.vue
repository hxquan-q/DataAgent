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
			title="指标配置"
			subtitle="维护 NL2Semantic2SQL 指标定义，统一业务口径。"
		>
			<template #actions>
				<v-btn
					class="text-none"
					style="border-color: var(--da-line-soft)"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					@click="loadMetrics"
				>
					刷新
				</v-btn>
				<v-btn
					color="primary"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					@click="openCreateDialog"
				>
					添加指标
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-3 pa-3 da-toolbar">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-text-field
					v-model="searchKeyword"
					placeholder="请输入关键词搜索指标编码、指标名称"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="search-field"
					style="max-width: 380px"
					@keyup.enter="applySearch"
					@click:clear="clearSearch"
				/>
				<v-select
					v-model="selectedAgentId"
					:items="agentItems"
					item-title="name"
					item-value="id"
					placeholder="选择智能体"
					prepend-inner-icon="mdi-robot"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="agent-select"
					style="max-width: 220px"
					@update:model-value="loadMetrics"
				/>
				<v-spacer />
				<v-chip
					color="primary"
					variant="flat"
					class="font-weight-medium"
				>
				总数 {{ metricList.length }}
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="metricList"
				item-value="id"
				hover
				:loading="loading"
				:search="searchKeyword"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.metricName="{ item }">
					<div class="d-flex flex-column">
						<span class="font-weight-medium">{{ item.metricName }}</span>
						<span class="text-caption text-medium-emphasis">{{
							item.metricCode
						}}</span>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-vslot -->
				<template #item.aggInfo="{ item }">
					<span v-if="item.aggFunc || item.aggField">
						<span class="font-weight-medium">{{ item.aggFunc || '—' }}</span>
						<span class="text-medium-emphasis">
							({{ item.aggField || '—' }})
						</span>
					</span>
					<span v-else>—</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<div class="d-flex align-center ga-2">
						<v-chip
							:color="item.status === 1 ? 'success' : 'grey'"
							variant="flat"
							size="small"
							label
							class="font-weight-medium"
						>
							<v-icon
								start
								:icon="item.status === 1 ? 'mdi-check-circle' : 'mdi-pause-circle'"
								size="14"
							/>
							{{ item.status === 1 ? '启用' : '停用' }}
						</v-chip>
						<v-switch
							:model-value="item.status === 1"
							color="success"
							hide-details
							density="compact"
							:loading="switchingIds.has(item.id)"
							@update:model-value="(val) => toggleStatus(item, val ? 1 : 0)"
						/>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.createdTime="{ item }">
					{{ formatDateTime(item.createdTime || item.updatedTime) }}
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
						<v-btn
							size="small"
							variant="text"
							color="primary"
							icon="mdi-flask"
							@click="openAssembleDialog(item)"
						>
							<v-tooltip activator="parent" location="top">测试拼装</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="primary"
							icon="mdi-pencil"
							@click="editModel(item)"
						>
							<v-tooltip activator="parent" location="top">编辑</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="primary"
							icon="mdi-source-branch"
							@click="openVersions(item)"
						>
							<v-tooltip activator="parent" location="top">口径版本</v-tooltip>
						</v-btn>
						<v-btn
							size="small"
							variant="text"
							color="error"
							icon="mdi-delete"
							@click="deleteModel(item)"
						>
							<v-tooltip activator="parent" location="top">删除</v-tooltip>
						</v-btn>
					</div>
				</template>

				<template #no-data>
					<div class="d-flex flex-column align-center py-12">
						<v-icon
							icon="mdi-chart-line"
							size="64"
							color="primary"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">暂无指标定义</p>
						<p class="text-body-2 text-disabled mb-6">
							点击「添加指标」开始配置 NL2Semantic2SQL 指标
						</p>
						<v-btn
							color="primary"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加指标
						</v-btn>
					</div>
				</template>
			</v-data-table>
		</v-card>

		<v-dialog v-model="dialogVisible" max-width="820" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-5 pb-3">
					<v-icon
						:icon="isEdit ? 'mdi-pencil-circle' : 'mdi-plus-circle'"
						color="primary"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-medium dialog-title">{{
						isEdit ? '编辑指标' : '添加指标'
					}}</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="closeDialog"
					/>
				</v-card-title>
				<v-divider />

				<v-card-text class="pa-5">
					<v-form ref="formRef">
						<v-row>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2 d-flex align-center"
								>
									指标编码 <span class="text-error">*</span>
									<v-tooltip location="top" max-width="320">
										<template #activator="{ props }">
											<v-icon
												v-bind="props"
												size="16"
												class="ml-1"
												color="grey"
												icon="mdi-information-outline"
											/>
										</template>
										<span
											>全局唯一标识，建议使用小写蛇形命名（snake_case），如
											<code>order_amount</code>、<code>active_user_count</code>。SemanticParse
											会按编码收敛指标选择空间。</span
										>
									</v-tooltip>
								</p>
								<v-text-field
									v-model="modelForm.metricCode"
									placeholder="全局唯一，如 order_amount"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '指标编码不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2 d-flex align-center"
								>
									指标名称 <span class="text-error">*</span>
									<v-tooltip location="top" max-width="320">
										<template #activator="{ props }">
											<v-icon
												v-bind="props"
												size="16"
												class="ml-1"
												color="grey"
												icon="mdi-information-outline"
											/>
										</template>
										<span>指标中文显示名，用户提问时会据此匹配，建议清晰可读，如「订单金额」「活跃用户数」。</span>
									</v-tooltip>
								</p>
								<v-text-field
									v-model="modelForm.metricName"
									placeholder="请输入指标中文名"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '指标名称不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									智能体
								</p>
								<v-select
									v-model="modelForm.agentId"
									:items="agentItems"
									item-title="name"
									item-value="id"
									placeholder="选择关联智能体"
									variant="outlined"
									density="compact"
									clearable
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									数据源
								</p>
								<v-select
									v-model="modelForm.datasourceId"
									:items="datasourceItems"
									item-title="name"
									item-value="id"
									placeholder="选择数据源（多源路由）"
									variant="outlined"
									density="compact"
									clearable
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2 d-flex align-center"
								>
									来源表 <span class="text-error">*</span>
									<v-tooltip location="top" max-width="320">
										<template #activator="{ props }">
											<v-icon
												v-bind="props"
												size="16"
												class="ml-1"
												color="grey"
												icon="mdi-information-outline"
											/>
										</template>
										<span>受控拼装 SQL 的 <code>FROM</code> 目标表名。如 <code>orders</code>。</span>
									</v-tooltip>
								</p>
								<v-text-field
									v-model="modelForm.sourceTable"
									placeholder="SQL 拼装目标表，如 orders"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '来源表不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2 d-flex align-center"
								>
									聚合字段 <span class="text-error">*</span>
									<v-tooltip location="top" max-width="320">
										<template #activator="{ props }">
											<v-icon
												v-bind="props"
												size="16"
												class="ml-1"
												color="grey"
												icon="mdi-information-outline"
											/>
										</template>
										<span>聚合计算的目标字段名，如 <code>amount</code>。<code>COUNT</code> 可用 <code>*</code>。</span>
									</v-tooltip>
								</p>
								<v-text-field
									v-model="modelForm.aggField"
									placeholder="如 amount"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '聚合字段不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2 d-flex align-center"
								>
									聚合函数 <span class="text-error">*</span>
									<v-tooltip location="top" max-width="320">
										<template #activator="{ props }">
											<v-icon
												v-bind="props"
												size="16"
												class="ml-1"
												color="grey"
												icon="mdi-information-outline"
											/>
										</template>
										<span
											>可选项：
											<code>SUM</code>（求和）、<code>COUNT</code>（计数）、<code>AVG</code>（均值）、<code>MAX</code>（最大）、<code>MIN</code>（最小）。决定
											<code>SELECT &lt;aggFunc&gt;(&lt;aggField&gt;)</code> 的拼装方式。</span
										>
									</v-tooltip>
								</p>
								<v-select
									v-model="modelForm.aggFunc"
									:items="aggFuncOptions"
									placeholder="SUM / COUNT / AVG / MAX / MIN"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '聚合函数不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									默认时间字段
								</p>
								<v-text-field
									v-model="modelForm.defaultTimeField"
									placeholder="如 created_at"
									variant="outlined"
									density="compact"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									SQL 模板（可选）
								</p>
								<v-textarea
									v-model="modelForm.sqlTemplate"
									placeholder="复杂指标可填自定义 SQL 模板，留空则按聚合函数拼装"
									variant="outlined"
									density="compact"
									rows="2"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									指标说明
								</p>
								<v-textarea
									v-model="modelForm.description"
									placeholder="描述该指标的业务口径与计算逻辑"
									variant="outlined"
									density="compact"
									rows="2"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									状态
								</p>
								<v-switch
									v-model="modelForm.status"
									:true-value="1"
									:false-value="0"
									color="success"
									hide-details="auto"
									:label="modelForm.status === 1 ? '启用' : '禁用'"
								/>
							</v-col>
						</v-row>
					</v-form>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn variant="outlined" class="text-none px-6" @click="closeDialog"
						>取消</v-btn
					>
					<v-btn
						color="primary"
						class="text-none px-6"
						elevation="0"
						:loading="saveLoading"
						@click="saveModel"
					>
						{{ isEdit ? '保存更新' : '立即创建' }}
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<!-- 测试拼装 Dialog：展示指标如何拼装为受控 SQL -->
		<v-dialog v-model="assembleDialogVisible" max-width="780" persistent>
			<v-card rounded="lg">
				<v-card-title class="d-flex align-center pa-5 pb-3">
					<v-icon
						icon="mdi-flask"
						color="primary"
						class="mr-3"
						size="28"
					/>
					<span class="text-h6 font-weight-medium dialog-title">测试指标拼装</span>
					<v-spacer />
					<v-btn
						icon="mdi-close"
						variant="text"
						size="small"
						@click="assembleDialogVisible = false"
					/>
				</v-card-title>
				<v-divider />

				<v-card-text class="pa-5">
					<v-alert
						variant="tonal"
						color="primary"
						density="compact"
						class="mb-4"
					>
						<span class="text-body-2">
							指标「<strong>{{ assembleTarget?.metricName }}</strong>」将按下列模板受控拼装为 SQL
							（BuildSQLEngine），用户提问匹配该指标后即执行该拼装结果。
						</span>
					</v-alert>

					<v-text-field
						v-model="assembleQuery"
						placeholder="模拟用户提问，如「本月订单金额」"
						prepend-inner-icon="mdi-comment-question"
						variant="outlined"
						density="compact"
						hide-details
						class="mb-4"
					/>

					<div class="d-flex flex-wrap ga-2 mb-4">
						<v-chip size="small" variant="outlined" color="grey">
							aggFunc: <strong class="ml-1">{{ assembleTarget?.aggFunc || '—' }}</strong>
						</v-chip>
						<v-chip size="small" variant="outlined" color="grey">
							aggField: <strong class="ml-1">{{ assembleTarget?.aggField || '—' }}</strong>
						</v-chip>
						<v-chip size="small" variant="outlined" color="grey">
							sourceTable: <strong class="ml-1">{{ assembleTarget?.sourceTable || '—' }}</strong>
						</v-chip>
						<v-chip size="small" variant="outlined" color="grey">
							timeField: <strong class="ml-1">{{ assembleTarget?.defaultTimeField || '—' }}</strong>
						</v-chip>
					</div>

					<p class="text-body-2 font-weight-medium text-medium-emphasis mb-2">
						受控拼装 SQL（预览）
					</p>
					<pre
						class="assemble-sql-preview"
					>{{ assemblePreviewSql }}</pre>

					<v-alert
						v-if="assembleTarget?.sqlTemplate"
						variant="tonal"
						color="warning"
						density="compact"
						class="mt-4"
					>
						<span class="text-body-2">
							该指标配置了自定义 SQL 模板，拼装时优先使用模板（上方预览为默认聚合拼装结果，仅供参考）。
						</span>
					</v-alert>
				</v-card-text>

				<v-divider />
				<v-card-actions class="pa-4 d-flex justify-end ga-2">
					<v-btn
						variant="outlined"
						class="text-none px-6"
						@click="assembleDialogVisible = false"
						>关闭</v-btn
					>
					<v-btn
						color="primary"
						class="text-none px-6"
						elevation="0"
						prepend-icon="mdi-content-copy"
						@click="copyAssembleSql"
					>
						复制 SQL
					</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>
	</section>
</template>

<script setup lang="ts">
import metricService, {
	type Metric,
	type MetricDto,
} from '~/services/metric/index';
import { useCrudPage } from '~/composables/useCrudPage/index';
import agentService from '~/services/agent/index';
import datasourceService from '~/services/datasource/index';

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 聚合函数候选 ———
const aggFuncOptions = ['SUM', 'COUNT', 'AVG', 'MAX', 'MIN'];

// ——— 搜索 & 过滤 ———
const searchKeyword = ref('');
const selectedAgentId = ref<number | null>(null);

// ——— 关联实体（智能体 / 数据源） ———
const agentItems = ref<Array<{ id?: number; name?: string }>>([]);
const datasourceItems = ref<Array<{ id?: number; name?: string }>>([]);

const currentEditId = ref<number | null>(null);

// 切换状态中的指标 id 集合（避免污染 Metric 类型）
const switchingIds = ref<Set<number>>(new Set());

// ——— useCrudPage ———
const {
	loading,
	saveLoading,
	items: metricList,
	dialogVisible,
	isEdit,
	formRef,
	formData: modelForm,
	loadItems: loadMetrics,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<Metric, MetricDto, Metric>({
	loadFn: () =>
		metricService.list(
			selectedAgentId.value ?? undefined,
			searchKeyword.value || undefined,
		),
	createFn: (data) => metricService.create(data),
	updateFn: (_id, data) => metricService.update(data),
	deleteFn: (id) => metricService.delete(id),
	defaultFormFactory: () => ({
		metricCode: '',
		metricName: '',
		agentId: undefined,
		datasourceId: undefined,
		sourceTable: '',
		aggField: '',
		aggFunc: '',
		defaultTimeField: '',
		sqlTemplate: '',
		description: '',
		status: 1,
	}),
});

const headers = [
	{ title: '指标编码', key: 'metricCode', minWidth: '160px' },
	{ title: '指标名称', key: 'metricName', minWidth: '160px' },
	{ title: '来源表', key: 'sourceTable', minWidth: '140px' },
	{ title: '聚合', key: 'aggInfo', width: '180px', sortable: false },
	{ title: '默认时间字段', key: 'defaultTimeField', minWidth: '140px' },
	{ title: '状态', key: 'status', width: '120px', sortable: false },
	{ title: '创建时间', key: 'createdTime', width: '180px' },
	{ title: '操作', key: 'actions', width: '200px', sortable: false },
];

function applySearch() {
	loadMetrics();
}

function clearSearch() {
	searchKeyword.value = '';
	loadMetrics();
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

function openCreateDialog() {
	_openCreateDialog();
	if (selectedAgentId.value != null) {
		modelForm.value.agentId = selectedAgentId.value;
	}
}

function editModel(model: Metric) {
	currentEditId.value = model.id ?? null;
	openEditDialog(model);
}

function deleteModel(model: Metric) {
	if (!model.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除指标「${model.metricName}」吗？此操作不可恢复。`,
		confirmText: '确定删除',
		icon: 'mdi-delete',
		onConfirm: async () => {
			const ok = await deleteItem(model.id!);
			if (ok) {
				$tip('删除成功');
			} else {
				$tip('删除失败', { color: 'error', icon: 'mdi-alert-circle' });
			}
		},
	});
}

async function toggleStatus(model: Metric, status: number) {
	if (!model.id) return;
	switchingIds.value.add(model.id);
	try {
		const ok = await metricService.update({ ...model, status });
		if (ok) {
			model.status = status;
			$tip(`${status === 1 ? '启用' : '禁用'}成功`);
		} else {
			// 还原 UI
			model.status = status === 1 ? 0 : 1;
			$tip(`${status === 1 ? '启用' : '禁用'}失败`, {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
		}
	} catch {
		model.status = status === 1 ? 0 : 1;
		$tip(`${status === 1 ? '启用' : '禁用'}失败`, {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	} finally {
		switchingIds.value.delete(model.id);
	}
}

// ——— 测试拼装 Dialog ———
const assembleDialogVisible = ref(false);
const assembleTarget = ref<Metric | null>(null);
const assembleQuery = ref('');

/**
 * 受控拼装 SQL 预览：模拟 BuildSQLEngine 的拼装逻辑。
 * - 有自定义 sqlTemplate：直接展示模板
 * - 否则：SELECT <aggFunc>(<aggField>) FROM <sourceTable>
 *   （若 aggFunc=COUNT 且 aggField 为空，则用 COUNT(*)）
 *   若配置了 defaultTimeField，则附加占位的时间过滤（示例用占位符提示由 SemanticParse 填值）。
 */
const assemblePreviewSql = computed(() => {
	const m = assembleTarget.value;
	if (!m) return '-- 请先选择指标';
	if (m.sqlTemplate && m.sqlTemplate.trim()) {
		return m.sqlTemplate.trim();
	}
	if (!m.aggFunc || !m.sourceTable) {
		return '-- 缺少 aggFunc 或 sourceTable，无法拼装';
	}
	const field = m.aggField && m.aggField.trim() ? m.aggField.trim() : '*';
	const agg = `${m.aggFunc.toUpperCase()}(${field})`;
	let sql = `SELECT ${agg} AS ${snakeCase(m.metricName || 'metric_value')}\nFROM ${m.sourceTable}`;
	if (m.defaultTimeField && m.defaultTimeField.trim()) {
		sql += `\nWHERE ${m.defaultTimeField.trim()} >= :start_time\n  AND ${m.defaultTimeField.trim()} < :end_time`;
	}
	return sql + ';';
});

function snakeCase(name: string): string {
	return (
		name
			.replace(/([A-Z])/g, '_$1')
			.replace(/[\s\-]+/g, '_')
			.replace(/[^a-zA-Z0-9_]/g, '')
			.toLowerCase()
			.replace(/^_+|_+$/g, '') || 'metric_value'
	);
}

function openAssembleDialog(model: Metric) {
	assembleTarget.value = model;
	assembleQuery.value = '';
	assembleDialogVisible.value = true;
}

async function copyAssembleSql() {
	try {
		await navigator.clipboard.writeText(assemblePreviewSql.value);
		$tip('SQL 已复制到剪贴板');
	} catch {
		$tip('复制失败，请手动选择文本', { color: 'error', icon: 'mdi-alert-circle' });
	}
}

function openVersions(_model: Metric) {
	// 口径版本入口由后续 version tab 提供
	$tip('口径版本管理即将上线', { color: 'info', icon: 'mdi-information' });
}

async function saveModel() {
	const createData: MetricDto = {
		metricCode: modelForm.value.metricCode,
		metricName: modelForm.value.metricName,
		agentId: modelForm.value.agentId,
		datasourceId: modelForm.value.datasourceId,
		sourceTable: modelForm.value.sourceTable,
		aggField: modelForm.value.aggField,
		aggFunc: modelForm.value.aggFunc,
		defaultTimeField: modelForm.value.defaultTimeField,
		sqlTemplate: modelForm.value.sqlTemplate,
		description: modelForm.value.description,
		status: modelForm.value.status,
	};
	const updateData: Metric = {
		...modelForm.value,
		id: currentEditId.value ?? undefined,
	};
	// eslint-disable-next-line @typescript-eslint/no-explicit-any
	const ok = await saveItem(createData as any, updateData, currentEditId.value);
	if (ok) {
		$tip(isEdit.value ? '更新成功' : '创建成功');
	} else {
		$tip(`${isEdit.value ? '更新' : '创建'}失败`, {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	}
}

async function loadAgents() {
	try {
		agentItems.value = await agentService.list();
	} catch {
		agentItems.value = [];
	}
}

async function loadDatasources() {
	try {
		datasourceItems.value = await datasourceService.list();
	} catch {
		datasourceItems.value = [];
	}
}

onMounted(async () => {
	await Promise.all([loadAgents(), loadDatasources()]);
	await loadMetrics();
});
</script>

<style scoped>
.assemble-sql-preview {
	background: #1e1e1e;
	color: #d4d4d4;
	padding: 16px;
	border-radius: var(--da-radius-md);
	font-family: var(--da-font-mono);
	font-size: 13px;
	line-height: 1.6;
	overflow-x: auto;
	white-space: pre;
	margin: 0;
}
</style>
