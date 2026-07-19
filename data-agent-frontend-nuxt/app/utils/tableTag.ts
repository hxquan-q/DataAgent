/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

/**
 * @description 表名标记协议（抄 Chat2DB）：解析 LLM 输出中的 [table::tableName] 标记，
 * 转为可高亮的 HTML span，供报告/反问消息前端渲染。
 */

/** [table::xxx] 标记正则 */
const TABLE_TAG_RE = /\[table::([^\]]+)\]/g;

/**
 * 将文本中的 [table::tableName] 标记转为 <span class="table-tag">tableName</span>。
 * 先 HTML 转义原文，再替换标记，避免 XSS（标记内的表名也转义）。
 * @param text 原始文本（可能含 [table::xxx]）
 * @returns HTML 字符串（标记已转为 span，可配合 DOMPurify 使用）
 */
export function renderTableTags(text: string | null | undefined): string {
	if (!text) {
		return '';
	}
	const escaped = escapeHtml(text);
	return escaped.replace(TABLE_TAG_RE, (_m, name) => {
		return `<span class="table-tag">${escapeHtml(String(name))}</span>`;
	});
}

/**
 * 在已渲染的 HTML 字符串中把文本节点的 [table::tableName] 标记转为
 * <span class="table-tag">tableName</span>。
 *
 * 适用场景：markdown-it 等已把 Markdown 渲染成 HTML 后的后处理。
 * 与 {@link renderTableTags} 不同，本函数 **不** 对整串做 HTML 转义
 * （否则会破坏既有标签，如把 <p> 变成 &lt;p&gt;）。
 * 仅对标记内的表名做转义，避免 XSS。
 *
 * 安全说明：[table::...] 使用方括号语法，而 HTML 标签使用尖括号 <...>，
 * 二者语法空间不重叠，因此对整串做正则替换不会误伤已有标签；
 * 表名（捕获组）经 escapeHtml 转义，最终由 DOMPurify 再做一次消毒兜底。
 *
 * @param html 已渲染的 HTML 字符串（含文本态 [table::xxx]）
 * @returns 标记替换后的 HTML 字符串
 */
export function transformTableTagsInHtml(
	html: string | null | undefined,
): string {
	if (!html) {
		return '';
	}
	return html.replace(TABLE_TAG_RE, (_m, name) => {
		return `<span class="table-tag">${escapeHtml(String(name))}</span>`;
	});
}

/**
 * 提取文本中所有 [table::xxx] 的表名（供调试/统计）。
 * @param text 原始文本
 * @returns 表名数组
 */
export function extractTableNames(text: string | null | undefined): string[] {
	if (!text) {
		return [];
	}
	const names: string[] = [];
	let m: RegExpExecArray | null;
	TABLE_TAG_RE.lastIndex = 0;
	while ((m = TABLE_TAG_RE.exec(text)) !== null) {
		names.push(m[1]);
	}
	return names;
}

function escapeHtml(s: string): string {
	return s
		.replace(/&/g, '&amp;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
		.replace(/"/g, '&quot;');
}
