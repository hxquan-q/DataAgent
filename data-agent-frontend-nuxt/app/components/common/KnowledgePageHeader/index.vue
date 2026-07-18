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
	<header class="d-flex align-center justify-space-between mb-4 knowledge-header">
		<div class="knowledge-header__text">
			<p v-if="kicker" class="knowledge-header__kicker">{{ kicker }}</p>
			<h1 class="knowledge-header__title">{{ title }}</h1>
			<p v-if="resolvedSubtitle" class="knowledge-header__subtitle">
				{{ resolvedSubtitle }}
			</p>
		</div>
		<div class="d-flex ga-2 knowledge-header__actions">
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
	/** Optional small uppercase label above title */
	kicker?: string;
}>();

const resolvedSubtitle = computed(() => props.subtitle || props.description || '');
</script>

<style scoped>
.knowledge-header {
	gap: 12px;
	flex-wrap: wrap;
	align-items: flex-start;
}

.knowledge-header__text {
	min-width: 0;
	flex: 1;
}

.knowledge-header__kicker {
	margin: 0 0 6px;
	font-size: 11px;
	font-weight: 600;
	letter-spacing: 0.1em;
	text-transform: uppercase;
	color: var(--da-muted);
}

.knowledge-header__title {
	margin: 0 0 6px;
	color: var(--da-ink);
	letter-spacing: -0.03em;
	font-family: var(--da-font-display);
	font-weight: 500;
	font-size: clamp(1.35rem, 2.2vw, 1.65rem);
	line-height: 1.25;
}

.knowledge-header__subtitle {
	color: var(--da-muted);
	line-height: 1.55;
	margin: 0;
	font-size: 13.5px;
	max-width: 52ch;
}

.knowledge-header__actions {
	flex-wrap: wrap;
	align-items: center;
}

.knowledge-header__actions :deep(.v-btn) {
	text-transform: none !important;
	letter-spacing: 0 !important;
	font-weight: 500 !important;
	border-radius: 10px !important;
	min-height: 34px !important;
}

.knowledge-header__actions :deep(.v-btn--variant-outlined) {
	border-color: color-mix(in srgb, var(--da-line) 55%, transparent) !important;
	color: var(--da-ink) !important;
}

.knowledge-header__actions :deep(.v-btn--variant-flat.v-btn--color-primary),
.knowledge-header__actions :deep(.v-btn.bg-primary) {
	box-shadow: none !important;
}
</style>
