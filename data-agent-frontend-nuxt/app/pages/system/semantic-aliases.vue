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
			title="语义别名配置"
			subtitle="业务黑话到指标编码的映射，连接业务语言与系统定义。"
		>
			<template #actions>
				<v-btn
					class="text-none"
					style="border-color: var(--da-line-soft)"
					variant="outlined"
					prepend-icon="mdi-refresh"
					:loading="loading"
					:disabled="!selectedAgentId"
					@click="loadAliases"
				>
					刷新
				</v-btn>
				<v-btn
					color="primary"
					prepend-icon="mdi-plus"
					class="text-none px-6"
					elevation="0"
					:disabled="!selectedAgentId"
					@click="openCreateDialog"
				>
					添加别名
				</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-3 pa-3 da-toolbar">
			<div class="d-flex flex-wrap ga-3 align-center">
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
					@update:model-value="onAgentChange"
				/>
				<v-text-field
					v-model="searchKeyword"
					placeholder="搜索别名文本 / 目标编码"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					class="search-field"
					style="max-width: 320px"
					@keyup.enter="applySearch"
					@click:clear="clearSearch"
				/>
				<v-spacer />
				<v-chip
					color="primary"
					variant="flat"
					class="font-weight-medium"
				>
					总数 {{ filteredList.length }}
				</v-chip>
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table
				:headers="headers"
				:items="filteredList"
				item-value="id"
				hover
				:loading="loading"
				:search="searchKeyword"
			>
				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.aliasText="{ item }">
					<div class="d-flex flex-column">
						<span class="font-weight-medium">{{ item.aliasText }}</span>
						<span class="text-caption text-medium-emphasis">
							{{ formatDateTime(item.createdTime) }}
						</span>
					</div>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.targetType="{ item }">
					<v-chip
						:color="getTargetTypeColor(item.targetType)"
						variant="flat"
						size="small"
						class="font-weight-medium"
					>
						{{ getTargetTypeLabel(item.targetType) }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.targetCode="{ item }">
					<span class="text-body-2 font-weight-medium">
						{{ item.targetCode }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.matchType="{ item }">
					<v-chip
						:color="
							item.matchType === 'EXACT' ? 'success' : 'warning'
						"
						variant="flat"
						size="small"
					>
						{{ getMatchTypeLabel(item.matchType) }}
					</v-chip>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.priority="{ item }">
					<span class="text-body-2 font-weight-medium">
						{{ item.priority }}
					</span>
				</template>

				<!-- eslint-disable-next-line vue/valid-v-slot -->
				<template #item.status="{ item }">
					<v-switch
						:model-value="item.status === 1"
						color="success"
						hide-details
						density="compact"
						:loading="switchingIds.has(item.id)"
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
							icon="mdi-tag-multiple"
							size="64"
							color="primary"
							class="mb-4"
						/>
						<p class="text-body-1 text-medium-emphasis mb-2">
							{{ selectedAgentId ? '暂无语义别名' : '请先选择智能体' }}
						</p>
						<p class="text-body-2 text-disabled mb-6">
							{{
								selectedAgentId
									? '点击「添加别名」开始配置业务黑话到指标编码的映射'
									: '在顶部下拉中选择一个智能体后加载其别名列表'
							}}
						</p>
						<v-btn
							v-if="selectedAgentId"
							color="primary"
							prepend-icon="mdi-plus"
							class="text-none"
							elevation="0"
							@click="openCreateDialog"
						>
							添加别名
						</v-btn>
					</div>
				</template>
			</v-data-table>
		</v-card>

		<!-- 别名测试器：输入业务黑话 → 实时显示命中的别名映射（调试/校验用） -->
		<v-card variant="flat" border class="rounded-lg pa-5 da-toolbar">
			<div class="d-flex align-center mb-3">
				<v-icon icon="mdi-flask-outline" color="primary" class="mr-2" size="22" />
				<span class="text-subtitle-1 font-weight-medium">别名测试器</span>
				<v-chip size="x-small" variant="tonal" color="primary" class="ml-3">
					Playground
				</v-chip>
			</div>
			<p class="text-body-2 text-medium-emphasis mb-4">
				输入一句业务说法，实时查看它命中哪条别名映射（按优先级取最佳匹配）。用于校验别名配置是否覆盖了真实业务黑话。
			</p>
			<div class="d-flex flex-wrap ga-3 align-center mb-1">
				<v-text-field
					v-model="testerText"
					placeholder="例如：上个月的 GMV"
					prepend-inner-icon="mdi-magnify"
					variant="outlined"
					density="compact"
					clearable
					hide-details
					:disabled="!selectedAgentId || testerLoading"
					class="tester-input"
					style="min-width: 280px; flex: 1"
					@keyup.enter="runTester"
				/>
				<v-btn
					color="primary"
					prepend-icon="mdi-play"
					class="text-none"
					elevation="0"
					:loading="testerLoading"
					:disabled="!selectedAgentId || !testerText?.trim()"
					@click="runTester"
				>
					解析
				</v-btn>
			</div>

			<!-- 解析结果 -->
			<v-alert
				v-if="testerTried && !testerResult"
				variant="tonal"
				type="info"
				density="compact"
				class="mt-3"
			>
				未命中任何别名。考虑将该业务说法新增为别名，或检查匹配类型/优先级配置。
			</v-alert>
			<div v-else-if="testerResult" class="mt-3">
				<v-sheet border rounded="lg" class="pa-4 bg-primary-soft">
					<div class="d-flex flex-wrap ga-4 align-center">
						<div class="d-flex flex-column">
							<span class="text-caption text-medium-emphasis">命中别名</span>
							<span class="text-body-1 font-weight-medium">
								{{ testerResult.aliasText }}
							</span>
						</div>
						<v-divider vertical class="mx-2" />
						<div class="d-flex flex-column">
							<span class="text-caption text-medium-emphasis">目标类型</span>
							<v-chip
								:color="getTargetTypeColor(testerResult.targetType)"
								variant="flat"
								size="small"
								class="font-weight-medium"
							>
								{{ getTargetTypeLabel(testerResult.targetType) }}
							</v-chip>
						</div>
						<div class="d-flex flex-column">
							<span class="text-caption text-medium-emphasis">映射 Code</span>
							<span class="text-body-1 font-weight-medium font-mono">
								{{ testerResult.targetCode }}
							</span>
						</div>
						<div class="d-flex flex-column">
							<span class="text-caption text-medium-emphasis">匹配方式</span>
							<v-chip
								:color="
									testerResult.matchType === 'EXACT'
										? 'success'
										: 'warning'
								"
								variant="flat"
								size="small"
							>
								{{ testerResult.matchType }}
							</v-chip>
						</div>
						<div class="d-flex flex-column">
							<span class="text-caption text-medium-emphasis">优先级</span>
							<span class="text-body-1 font-weight-medium">
								{{ testerResult.priority }}
							</span>
						</div>
					</div>
				</v-sheet>
			</div>
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
						isEdit ? '编辑语义别名' : '添加语义别名'
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
							<v-col cols="12">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									别名文本 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.aliasText"
									placeholder="业务黑话 / 自然语言表述，如「GMV」「成交额」"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '别名文本不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									目标类型 <span class="text-error">*</span>
								</p>
								<v-select
									v-model="modelForm.targetType"
									:items="targetTypeOptions"
									placeholder="METRIC / DIM / VER / FILTER"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '目标类型不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									目标编码 <span class="text-error">*</span>
								</p>
								<v-text-field
									v-model="modelForm.targetCode"
									placeholder="映射到的规范化编码，如 order_amount"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '目标编码不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									匹配类型 <span class="text-error">*</span>
								</p>
								<v-select
									v-model="modelForm.matchType"
									:items="matchTypeOptions"
									placeholder="EXACT / FUZZY"
									variant="outlined"
									density="compact"
									:rules="[(v) => !!v || '匹配类型不能为空']"
									hide-details="auto"
								/>
							</v-col>
							<v-col cols="12" md="6">
								<p
									class="text-body-2 font-weight-medium text-medium-emphasis mb-2"
								>
									优先级
								</p>
								<v-text-field
									v-model.number="modelForm.priority"
									type="number"
									placeholder="数值越大优先级越高（默认 0）"
									variant="outlined"
									density="compact"
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
	</section>
</template>

<script setup lang="ts">
import semanticAliasService, {
	type SemanticAlias,
} from '~/services/semanticAlias/index';
import agentService from '~/services/agent/index';
import { useCrudPage } from '~/composables/useCrudPage/index';

const { $tip } = useNuxtApp();
const { showConfirm } = useConfirm();

// ——— 选项候选 ———
const targetTypeOptions = ['METRIC', 'DIM', 'VER', 'FILTER'];
const matchTypeOptions = ['EXACT', 'FUZZY'];

// ——— 智能体下拉 & 搜索 ———
const agentItems = ref<Array<{ id?: number; name?: string }>>([]);
const selectedAgentId = ref<number | null>(null);
const searchKeyword = ref('');

const currentEditId = ref<number | null>(null);

// 切换状态中的别名 id 集合
const switchingIds = ref<Set<number>>(new Set());

// ——— 别名测试器（Playground）状态 ———
const testerText = ref('');
const testerLoading = ref(false);
const testerResult = ref<SemanticAlias | null>(null);
// testerTried: 是否已尝试解析（区分“未尝试”与“尝试后无命中”）
const testerTried = ref(false);

// ——— useCrudPage ———
const {
	loading,
	saveLoading,
	items: aliasList,
	dialogVisible,
	isEdit,
	formRef,
	formData: modelForm,
	loadItems: loadAliases,
	openCreateDialog: _openCreateDialog,
	openEditDialog,
	closeDialog,
	saveItem,
	deleteItem,
} = useCrudPage<SemanticAlias, SemanticAlias, SemanticAlias>({
	loadFn: () => {
		if (selectedAgentId.value == null) return Promise.resolve([]);
		return semanticAliasService.listByAgentId(selectedAgentId.value);
	},
	createFn: (data) => semanticAliasService.create(data),
	updateFn: (_id, data) => semanticAliasService.update(data),
	deleteFn: (id) => semanticAliasService.delete(id),
	defaultFormFactory: () => ({
		agentId: selectedAgentId.value ?? 0,
		aliasText: '',
		targetType: 'METRIC',
		targetCode: '',
		matchType: 'EXACT',
		priority: 0,
		status: 1,
	}),
});

// 前端二次过滤（v-data-table 自带 search 作用于全部列）
const filteredList = computed(() => {
	if (!searchKeyword.value) return aliasList.value;
	const kw = searchKeyword.value.toLowerCase();
	return aliasList.value.filter(
		(a) =>
			a.aliasText?.toLowerCase().includes(kw) ||
			a.targetCode?.toLowerCase().includes(kw),
	);
});

const headers = [
	{ title: '别名文本', key: 'aliasText', minWidth: '180px' },
	{ title: '目标类型', key: 'targetType', width: '130px', sortable: false },
	{ title: '目标编码', key: 'targetCode', minWidth: '160px' },
	{ title: '匹配类型', key: 'matchType', width: '120px', sortable: false },
	{ title: '优先级', key: 'priority', width: '100px' },
	{ title: '状态', key: 'status', width: '120px', sortable: false },
	{ title: '操作', key: 'actions', width: '140px', sortable: false },
];

function getTargetTypeLabel(type: string): string {
	const map: Record<string, string> = {
		METRIC: '指标',
		DIM: '维度',
		VER: '版本',
		FILTER: '过滤器',
	};
	return map[type] || type;
}

function getTargetTypeColor(type: string): string {
	const map: Record<string, string> = {
		METRIC: 'primary',
		DIM: 'primary',
		VER: 'info',
		FILTER: 'warning',
	};
	return map[type] || 'grey';
}

function getMatchTypeLabel(type: string): string {
	const map: Record<string, string> = {
		EXACT: '精确匹配',
		FUZZY: '模糊匹配',
	};
	return map[type] || type;
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

function applySearch() {
	// filteredList 是 computed，searchKeyword 变更即触发；此处保留钩子用于扩展
}

function clearSearch() {
	searchKeyword.value = '';
}

async function onAgentChange(agentId: number | null) {
	selectedAgentId.value = agentId;
	// 切换智能体时重置测试器状态
	testerResult.value = null;
	testerTried.value = false;
	testerText.value = '';
	if (agentId != null) {
		await loadAliases();
	} else {
		aliasList.value = [];
	}
}

// 别名测试器：调用后端 resolve（按优先级取最佳匹配），实时展示命中结果
async function runTester() {
	if (selectedAgentId.value == null || !testerText.value?.trim()) return;
	testerLoading.value = true;
	testerTried.value = true;
	try {
		const result = await semanticAliasService.resolve(
			selectedAgentId.value,
			testerText.value.trim(),
		);
		testerResult.value = result;
	} catch {
		testerResult.value = null;
		testerResolved.value = false;
		$tip('解析失败，请重试', { color: 'error', icon: 'mdi-alert-circle' });
	} finally {
		testerLoading.value = false;
	}
}

function openCreateDialog() {
	if (selectedAgentId.value == null) {
		$tip('请先选择智能体', { color: 'info', icon: 'mdi-information' });
		return;
	}
	_openCreateDialog();
	modelForm.value.agentId = selectedAgentId.value;
}

function editModel(model: SemanticAlias) {
	currentEditId.value = model.id ?? null;
	openEditDialog(model);
}

function deleteModel(model: SemanticAlias) {
	if (!model.id) return;
	showConfirm({
		title: '删除确认',
		message: `确定要删除别名「${model.aliasText}」吗？此操作不可恢复。`,
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

async function toggleStatus(model: SemanticAlias, status: number) {
	if (!model.id) return;
	switchingIds.value.add(model.id);
	try {
		const ok = await semanticAliasService.update({ ...model, status });
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
		switchingIds.value.delete(model.id);
	}
}

async function saveModel() {
	const createData: SemanticAlias = {
		...modelForm.value,
		agentId: selectedAgentId.value ?? modelForm.value.agentId,
	};
	const updateData: SemanticAlias = {
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
		// 默认选中第一个智能体，便于直接查看
		if (selectedAgentId.value == null && agentItems.value.length > 0) {
			selectedAgentId.value = agentItems.value[0].id ?? null;
			await loadAliases();
		}
	} catch {
		agentItems.value = [];
	}
}

onMounted(async () => {
	await loadAgents();
});
</script>

<style scoped></style>
