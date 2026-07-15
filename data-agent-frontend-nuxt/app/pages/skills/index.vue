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
		<KnowledgePageHeader title="技能管理" subtitle="维护可复用的 Agent 技能（报告风格等），按智能体绑定后注入对应 Prompt。">
			<template #actions>
				<v-btn class="text-none bg-white" style="border-color: #e2e8f0" variant="outlined" prepend-icon="mdi-refresh" :loading="loading" @click="loadSkills">刷新</v-btn>
				<v-btn color="blue-darken-3" prepend-icon="mdi-plus" class="text-none px-6" elevation="0" @click="openCreate">添加技能</v-btn>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" border class="rounded-lg mb-4 pa-4">
			<div class="d-flex flex-wrap ga-3 align-center">
				<v-select v-model="filterScope" :items="scopeOptions" item-title="label" item-value="value" label="作用域" density="comfortable" hide-details style="max-width: 200px" clearable @update:model-value="loadSkills" />
				<v-text-field v-model="filterKeyword" label="搜索名称/描述" density="comfortable" hide-details style="max-width: 280px" append-inner-icon="mdi-magnify" @keyup.enter="loadSkills" @click:append-inner="loadSkills" />
				<v-spacer />
			</div>
		</v-card>

		<v-card variant="flat" border class="rounded-lg">
			<v-data-table :headers="headers" :items="skills" :loading="loading" item-value="id" hover>
				<template #item.scope="{ value }">
					<v-chip size="small" color="blue-lighten-4">{{ scopeLabel(value) }}</v-chip>
				</template>
				<template #item.enabled="{ item }">
					<v-switch v-model="item.enabled" color="primary" hide-details density="compact" @update:model-value="toggleEnabled(item)" />
				</template>
				<template #item.actions="{ item }">
					<div class="d-flex ga-1">
						<v-icon icon="mdi-pencil-outline" size="small" @click.stop="openEdit(item)" />
						<v-icon icon="mdi-delete-outline" size="small" color="error" @click.stop="removeSkill(item)" />
					</div>
				</template>
				<template #no-data>
					<div class="pa-6 text-medium-emphasis">暂无技能，点击右上角“添加技能”创建。</div>
				</template>
			</v-data-table>
		</v-card>

		<!-- Agent 绑定面板 -->
		<v-card variant="flat" border class="rounded-lg mt-6 pa-4">
			<div class="text-h6 mb-3">按智能体绑定</div>
			<div class="d-flex flex-wrap ga-3 align-center mb-3">
				<v-select v-model="bindAgentId" :items="agentOptions" item-title="label" item-value="value" label="选择智能体" density="comfortable" hide-details style="max-width: 320px" @update:model-value="loadBoundSkills" />
			</div>
			<v-data-table v-if="bindAgentId" :headers="bindHeaders" :items="skills" :loading="loading" item-value="id" hover density="comfortable">
				<template #item.bound="{ item }">
					<v-switch :model-value="isBound(item.id)" color="primary" hide-details density="compact" @update:model-value="toggleBind(item, $event)" />
				</template>
			</v-data-table>
		</v-card>

		<!-- 新建/编辑对话框 -->
		<v-dialog v-model="dialog" max-width="720">
			<v-card>
				<v-card-title class="text-h6">{{ editing.id ? '编辑技能' : '添加技能' }}</v-card-title>
				<v-card-text>
					<v-row dense>
						<v-col cols="12" md="6">
							<v-text-field v-model="editing.name" label="技能名称" density="comfortable" :rules="[v => !!v || '必填']" />
						</v-col>
						<v-col cols="12" md="6">
							<v-select v-model="editing.scope" :items="scopeOptions" item-title="label" item-value="value" label="作用域" density="comfortable" />
						</v-col>
						<v-col cols="12">
							<v-text-field v-model="editing.description" label="技能描述（何时用）" density="comfortable" />
						</v-col>
						<v-col cols="12">
							<v-text-field v-model="editing.triggers" label="触发关键词（逗号分隔）" density="comfortable" />
						</v-col>
						<v-col cols="6">
							<v-text-field v-model.number="editing.priority" type="number" label="优先级（大的先注入）" density="comfortable" />
						</v-col>
						<v-col cols="6">
							<v-text-field v-model.number="editing.displayOrder" type="number" label="显示顺序" density="comfortable" />
						</v-col>
						<v-col cols="12">
							<v-textarea v-model="editing.content" label="技能正文/指令" rows="6" density="comfortable" :rules="[v => !!v || '必填']" />
						</v-col>
						<v-col cols="12">
							<v-switch v-model="editing.enabled" label="启用" density="compact" hide-details />
						</v-col>
					</v-row>
				</v-card-text>
				<v-card-actions>
					<v-spacer />
					<v-btn variant="text" @click="dialog = false">取消</v-btn>
					<v-btn color="primary" :loading="saving" @click="save">保存</v-btn>
				</v-card-actions>
			</v-card>
		</v-dialog>

		<v-snackbar v-model="snackbar" :color="snackbarColor" :timeout="2000">{{ snackbarText }}</v-snackbar>
	</section>
</template>

<script setup lang="ts">
import { skillService, type Skill, type SkillScope } from '~/services/skill';
import agentService from '~/services/agent';

definePageMeta({ layout: 'default' });

const scopeOptions = [
	{ label: '全部', value: '' },
	{ label: '报告 (report)', value: 'report' },
	{ label: 'SQL', value: 'sql' },
	{ label: 'Python', value: 'python' },
];

const headers = [
	{ title: '名称', key: 'name' },
	{ title: '作用域', key: 'scope' },
	{ title: '描述', key: 'description' },
	{ title: '优先级', key: 'priority' },
	{ title: '启用', key: 'enabled' },
	{ title: '操作', key: 'actions', sortable: false },
];
const bindHeaders = [
	{ title: '名称', key: 'name' },
	{ title: '作用域', key: 'scope' },
	{ title: '已绑定', key: 'bound', sortable: false },
];

const skills = ref<Skill[]>([]);
const loading = ref(false);
const filterScope = ref<'' | SkillScope>('');
const filterKeyword = ref('');
const dialog = ref(false);
const saving = ref(false);
const editing = ref<Skill>(emptySkill());

// Agent 绑定
const agentOptions = ref<{ label: string; value: number }[]>([]);
const bindAgentId = ref<number | null>(null);
const boundSkillIds = ref<Set<number>>(new Set());

const snackbar = ref(false);
const snackbarText = ref('');
const snackbarColor = ref('success');

function emptySkill(): Skill {
	return { name: '', scope: 'report', description: '', triggers: '', content: '', enabled: true, priority: 0, displayOrder: 0 };
}
function scopeLabel(s: string): string {
	return scopeOptions.find(o => o.value === s)?.label ?? s;
}
function notify(text: string, color = 'success') {
	snackbarText.value = text;
	snackbarColor.value = color;
	snackbar.value = true;
}

async function loadSkills() {
	loading.value = true;
	try {
		const { list } = await skillService.page({
			scope: filterScope.value || undefined,
			keyword: filterKeyword.value || undefined,
			pageSize: 200,
		});
		skills.value = list;
	} catch (e) {
		notify(`加载失败：${(e as Error).message}`, 'error');
	} finally {
		loading.value = false;
	}
}

async function loadAgents() {
	try {
		const list = await agentService.list();
		agentOptions.value = list.map((a: any) => ({ label: a.name, value: a.id }));
	} catch {
		agentOptions.value = [];
	}
}

async function loadBoundSkills() {
	if (!bindAgentId.value) {
		boundSkillIds.value = new Set();
		return;
	}
	try {
		const bound = await skillService.listByAgent(bindAgentId.value);
		boundSkillIds.value = new Set(bound.map(s => s.id!));
	} catch (e) {
		notify(`加载绑定失败：${(e as Error).message}`, 'error');
	}
}
function isBound(id?: number): boolean {
	return !!id && boundSkillIds.value.has(id);
}
async function toggleBind(skill: Skill, val: boolean) {
	if (!bindAgentId.value || !skill.id) return;
	try {
		if (val) {
			await skillService.bind(skill.id, bindAgentId.value);
			boundSkillIds.value.add(skill.id);
		} else {
			await skillService.unbind(skill.id, bindAgentId.value);
			boundSkillIds.value.delete(skill.id);
		}
		notify(val ? '已绑定' : '已解绑');
	} catch (e) {
		notify(`操作失败：${(e as Error).message}`, 'error');
	}
}

function openCreate() {
	editing.value = emptySkill();
	dialog.value = true;
}
function openEdit(skill: Skill) {
	editing.value = { ...skill };
	dialog.value = true;
}
async function save() {
	if (!editing.value.name || !editing.value.content) {
		notify('名称与正文必填', 'error');
		return;
	}
	saving.value = true;
	try {
		if (editing.value.id) {
			await skillService.update(editing.value.id, editing.value);
		} else {
			await skillService.create(editing.value);
		}
		dialog.value = false;
		notify('保存成功');
		await loadSkills();
	} catch (e) {
		notify(`保存失败：${(e as Error).message}`, 'error');
	} finally {
		saving.value = false;
	}
}
async function toggleEnabled(skill: Skill) {
	try {
		await skillService.update(skill.id!, skill);
		notify(skill.enabled ? '已启用' : '已禁用');
	} catch (e) {
		notify(`操作失败：${(e as Error).message}`, 'error');
	}
}
async function removeSkill(skill: Skill) {
	if (!skill.id) return;
	if (!window.confirm(`确认删除技能「${skill.name}」？`)) return;
	try {
		await skillService.remove(skill.id);
		notify('已删除');
		await loadSkills();
	} catch (e) {
		notify(`删除失败：${(e as Error).message}`, 'error');
	}
}

onMounted(() => {
	loadSkills();
	loadAgents();
});
</script>

<style scoped>
.page-shell {
	padding: 24px;
	max-width: 1200px;
	margin: 0 auto;
}
</style>
