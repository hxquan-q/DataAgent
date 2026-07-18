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
			title="口径版本配置"
			subtitle="维护指标的多口径定义，杜绝金额歧义。"
		>
			<template #actions>
				<v-btn
					class="text-none"
					style="border-color: #e2e8f0"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					:disabled="!selectedMetricId"
					@click="loadVersions"
				>
					刷新
				</v-btn>
				<v-btn
					color="primary"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					:disabled="!selectedMetricId"
					@click="openCreateDialog"
				>
					添加版本
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-3 pa-3">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-select
					v-model="selectedMetricId"
					:items="metricItems"
					item-title="metricName"
					item-value="id"
					placeholder="选择指标"
					prepend-inner-icon="mdi-chart-line"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="metric-select"
					style="max-width: 320px"
					@update:model-value="onMetricChange"
				/>
				<v-spacer />
				<v-chip
					color="primary"
					variant="flat"
					class="font-weight-medium"
				>
					总数 {{ versionList.length }}
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="versionList"
				item-value="id"
				hover
				:loading="loading"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.verCode="{ item }">
					<div class="d-flex flex-column">
						<span class="font-weight-medium">{{ item.verCode }}</span>
						<span class="text-caption text-medium-emphasis">
							{{ formatDateTime(item.createdTime) }}
						</span>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.filterCondition="{ item }">
					<span class="text-body-2 text-medium-emphasis">
						{{ item.filterCondition || '—' }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.isDefault="{ item }">
					<v-switch
						:model-value="item.isDefault === 1"
						color="success"
						hide-details
						density="compact"
						:loading="switchingIds.has(item.id)"
						@update:model-value="(val) => toggleDefault(item, val ? 1 : 0)"
					>
						<template #label>
							<span class="text-body-2">
								{{ item.isDefault === 1 ? '默认' : '非默认' }}
							</span>
						</template>
					</v-switch>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-switch
						:model-value="item.status === 1"
						color="success"
						hide-details
						density="compact"
						:loading="statusSwitchingIds.has(item.id)"
						@update:model-value="(val) => toggleStatus(item, val ? 1 : 0)"
					>
						<template #label>
							<span class="text-body-2">
								{{ item.status === 1 ? '启用' : '禁用' }}
							</span>
						</template>
					</v-switch>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.actions="{ item }">
					<div class="d-flex ga-1 align-center">
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
							color="red-darken-1"
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
							icon="mdi-source-branch"
							size="64"
							color="primary"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">
							{{ selectedMetricId ? '暂无口径版本' : '请先选择指标' }}
						</p>
						<p class="text-body-2 text-disabled mb-6">
							{{
								selectedMetricId
									? '点击「添加版本」开始配置该指标的多口径定义'
									: '在顶部下拉中选择一个指标后加载其口径版本列表'
							}}
						</p>
						<v-btn
							v-if="selectedMetricId"
							color="primary"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加版本
						</v-btn>
					</div>
				</template>
				</v-data-table>
		</v-card>

		<!-- 口径版本对比：选两个版本并排 diff，直观查看口径差异（杜绝金额歧义） -->
		<v-card variant="flat" border class="rounded-lg pa-5">
			<div class="d-flex align-center mb-3">
				<v-icon icon="mdi-compare-horizontal" color="primary" class="mr-2" size="22" />
				<span class="text-subtitle-1 font-weight-bold">口径版本对比</span>
				<v-chip size="x-small" variant="tonal" color="primary" class="ml-3">
					Diff
				</v-chip>
			</div>
			<p class="text-body-2 text-medium-emphasis mb-4">
				选择两个口径版本，并排查看其时间字段、过滤条件、描述差异，快速定位口径分歧。
			</p>
			<div class="d-flex flex-wrap ga-3 align-center mb-4">
				<v-select
					v-model="diffLeftId"
					:items="versionList"
					item-title="verCode"
					item-value="id"
					placeholder="版本 A"
					variant="outlined"
					density="compact"
					hide-details
					:disabled="versionList.length === 0"
					style="max-width: 220px"
				/>
				<v-icon icon="mdi-arrow-right" color="medium-emphasis" />
				<v-select
					v-model="diffRightId"
					:items="versionList"
					item-title="verCode"
					item-value="id"
					placeholder="版本 B"
					variant="outlined"
					density="compact"
					hide-details
					:disabled="versionList.length === 0"
					style="max-width: 220px"
				/>
			</div>

			<v-sheet
				v-if="diffLeft && diffRight"
				border
				rounded="lg"
				class="overflow-hidden"
			>
				<div class="d-flex">
					<div class="flex-grow-1 pa-4 diff-col diff-col--left">
						<div class="d-flex align-center mb-3">
							<v-chip size="small" color="primary" variant="flat">
								{{ diffLeft.verCode }}
							</v-chip>
							<v-chip
								v-if="diffLeft.isDefault === 1"
								size="x-small"
								color="green-lighten-4"
								variant="flat"
								class="ml-2"
							>
								默认
							</v-chip>
						</div>
						<DiffRow label="时间字段" :value="diffLeft.timeField" />
						<DiffRow label="过滤条件" :value="diffLeft.filterCondition" mono />
						<DiffRow label="描述" :value="diffLeft.description" />
					</div>
					<v-divider vertical />
					<div class="flex-grow-1 pa-4 diff-col diff-col--right">
						<div class="d-flex align-center mb-3">
							<v-chip size="small" color="primary" variant="flat">
								{{ diffRight.verCode }}
							</v-chip>
							<v-chip
								v-if="diffRight.isDefault === 1"
								size="x-small"
								color="green-lighten-4"
								variant="flat"
								class="ml-2"
							>
								默认
							</v-chip>
						</div>
						<DiffRow
							label="时间字段"
							:value="diffRight.timeField"
							:changed="diffLeft.timeField !== diffRight.timeField"
						/>
						<DiffRow
							label="过滤条件"
							:value="diffRight.filterCondition"
							mono
							:changed="diffLeft.filterCondition !== diffRight.filterCondition"
						/>
						<DiffRow
							label="描述"
							:value="diffRight.description"
							:changed="diffLeft.description !== diffRight.description"
						/>
					</div>
				</div>
				<v-alert
					v-if="isIdentical"
					variant="tonal"
					type="success"
					density="compact"
					class="ma-3"
				>
					两版本口径一致，无差异。
				</v-alert>
			</v-sheet>
			<v-alert
				v-else-if="versionList.length < 2"
				variant="tonal"
				type="info"
				density="compact"
			>
				该指标至少需要 2 个口径版本才能进行对比。
			</v-alert>
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
					<span class="text-h6 font-weight-bold">{{
						isEdit ? '编辑口径版本' : '添加口径版本'
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
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									版本编码 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.verCode"
									placeholder="如 v2024_paid / annual / monthly"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '版本编码不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									时间字段
								</p>
								<v-text-field
									v-model="modelForm.timeField"
									placeholder="如 paid_at / created_at"
									variant="outlined"
									density="compact"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									过滤条件（SQL 片段）
								</p>
								<v-textarea
									v-model="modelForm.filterCondition"
									placeholder='如 status = "PAID" AND amount > 0'
									variant="outlined"
									density="compact"
									rows="3"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									口径说明
								</p>
								<v-textarea
									v-model="modelForm.description"
									placeholder="描述该口径的业务定义，如「已支付订单金额（剔除退款）」"
									variant="outlined"
									density="compact"
									rows="2"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
								>
									是否默认口径
								</p>
								<v-switch
									v-model="modelForm.isDefault"
									:true-value="1"
									:false-value="0"
									color="success"
									hide-details="auto"
									:label="
										modelForm.isDefault === 1 ? '默认口径' : '非默认'
									"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-grey-darken-2 mb-2"
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
	</section>
</template>

<script setup lang="ts">
import metricVersionService, {
	type MetricVersion,
} from '~/services/metricVersion/index';
import metricService, { type Metric } from '~/services/metric/index';
import { useCrudPage } from '~/composables/useCrudPage/index';

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 指标下拉 ———
const metricItems = ref<Metric[]>([]);
const selectedMetricId = ref<number | null>(null);

const currentEditId = ref<number | null>(null);

// ——— 口径版本对比（Diff）状态 ———
const diffLeftId = ref<number | null>(null);
const diffRightId = ref<number | null>(null);

// 切换默认/状态中的版本 id 集合（避免污染 MetricVersion 类型）
const switchingIds = ref<Set<number>>(new Set());
const statusSwitchingIds = ref<Set<number>>(new Set());

// ——— useCrudPage ———
const {
	loading,
	saveLoading,
	items: versionList,
	dialogVisible,
	isEdit,
	formRef,
	formData: modelForm,
	loadItems: loadVersions,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<MetricVersion, MetricVersion, MetricVersion>({
	loadFn: () => {
		if (selectedMetricId.value == null) return Promise.resolve([]);
		return metricVersionService.listByMetricId(selectedMetricId.value);
	},
	createFn: (data) => metricVersionService.create(data),
	updateFn: (_id, data) => metricVersionService.update(data),
	deleteFn: (id) => metricVersionService.delete(id),
	defaultFormFactory: () => ({
		metricId: selectedMetricId.value ?? 0,
		verCode: '',
		timeField: '',
		filterCondition: '',
		isDefault: 0,
		description: '',
		status: 1,
	}),
});

const headers = [
	{ title: '版本编码', key: 'verCode', minWidth: '160px' },
	{ title: '时间字段', key: 'timeField', minWidth: '140px' },
	{ title: '过滤条件', key: 'filterCondition', minWidth: '200px' },
	{ title: '默认口径', key: 'isDefault', width: '130px', sortable: false },
	{ title: '说明', key: 'description', minWidth: '180px' },
	{ title: '状态', key: 'status', width: '120px', sortable: false },
	{ title: '操作', key: 'actions', width: '140px', sortable: false },
];

// ——— 口径版本对比 computed ———
const diffLeft = computed(
	() => versionList.value.find((v) => v.id === diffLeftId.value) ?? null,
);
const diffRight = computed(
	() => versionList.value.find((v) => v.id === diffRightId.value) ?? null,
);
const isIdentical = computed(() => {
	if (!diffLeft.value || !diffRight.value) return false;
	return (
		diffLeft.value.timeField === diffRight.value.timeField &&
		diffLeft.value.filterCondition === diffRight.value.filterCondition &&
		diffLeft.value.description === diffRight.value.description
	);
});

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

async function onMetricChange(metricId: number | null) {
	selectedMetricId.value = metricId;
	// 切换指标时重置对比选择（避免跨指标残留）
	diffLeftId.value = null;
	diffRightId.value = null;
	if (metricId != null) {
		await loadVersions();
	} else {
		versionList.value = [];
	}
}

function openCreateDialog() {
	if (selectedMetricId.value == null) {
		$tip('请先选择指标', { color: 'info', icon: 'mdi-information' });
		return;
	}
	_openCreateDialog();
	modelForm.value.metricId = selectedMetricId.value;
}

function editModel(model: MetricVersion) {
	currentEditId.value = model.id ?? null;
	openEditDialog(model);
}

function deleteModel(model: MetricVersion) {
	if (!model.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除口径版本「${model.verCode}」吗？此操作不可恢复。`,
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

async function toggleDefault(model: MetricVersion, isDefault: number) {
	if (!model.id) return;
	switchingIds.value.add(model.id);
	try {
		const ok = await metricVersionService.update({ ...model, isDefault });
		if (ok) {
			// 默认口径互斥：同一指标内只允许一个默认
			if (isDefault === 1) {
				versionList.value.forEach((v) => {
					if (v.id !== model.id) v.isDefault = 0;
				});
			}
			model.isDefault = isDefault;
			$tip(`${isDefault === 1 ? '已设为默认' : '已取消默认'}`);
		} else {
			$tip('设置默认口径失败', {
				color: 'error',
				icon: 'mdi-alert-circle',
			});
		}
	} catch {
		$tip('设置默认口径失败', {
			color: 'error',
			icon: 'mdi-alert-circle',
		});
	} finally {
		switchingIds.value.delete(model.id);
	}
}

async function toggleStatus(model: MetricVersion, status: number) {
	if (!model.id) return;
	statusSwitchingIds.value.add(model.id);
	try {
		const ok = await metricVersionService.update({ ...model, status });
		if (ok) {
			model.status = status;
			$tip(`${status === 1 ? '启用' : '禁用'}成功`);
		} else {
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
		statusSwitchingIds.value.delete(model.id);
	}
}

async function saveModel() {
	const createData: MetricVersion = {
		...modelForm.value,
		metricId: selectedMetricId.value ?? modelForm.value.metricId,
	};
	const updateData: MetricVersion = {
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

async function loadMetrics() {
	try {
		metricItems.value = await metricService.list();
		// 默认选中第一个指标，便于直接查看
		if (selectedMetricId.value == null && metricItems.value.length > 0) {
			selectedMetricId.value = metricItems.value[0].id ?? null;
			await loadVersions();
		}
	} catch {
		metricItems.value = [];
	}
}

onMounted(async () => {
	await loadMetrics();
});
</script>

<style scoped></style>
