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

<!--
	DiffRow —— 口径版本对比的单行字段渲染。
	- value 为空显示「—」占位，避免空白
	- changed=true 时整行高亮（warning 底色 + 左侧色条），直观标记差异点
	- mono=true 时等宽字体（用于 SQL/JSON 片段）
-->
<template>
	<div class="diff-row" :class="{ 'diff-row--changed': changed }">
		<div class="text-caption text-medium-emphasis mb-1">
			{{ label }}
		</div>
		<div
			class="text-body-2"
			:class="{ 'font-mono': mono, 'text-medium-emphasis': !value }"
		>
			{{ value || '—' }}
		</div>
	</div>
</template>

<script setup lang="ts">
defineProps<{
	label: string;
	value?: string | null;
	/** 该字段相对另一版本是否变更（高亮标记） */
	changed?: boolean;
	/** 等宽字体（SQL / JSON 片段） */
	mono?: boolean;
}>();
</script>

<style scoped>
.diff-row {
	padding: 8px 10px;
	border-radius: var(--da-radius-sm);
	margin-bottom: 6px;
	transition: background-color var(--da-dur-fast) var(--da-ease-out);
}

.diff-row--changed {
	background-color: color-mix(in srgb, var(--da-warning) 12%, white);
	border-left: 3px solid var(--da-warning);
}

.font-mono {
	font-family: var(--da-font-mono);
	font-size: 0.8125rem;
}
</style>
