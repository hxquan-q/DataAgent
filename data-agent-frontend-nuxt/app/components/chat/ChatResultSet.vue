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
	<div class="result-set-wrap" :class="{ 'is-pending-report': pendingReport }">
		<!-- Error state -->
		<div v-if="errorMsg" class="result-error" role="alert">
			<v-icon size="14" color="error" class="mr-1">mdi-alert-circle-outline</v-icon>
			{{ errorMsg }}
		</div>

		<!-- Parse miss / no structure -->
		<div v-else-if="!data || !columns.length" class="result-empty" role="status">
			<v-icon size="18" color="grey" class="mb-1">mdi-table-off</v-icon>
			<div>{{ parseHint }}</div>
		</div>

		<!-- Columns but 0 rows -->
		<template v-else-if="totalRows === 0">
			<div class="result-header">
				<span class="result-count">查询成功 · <strong>0</strong> 条</span>
			</div>
			<div class="result-empty result-empty--soft" role="status">
				结果集为空（列已解析，无行数据）
			</div>
		</template>

		<!-- Table -->
		<template v-else>
			<div v-if="pendingReport" class="result-pending" role="status">
				<span class="result-pending-dot" />
				数据已就绪 · 报告生成中
			</div>
			<div class="result-header">
				<span class="result-count">
					共 <strong>{{ totalRows }}</strong> 条
					<span v-if="columns.length" class="result-meta">· {{ columns.length }} 列</span>
					<span v-if="isLarge" class="result-warn">· 大数据集，已分页</span>
				</span>
				<div class="result-actions">
					<button
						type="button"
						class="action-btn"
						:title="copied ? '已复制' : '复制 CSV'"
						:aria-label="copied ? '已复制' : '复制 CSV'"
						title="复制 CSV"
						@click="copyCsv"
					>
						<v-icon size="14">{{ copied ? 'mdi-check' : 'mdi-content-copy' }}</v-icon>
						<span>{{ copied ? '已复制' : '复制' }}</span>
					</button>
					<button
						type="button"
						class="action-btn"
						title="下载 CSV"
						aria-label="下载 CSV"
						@click="downloadCsv"
					>
						<v-icon size="14">mdi-download</v-icon>
						<span>下载</span>
					</button>
					<div v-if="totalPages > 1" class="pagination">
						<span class="pagination-info">{{ currentPage }}/{{ totalPages }}</span>
						<button
							type="button"
							class="page-btn"
							:disabled="currentPage <= 1"
							aria-label="上一页"
							@click="currentPage--"
						>
							<v-icon size="13">mdi-chevron-left</v-icon>
						</button>
						<button
							type="button"
							class="page-btn"
							:disabled="currentPage >= totalPages"
							aria-label="下一页"
							@click="currentPage++"
						>
							<v-icon size="13">mdi-chevron-right</v-icon>
						</button>
					</div>
				</div>
			</div>
			<div class="table-container custom-scrollbar">
				<table class="result-table">
					<thead>
						<tr>
							<th v-for="col in columns" :key="col" scope="col">{{ col }}</th>
						</tr>
					</thead>
					<tbody>
						<tr v-for="(row, i) in pageData" :key="i">
							<td
								v-for="col in columns"
								:key="col"
								:class="cellClass(row[col])"
								:title="cellTitle(row[col])"
							>
								{{ formatCell(row[col]) }}
							</td>
						</tr>
					</tbody>
				</table>
			</div>
		</template>
	</div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { ResultData } from '~/services/resultSet/index';

const LARGE_THRESHOLD = 500;

const props = defineProps<{
	data: ResultData | null;
	pageSize?: number;
	/** vercel AI: 报告流式中时表可先到，标「数据就绪」避免双焦点误判 */
	pendingReport?: boolean;
}>();

const currentPage = ref(1);
const copied = ref(false);
let copyTimer: ReturnType<typeof setTimeout> | null = null;

const pageSz = computed(() => props.pageSize || 50);
const columns = computed(() => props.data?.resultSet?.column || []);
const allRows = computed(() => props.data?.resultSet?.data || []);
const totalRows = computed(() => allRows.value.length);
const totalPages = computed(() => Math.max(1, Math.ceil(totalRows.value / pageSz.value)));
const pageData = computed(() => {
	const start = (currentPage.value - 1) * pageSz.value;
	return allRows.value.slice(start, start + pageSz.value);
});
const errorMsg = computed(() => props.data?.resultSet?.errorMsg || '');
const isLarge = computed(() => totalRows.value >= LARGE_THRESHOLD);
const parseHint = computed(() => {
	if (!props.data) return '结果解析失败或未返回数据';
	return '暂无表格结构';
});

watch(
	() => props.data,
	() => {
		currentPage.value = 1;
	},
);

function isNumeric(v: unknown): boolean {
	if (typeof v === 'number') return Number.isFinite(v);
	if (typeof v !== 'string' || v.trim() === '') return false;
	return /^-?\d+(\.\d+)?([eE][+-]?\d+)?$/.test(v.trim());
}

function formatCell(v: unknown): string {
	if (v === null || v === undefined) return 'NULL';
	if (v === '') return '—';
	if (typeof v === 'object') {
		try {
			return JSON.stringify(v);
		} catch {
			return String(v);
		}
	}
	return String(v);
}

function cellClass(v: unknown): string {
	if (v === null || v === undefined) return 'cell-null';
	if (v === '') return 'cell-empty';
	if (isNumeric(v)) return 'cell-num';
	return '';
}

function cellTitle(v: unknown): string {
	const s = formatCell(v);
	return s.length > 40 ? s : '';
}

function csvEscape(v: unknown): string {
	if (v === null || v === undefined) return '';
	const s = typeof v === 'object' ? JSON.stringify(v) : String(v);
	if (/[",\n\r]/.test(s)) return `"${s.replace(/"/g, '""')}"`;
	return s;
}

function buildCsv(): string {
	const cols = columns.value;
	const rows = allRows.value;
	const lines = [
		cols.map(csvEscape).join(','),
		...rows.map((row) => cols.map((c) => csvEscape(row[c])).join(',')),
	];
	return lines.join('\n');
}

async function copyCsv() {
	const text = buildCsv();
	try {
		await navigator.clipboard.writeText(text);
	} catch {
		// ponytail: clipboard fallback
		const ta = document.createElement('textarea');
		ta.value = text;
		ta.style.position = 'fixed';
		ta.style.left = '-9999px';
		document.body.appendChild(ta);
		ta.select();
		document.execCommand('copy');
		document.body.removeChild(ta);
	}
	copied.value = true;
	if (copyTimer) clearTimeout(copyTimer);
	copyTimer = setTimeout(() => {
		copied.value = false;
	}, 1600);
}

function downloadCsv() {
	const text = buildCsv();
	const blob = new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' });
	const url = URL.createObjectURL(blob);
	const a = document.createElement('a');
	a.href = url;
	a.download = `result-${Date.now()}.csv`;
	a.click();
	URL.revokeObjectURL(url);
}
</script>

<style scoped>
.result-set-wrap {
	font-size: 13px;
}
.result-set-wrap.is-pending-report {
	opacity: 0.96;
}
.result-pending {
	display: flex;
	align-items: center;
	gap: 6px;
	padding: 5px 6px;
	font-size: 11px;
	font-weight: 600;
	color: var(--da-primary);
	background: var(--da-primary-soft);
	border: 1px solid color-mix(in srgb, var(--da-primary) 35%, transparent);
	border-bottom: none;
	border-radius: 6px 6px 0 0;
}
.result-pending-dot {
	width: 6px;
	height: 6px;
	border-radius: 50%;
	background: var(--da-accent);
	animation: pendingPulse 1.2s ease-in-out infinite;
}
@keyframes pendingPulse {
	0%,
	100% {
		opacity: 0.45;
		transform: scale(0.9);
	}
	50% {
		opacity: 1;
		transform: scale(1.1);
	}
}
.result-error {
	font-size: 12.5px;
	display: flex;
	align-items: center;
	background: color-mix(in srgb, var(--da-danger) 8%, white);
	color: var(--da-danger);
	padding: 6px 10px;
	font-size: 13px;
	border-radius: 6px;
}
.result-empty {
	display: flex;
	flex-direction: column;
	align-items: center;
	justify-content: center;
	text-align: center;
	color: var(--da-muted);
	padding: 12px 10px;
	font-size: 12.5px;
	background: var(--da-surface-soft);
	border: 1px dashed var(--da-line-soft);
	border-radius: 6px;
	gap: 4px;
}
.result-empty--soft {
	border-radius: 0 0 6px 6px;
	border-top: none;
}
.result-header {
	min-height: 32px;
	display: flex;
	justify-content: space-between;
	align-items: center;
	gap: 6px;
	padding: 4px 6px;
	background: linear-gradient(180deg, var(--da-surface-soft) 0%, var(--da-surface-soft) 100%);
	border: 1px solid var(--da-line-soft);
	border-bottom: none;
	border-radius: 8px 8px 0 0;
}
.result-set-wrap.is-pending-report .result-header {
	border-radius: 0;
}
.result-count {
	font-size: 12px;
	color: var(--da-muted);
}
.result-count strong {
	color: var(--da-primary);
	font-weight: 600;
}
.result-meta {
	color: var(--da-muted);
	font-size: 11.5px;
	line-height: 1.3;
}
.result-warn {
	color: var(--da-warning);
	font-weight: 600;
	font-size: 11px;
}
.result-actions {
	display: flex;
	align-items: center;
	gap: 8px;
	flex-wrap: wrap;
	justify-content: flex-end;
}
.action-btn {
	min-width: 28px;
	height: 28px;
	display: inline-flex;
	align-items: center;
	gap: 4px;
	height: 28px;
	padding: 0 10px;
	background: var(--da-surface);
	border: 1px solid var(--da-line);
	border-radius: 6px;
	font-size: 11.5px;
	color: var(--da-ink);
	cursor: pointer;
	transition:
		background 0.15s,
		border-color 0.15s,
		color 0.15s;
}
.action-btn:hover {
	background: var(--da-primary-soft);
	border-color: color-mix(in srgb, var(--da-primary) 45%, transparent);
	color: var(--da-primary);
}
.action-btn:focus-visible {
	outline: 2px solid var(--da-primary);
	outline-offset: 1px;
}
.pagination {
	display: flex;
	align-items: center;
	gap: 4px;
}
.pagination-info {
	font-size: 11.5px;
	color: var(--da-muted);
	padding: 0 4px;
	min-width: 36px;
	text-align: center;
}
.page-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	width: 28px;
	height: 28px;
	background: white;
	border: 1px solid var(--da-line-soft);
	border-radius: 6px;
	cursor: pointer;
	transition: background 0.15s;
}
.page-btn:hover:not(:disabled) {
	background: var(--da-surface-soft);
}
.page-btn:disabled {
	opacity: 0.4;
	cursor: not-allowed;
}
.page-btn:focus-visible {
	outline: 2px solid var(--da-primary);
	outline-offset: 1px;
}
.table-container {
	overflow: auto;
	max-height: 360px;
	border: 1px solid var(--da-line-soft);
	border-radius: 0 0 8px 8px;
	background: var(--da-surface);
}
.result-table {
	width: 100%;
	border-collapse: separate;
	border-spacing: 0;
	min-width: 100%;
}
.result-table th {
	position: sticky;
	top: 0;
	z-index: 1;
	background: var(--da-surface-soft);
	padding: 4px 6px;
	border-bottom: 2px solid var(--da-line-soft);
	font-weight: 600;
	color: var(--da-ink);
	font-size: 12px;
	text-align: left;
	white-space: nowrap;
}
.result-table td {
	padding: 4px 6px;
	border-bottom: 1px solid var(--da-surface-soft);
	color: var(--da-ink);
	font-size: 12px;
	max-width: 320px;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	vertical-align: top;
}
.result-table tr:last-child td {
	border-bottom: none;
}
.result-table tr:hover td {
	background: var(--da-surface-soft);
}
.cell-null {
	color: var(--da-muted);
	font-style: italic;
	font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
	font-size: 11.5px;
}
.cell-empty {
	color: var(--da-line);
}
.cell-num {
	font-size: 12px;
	text-align: right;
	font-variant-numeric: tabular-nums;
	font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
.custom-scrollbar::-webkit-scrollbar {
	height: 6px;
	width: 6px;
}
.custom-scrollbar::-webkit-scrollbar-track {
	background: transparent;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
	background: var(--da-line);
	border-radius: 4px;
}
@media (prefers-reduced-motion: reduce) {
	.result-pending-dot {
		animation: none;
	}
}

.result-header :deep(.v-btn) {
	min-width: 28px !important;
	width: 28px !important;
	height: 28px !important;
}
</style>
