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
	<header class="d-flex align-center justify-space-between mb-5 knowledge-header">
		<div class="knowledge-header__text">
			<h1 class="text-h4 mb-1 knowledge-header__title">{{ title }}</h1>
			<p v-if="resolvedSubtitle" class="text-body-2 knowledge-header__subtitle">
				{{ resolvedSubtitle }}
			</p>
		</div>
		<div class="d-flex ga-3 knowledge-header__actions">
			<slot name="actions" />
		</div>
	</header>
</template>

<script setup lang="ts">
const props = defineProps<{
	title: string;
	/** Preferred prop for page description */
	subtitle?: string;
	/** Alias used by some system pages (security / eval) */
	description?: string;
}>();

const resolvedSubtitle = computed(() => props.subtitle || props.description || '');
</script>

<style scoped>
.knowledge-header {
	gap: 12px;
	flex-wrap: wrap;
}

.knowledge-header__text {
	min-width: 0;
	flex: 1;
}

.knowledge-header__title {
	color: var(--da-primary, #2f84d6);
	letter-spacing: -0.02em;
	font-family: var(--da-font-display);
	font-weight: 500 !important;
}

.knowledge-header__subtitle {
	color: var(--da-muted, #64748b);
	line-height: 1.55;
	margin: 0;
}

.knowledge-header__actions {
	flex-wrap: wrap;
}
</style>
