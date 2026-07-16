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

import { nextTick, onBeforeUnmount } from 'vue';

/**
 * Lazy-load echarts only when a chart node appears.
 * Sync `import * as echarts` forced ~2.8MB into embed init in Vite dev — that was the cold start tax.
 */

type EChartsNS = typeof import('echarts');

const EXTENDED_COLORS = [
	'#5584FF',
	'#36CBCB',
	'#4ECB74',
	'#FAD337',
	'#F2637B',
	'#975FEE',
	'#5470c6',
	'#91cc75',
	'#fac858',
	'#ee6666',
	'#73c0de',
	'#3ba272',
	'#fc8452',
	'#9a60b4',
	'#ea7ccc',
	'#0082fc',
	'#fdd845',
	'#22ed7c',
	'#1d27c9',
	'#05f8d6',
	'#f9e264',
	'#f47a75',
	'#009db2',
];

let echartsMod: EChartsNS | null = null;
let echartsLoading: Promise<EChartsNS> | null = null;

function loadEcharts(): Promise<EChartsNS> {
	if (echartsMod) return Promise.resolve(echartsMod);
	if (!echartsLoading) {
		echartsLoading = import('echarts').then((m) => {
			echartsMod = m;
			return m;
		});
	}
	return echartsLoading;
}

async function renderEChartsInContainer(container: HTMLElement) {
	const elements = container.querySelectorAll<HTMLElement>('.md-echarts');
	if (!elements.length) return;

	const echarts = await loadEcharts();

	elements.forEach((el) => {
		try {
			const rawConfig = el.getAttribute('data-echarts-config');
			if (!rawConfig) return;

			const code = rawConfig
				.replace(/&quot;/g, '"')
				.replace(/&lt;/g, '<')
				.replace(/&gt;/g, '>')
				.replace(/&amp;/g, '&');

			if (!code || code.trim() === '') return;

			const options = new Function(`return (${code})`)() as Record<string, unknown>;
			if (!options.color) {
				options.color = EXTENDED_COLORS;
			}

			el.removeAttribute('data-echarts-config');
			el.textContent = '';

			const existingChart = echarts.getInstanceByDom(el);
			if (existingChart) {
				existingChart.setOption(options, true);
			} else {
				const chart = echarts.init(el);
				chart.setOption(options);
			}
		} catch (e) {
			console.error('ECharts rendering error:', e);
		}
	});
}

function disposeEChartsInContainer(container: HTMLElement | null) {
	if (!container || !echartsMod) return;
	const elements = container.querySelectorAll<HTMLElement>('.md-echarts');
	elements.forEach((el) => {
		const chart = echartsMod!.getInstanceByDom(el);
		if (chart) chart.dispose();
	});
}

export function useEchartsRenderer() {
	let debounceTimer: ReturnType<typeof setTimeout> | null = null;
	const chartContainers: HTMLElement[] = [];

	function renderECharts(container: HTMLElement | null) {
		if (!container) return;
		if (!chartContainers.includes(container)) chartContainers.push(container);

		if (debounceTimer) clearTimeout(debounceTimer);
		debounceTimer = setTimeout(() => {
			debounceTimer = null;
			nextTick(() => {
				void renderEChartsInContainer(container);
			});
		}, 200);
	}

	onBeforeUnmount(() => {
		if (debounceTimer) clearTimeout(debounceTimer);
		chartContainers.forEach((c) => disposeEChartsInContainer(c));
	});

	return { renderECharts, disposeEChartsInContainer };
}
