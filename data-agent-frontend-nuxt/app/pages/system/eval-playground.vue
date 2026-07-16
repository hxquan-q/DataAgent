<!--
  Copyright 2026 the original author or authors.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<!-- 评测游乐场：在线计算 NL2SQL 检索/生成质量指标（Recall/MRR/NDCG + BLEU/ROUGE）。参考 WeKnora 缺失的评测可视化。 -->
<template>
	<div class="page-shell pa-4 pa-md-6">
		<KnowledgePageHeader title="评测游乐场" description="在线计算 NL2SQL 检索/生成质量指标（召回率/MRR/NDCG + BLEU/ROUGE），辅助语义层与召回调优">
			<template #actions>
				<v-chip color="primary" variant="tonal" size="small">v0.2 · eval</v-chip>
			</template>
		</KnowledgePageHeader>

		<v-card variant="flat" class="mt-4" border>
			<v-tabs v-model="tab" color="primary" density="compact">
				<v-tab value="generation">
					<v-icon start>mdi-file-document-check</v-icon>
					生成评测（BLEU / ROUGE）
				</v-tab>
				<v-tab value="retrieval">
					<v-icon start>mdi-database-search</v-icon>
					检索评测（Recall / MRR / NDCG）
				</v-tab>
			</v-tabs>
			<v-divider />
			<v-window v-model="tab" class="pa-4">
				<!-- 生成评测 -->
				<v-window-item value="generation">
					<v-row>
						<v-col cols="12" md="6">
							<v-textarea v-model="gen.candidate" label="候选（生成的 SQL / 文本）" rows="5" variant="outlined"
								hint="生成的 SQL 或答案" persistent-hint />
						</v-col>
						<v-col cols="12" md="6">
							<v-textarea v-model="gen.reference" label="参考（黄金 SQL / 文本）" rows="5" variant="outlined"
								hint="标准答案" persistent-hint />
						</v-col>
					</v-row>
					<v-row dense align="center">
						<v-col cols="auto">
							<v-select v-model="gen.tokenize" :items="['sql', 'words']" label="分词" density="compact"
								style="min-width: 120px" hide-details />
						</v-col>
						<v-col cols="auto">
							<v-select v-model="gen.n" :items="[1, 2, 3, 4]" label="n-gram 阶数" density="compact"
								style="min-width: 130px" hide-details />
						</v-col>
						<v-col cols="auto">
							<v-checkbox v-model="gen.smoothing" label="BLEU 平滑" density="compact" hide-details />
						</v-col>
						<v-spacer />
						<v-col cols="auto">
							<v-btn color="primary" :loading="gen.loading" @click="runGeneration">
								<v-icon start>mdi-calculator</v-icon>
								计算
							</v-btn>
						</v-col>
					</v-row>

					<v-alert v-if="gen.error" type="error" variant="tonal" class="mt-3" closable
						:text="gen.error" />
					<v-row v-if="gen.result" class="mt-2" dense>
						<v-col v-for="m in genMetrics" :key="m.label" cols="6" sm="4" md="3">
							<v-card variant="tonal" class="text-center pa-3">
								<div class="text-caption text-medium-emphasis">{{ m.label }}</div>
								<div class="text-h6 font-weight-bold" :class="scoreColor(m.value)">
									{{ m.value.toFixed(4) }}
								</div>
							</v-card>
						</v-col>
						<v-col cols="12" class="text-caption text-medium-emphasis">
							tokens：候选 {{ gen.result.candidateTokens }} / 参考 {{ gen.result.referenceTokens }}
						</v-col>
					</v-row>
				</v-window-item>

				<!-- 检索评测 -->
				<v-window-item value="retrieval">
					<v-row>
						<v-col cols="12" md="6">
							<v-textarea v-model="ret.retrievedRaw" label="召回列表（每行一个 ID，顺序即排序）" rows="6"
								variant="outlined" hint="如 schema/指标 ID，按召回排序" persistent-hint />
						</v-col>
						<v-col cols="12" md="6">
							<v-textarea v-model="ret.relevantRaw" label="黄金相关列表（每行一个 ID）" rows="6"
								variant="outlined" hint="该查询应当召回的 ID 集合" persistent-hint />
						</v-col>
					</v-row>
					<v-row dense align="center">
						<v-col cols="auto">
							<v-text-field v-model.number="ret.k" type="number" label="top-k" density="compact"
								style="min-width: 100px" hide-details />
						</v-col>
						<v-spacer />
						<v-col cols="auto">
							<v-btn color="primary" :loading="ret.loading" @click="runRetrieval">
								<v-icon start>mdi-calculator</v-icon>
								计算
							</v-btn>
						</v-col>
					</v-row>

					<v-alert v-if="ret.error" type="error" variant="tonal" class="mt-3" closable
						:text="ret.error" />
					<v-row v-if="ret.result" class="mt-2" dense>
						<v-col v-for="m in retMetrics" :key="m.label" cols="6" sm="4" md="3">
							<v-card variant="tonal" class="text-center pa-3">
								<div class="text-caption text-medium-emphasis">{{ m.label }}</div>
								<div class="text-h6 font-weight-bold" :class="scoreColor(m.value)">
									{{ m.value.toFixed(4) }}
								</div>
							</v-card>
						</v-col>
					</v-row>
				</v-window-item>
			</v-window>
		</v-card>
	</div>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue';
import evalService, {
	type GenerationScores,
	type RetrievalScores,
} from '~/services/eval/index';

definePageMeta({ layout: 'default' });

const tab = ref<'generation' | 'retrieval'>('generation');

// —— 生成评测 ——
const gen = reactive({
	candidate: "select order_id, amount from orders where status = 'completed'",
	reference: "select order_id, amount from orders",
	tokenize: 'sql',
	n: 4,
	smoothing: true,
	loading: false,
	result: null as GenerationScores | null,
	error: '' as string,
});

const genMetrics = computed(() => {
	const r = gen.result;
	if (!r) return [];
	return [
		{ label: 'BLEU-' + gen.n, value: r.bleu },
		{ label: 'ROUGE-N 精确率', value: r.rougePrecision },
		{ label: 'ROUGE-N 召回率', value: r.rougeRecall },
		{ label: 'ROUGE-N F1', value: r.rougeFMeasure },
		{ label: 'ROUGE-L 精确率', value: r.rougeLPrecision },
		{ label: 'ROUGE-L 召回率', value: r.rougeLRecall },
		{ label: 'ROUGE-L F1', value: r.rougeLFMeasure },
	];
});

async function runGeneration() {
	gen.error = '';
	if (!gen.candidate.trim() || !gen.reference.trim()) {
		gen.error = '候选与参考文本均不能为空';
		return;
	}
	gen.loading = true;
	try {
		gen.result = await evalService.generation({
			candidate: gen.candidate,
			reference: gen.reference,
			tokenize: gen.tokenize,
			n: gen.n,
			smoothing: gen.smoothing,
		});
	}
	catch (e: any) {
		gen.error = e?.response?.data?.message || e?.message || '请求失败';
	}
	finally {
		gen.loading = false;
	}
}

// —— 检索评测 ——
const ret = reactive({
	retrievedRaw: 'orders\norder_items\nusers\nproducts',
	relevantRaw: 'orders\norder_items',
	k: 10,
	loading: false,
	result: null as RetrievalScores | null,
	error: '' as string,
});

const retMetrics = computed(() => {
	const r = ret.result;
	if (!r) return [];
	return [
		{ label: 'Recall', value: r.recall },
		{ label: 'Precision', value: r.precision },
		{ label: `Recall@${ret.k}`, value: r.recallAtK },
		{ label: `Precision@${ret.k}`, value: r.precisionAtK },
		{ label: 'MRR', value: r.mrr },
		{ label: 'MAP', value: r.map },
		{ label: `NDCG@${ret.k}`, value: r.ndcgAtK },
	];
});

function parseLines(raw: string): string[] {
	return raw.split(/\r?\n/).map(s => s.trim()).filter(Boolean);
}

async function runRetrieval() {
	ret.error = '';
	const retrieved = parseLines(ret.retrievedRaw);
	const relevant = parseLines(ret.relevantRaw);
	if (retrieved.length === 0 || relevant.length === 0) {
		ret.error = '召回列表与黄金列表均不能为空';
		return;
	}
	ret.loading = true;
	try {
		ret.result = await evalService.retrieval({ retrieved, relevant, k: ret.k });
	}
	catch (e: any) {
		ret.error = e?.response?.data?.message || e?.message || '请求失败';
	}
	finally {
		ret.loading = false;
	}
}

function scoreColor(v: number): string {
	if (v >= 0.8) return 'text-success';
	if (v >= 0.5) return 'text-warning';
	if (v > 0) return 'text-error';
	return 'text-medium-emphasis';
}
</script>
