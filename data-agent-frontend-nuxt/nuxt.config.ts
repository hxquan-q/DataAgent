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

// https://nuxt.com/docs/api/configuration/nuxt-config
export default defineNuxtConfig({
	compatibilityDate: '2025-07-15',
	devtools: { enabled: true },
	modules: ['vuetify-nuxt-module', '@pinia/nuxt', '@nuxt/eslint'],
	//基于组件名称自动导入
	components: [
		{
			path: '~/components', // 扫描 components 目录
			extensions: ['.vue'], // 确保只扫描 .vue 文件
			pathPrefix: false, // 禁用文件夹路径前缀
		},
	],
	imports: {
		dirs: [
			// 递归扫描所有的 index.ts，这样文件夹名就是函数名
			'composables/**/index.ts',
			'app/services/**/index.ts', // 匹配你规范中的 app/services/
			'composables/*.ts',
			'app/services/*.ts',
		],
	},
	vuetify: {
		vuetifyOptions: {
			// Keep static hex in sync with app/assets/css/tokens.css (rebuild-ui · DEEIX azure).
			theme: {
				defaultTheme: 'light',
				themes: {
					light: {
						dark: false,
						colors: {
							primary: '#2F84D6',
							secondary: '#3B9EEA',
							accent: '#3B9EEA',
							error: '#DC2626',
							info: '#3B9EEA',
							success: '#16A34A',
							warning: '#D97706',
							background: '#F4F8FB',
							surface: '#FFFFFF',
						},
					},
				},
			},
			defaults: {
				VBtn: { variant: 'flat', rounded: 'lg' },
			},
		},
	},
	//全局关闭ssr
	ssr: false,
	// 混合开发：公网 IP / 域名访问（经 3301 nginx 或直连 3000）
	// allowedHosts 避免 Vite 拦截 Host 头；HMR 走当前访问的 host
	vite: {
		server: {
			host: '0.0.0.0',
			// 允许公网 IP / 任意 Host（开发环境）
			allowedHosts: true,
			hmr: {
				// 经 3301 反代时由客户端自动用当前页面 host；直连 3000 也可用
				clientPort: undefined,
			},
		},
	},
	// /路由重定向到/create-agent
	routeRules: {
		'/': { redirect: '/agent/new' },
		// 代理所有 /api/** 的请求到 Java 后端（Nuxt 服务端转发，浏览器仍同源）
		// 公网访问 3000/3301 时，/api 仍转到本机 IDEA 8065
		'/api/**': { proxy: 'http://127.0.0.1:8065/api/**' },
		'/nl2sql/**': { proxy: 'http://127.0.0.1:8065/nl2sql/**' },
	},
	//全局动画配置
	app: {
		pageTransition: { name: 'page', mode: 'out-in' },
	},
	css: ['@/assets/css/main.css'],
});
