/*
 * Copyright 2024-2026 the original author or authors.
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

/**
 * @description 图搜索服务：fetch SSE（Bearer 鉴权，无 EventSource 重连误报）
 */

import { openSseStream } from '~/utils/sse';

export interface GraphRequest {
	agentId: string;
	threadId?: string;
	query: string;
	humanFeedback: boolean;
	humanFeedbackContent?: string;
	rejectedPlan: boolean;
	nl2sqlOnly: boolean;
}

export interface GraphNodeResponse {
	agentId: string;
	threadId: string;
	nodeName: string;
	textType: TextType;
	text: string;
	error: boolean;
	complete: boolean;
}

export enum TextType {
	JSON = 'JSON',
	PYTHON = 'PYTHON',
	SQL = 'SQL',
	HTML = 'HTML',
	MARK_DOWN = 'MARK_DOWN',
	RESULT_SET = 'RESULT_SET',
	TEXT = 'TEXT',
}

const API_BASE_URL = '/api';

class GraphService {
	/**
	 * @returns 手动关闭流的函数（用户停止：不触发 onError/onComplete）
	 */
	async streamSearch(
		request: GraphRequest,
		onMessage: (response: GraphNodeResponse) => Promise<void>,
		onError?: (error: Error) => Promise<void>,
		onComplete?: () => Promise<void>,
	): Promise<() => void> {
		const params = new URLSearchParams();
		params.append('agentId', request.agentId);
		if (request.threadId) params.append('threadId', request.threadId);
		params.append('query', request.query);
		params.append('humanFeedback', String(request.humanFeedback));
		params.append('rejectedPlan', String(request.rejectedPlan));
		params.append('nl2sqlOnly', String(request.nl2sqlOnly));
		if (request.humanFeedbackContent) {
			params.append('humanFeedbackContent', request.humanFeedbackContent);
		}

		const url = `${API_BASE_URL}/stream/search?${params.toString()}`;
		/** terminal: complete | error | user-stop */
		let finished = false;
		let stoppedByUser = false;

		const markFinished = () => {
			finished = true;
		};

		const close = openSseStream(url, {
			retryOnEmptyNetwork: 1,
			onMessage: async (msg) => {
				if (finished || stoppedByUser) return;

				if (msg.event === 'complete') {
					markFinished();
					if (onComplete) await onComplete();
					return;
				}

				if (msg.event === 'error') {
					let text = msg.data || 'Stream error';
					try {
						const parsed = JSON.parse(msg.data) as GraphNodeResponse;
						if (parsed?.text) text = parsed.text;
						if (parsed?.error) {
							// surface as stream error only
						}
					} catch {
						/* plain text */
					}
					markFinished();
					if (onError) await onError(new Error(text));
					return;
				}

				if (!msg.data) return;
				try {
					const nodeResponse = JSON.parse(msg.data) as GraphNodeResponse;
					if (nodeResponse.complete) {
						markFinished();
						if (onComplete) await onComplete();
						return;
					}
					if (nodeResponse.error) {
						markFinished();
						if (onError) {
							await onError(new Error(nodeResponse.text || 'Stream error'));
						}
						return;
					}
					await onMessage(nodeResponse);
				} catch (parseError) {
					console.error('Failed to parse SSE data:', parseError);
					if (!finished && !stoppedByUser) {
						markFinished();
						if (onError) await onError(new Error('Failed to parse server response'));
					}
				}
			},
			onError: async (error) => {
				if (finished || stoppedByUser) return;
				markFinished();
				if (onError) await onError(error);
			},
			onDone: async () => {
				// body ended without explicit complete event
				if (finished || stoppedByUser) return;
				markFinished();
				if (onComplete) await onComplete();
			},
		});

		return () => {
			// user stop: silence callbacks, abort fetch
			stoppedByUser = true;
			finished = true;
			close();
		};
	}
}

export default new GraphService();
