/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 */

import { authHeaders } from '~/utils/authToken';

export type SseMessage = {
	event: string;
	data: string;
	id?: string;
};

export type OpenSseOptions = {
	/** Extra headers (Authorization injected by default) */
	headers?: Record<string, string>;
	/** Called for every SSE message (including named events) */
	onMessage: (msg: SseMessage) => void | Promise<void>;
	/** HTTP/network errors only — not called on clean stream end or user abort */
	onError?: (error: Error) => void | Promise<void>;
	/** Stream body finished cleanly (after last message). Not called on user abort. */
	onDone?: () => void | Promise<void>;
	signal?: AbortSignal;
	/**
	 * If no SSE message was received and the failure looks like a transient network
	 * error (not 4xx), retry once after a short delay. Default 0.
	 */
	retryOnEmptyNetwork?: 0 | 1;
};

/**
 * fetch-based SSE. Prefer over EventSource:
 * - can set Authorization header
 * - no auto-reconnect false errors when server closes after complete
 * - user abort does not fire onError/onDone
 */
export function openSseStream(url: string, options: OpenSseOptions): () => void {
	const ac = new AbortController();
	const signal = options.signal
		? anySignal([options.signal, ac.signal])
		: ac.signal;

	let closed = false; // user or caller closed
	let settled = false; // onError/onDone already delivered

	const close = () => {
		if (closed) return;
		closed = true;
		ac.abort();
	};

	const settleError = async (err: Error) => {
		if (settled || closed) return;
		settled = true;
		if (options.onError) await options.onError(err);
	};

	const settleDone = async () => {
		if (settled || closed) return;
		settled = true;
		if (options.onDone) await options.onDone();
	};

	void (async () => {
		const maxAttempts = (options.retryOnEmptyNetwork ?? 0) + 1;
		let attempt = 0;
		let receivedAny = false;

		while (attempt < maxAttempts) {
			attempt += 1;
			if (closed) return;

			try {
				const res = await fetch(url, {
					method: 'GET',
					headers: authHeaders({
						Accept: 'text/event-stream',
						'Cache-Control': 'no-cache',
						...(options.headers || {}),
					}),
					signal,
					credentials: 'same-origin',
				});

				if (!res.ok) {
					let detail = '';
					try {
						detail = (await res.text()).slice(0, 200);
					} catch {
						/* ignore */
					}
					const err = new Error(
						res.status === 401
							? '未认证或登录已过期，请重新登录。'
							: res.status === 403
								? '没有权限访问该流。'
								: `流式请求失败 (${res.status})${detail ? `: ${detail}` : ''}`,
					);
					// 4xx: do not retry
					await settleError(err);
					return;
				}

				if (!res.body) {
					await settleError(new Error('浏览器不支持流式响应'));
					return;
				}

				const reader = res.body.getReader();
				const decoder = new TextDecoder('utf-8');
				let buffer = '';
				let eventName = 'message';
				let dataLines: string[] = [];
				let id: string | undefined;

				const flush = async () => {
					if (dataLines.length === 0) {
						eventName = 'message';
						id = undefined;
						return;
					}
					const data = dataLines.join('\n');
					dataLines = [];
					const msg: SseMessage = { event: eventName || 'message', data, id };
					eventName = 'message';
					id = undefined;
					receivedAny = true;
					await options.onMessage(msg);
				};

				while (true) {
					if (closed) {
						try {
							await reader.cancel();
						} catch {
							/* ignore */
						}
						return; // user abort: no onDone/onError
					}
					const { done, value } = await reader.read();
					if (done) break;
					buffer += decoder.decode(value, { stream: true });
					buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');

					let nl: number;
					while ((nl = buffer.indexOf('\n')) >= 0) {
						const line = buffer.slice(0, nl);
						buffer = buffer.slice(nl + 1);

						if (line === '') {
							await flush();
							continue;
						}
						if (line.startsWith(':')) continue;
						if (line.startsWith('event:')) {
							eventName = line.slice(6).trim();
							continue;
						}
						if (line.startsWith('data:')) {
							const v = line.slice(5);
							dataLines.push(v.startsWith(' ') ? v.slice(1) : v);
							continue;
						}
						if (line.startsWith('id:')) {
							id = line.slice(3).trim();
							continue;
						}
					}
				}

				await flush();
				await settleDone();
				return;
			} catch (e) {
				if (closed || (e instanceof DOMException && e.name === 'AbortError')) {
					return;
				}
				const err = e instanceof Error ? e : new Error(String(e));
				const canRetry =
					!receivedAny &&
					attempt < maxAttempts &&
					isTransientNetworkError(err);
				if (canRetry) {
					await sleep(400);
					continue;
				}
				await settleError(err);
				return;
			}
		}
	})();

	return close;
}

function isTransientNetworkError(err: Error): boolean {
	const m = (err.message || '').toLowerCase();
	return (
		err.name === 'TypeError' ||
		/network|fetch|failed to fetch|load failed|econnreset|etimedout|socket/i.test(m)
	);
}

function sleep(ms: number) {
	return new Promise((r) => setTimeout(r, ms));
}

function anySignal(signals: AbortSignal[]): AbortSignal {
	const ac = new AbortController();
	for (const s of signals) {
		if (s.aborted) {
			ac.abort();
			return ac.signal;
		}
		s.addEventListener('abort', () => ac.abort(), { once: true });
	}
	return ac.signal;
}
