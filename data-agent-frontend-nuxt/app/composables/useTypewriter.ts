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

import { ref, onBeforeUnmount } from 'vue';

/**
 * Typewriter effect — adaptive chars/frame so long reports don't feel stuck.
 * Small chunks: ~3–8 chars/frame; backlog large: flush faster up to 64/frame.
 * Stream complete should still call flush().
 */

const BASE_CHARS = 12;
const MAX_CHARS = 64;

export function useTypewriter() {
	const displayedText = ref('');

	let enqueuedLength = 0;
	let sourceText = '';
	let rafId: number | null = null;
	let isActive = true;

	function charsThisFrame(): number {
		const backlog = enqueuedLength - displayedText.value.length;
		if (backlog <= 0) return BASE_CHARS;
		// catch up when SSE/report dumps large chunks (root cause of "慢")
		if (backlog > 2000) return MAX_CHARS;
		if (backlog > 800) return 40;
		if (backlog > 300) return 24;
		if (backlog > 100) return 20;
		return BASE_CHARS;
	}

	function tick() {
		rafId = null;
		if (!isActive) return;

		const current = displayedText.value.length;
		const target = enqueuedLength;

		if (current < target) {
			const end = Math.min(current + charsThisFrame(), target);
			displayedText.value = sourceText.slice(0, end);
		}

		if (displayedText.value.length < enqueuedLength) {
			rafId = requestAnimationFrame(tick);
		}
	}

	function scheduleTick() {
		if (!rafId && isActive) {
			rafId = requestAnimationFrame(tick);
		}
	}

	function append(chunk: string) {
		sourceText += chunk;
		enqueuedLength = sourceText.length;
		scheduleTick();
	}

	function reset() {
		if (rafId) {
			cancelAnimationFrame(rafId);
			rafId = null;
		}
		sourceText = '';
		enqueuedLength = 0;
		displayedText.value = '';
	}

	function flush() {
		if (rafId) {
			cancelAnimationFrame(rafId);
			rafId = null;
		}
		displayedText.value = sourceText;
		enqueuedLength = sourceText.length;
	}

	onBeforeUnmount(() => {
		isActive = false;
		if (rafId) cancelAnimationFrame(rafId);
	});

	return { displayedText, append, reset, flush };
}
