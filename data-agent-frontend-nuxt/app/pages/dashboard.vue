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
  <section class="page-shell dashboard-placeholder">
    <div class="dashboard-card">
    <p class="dashboard-kicker">Data Agent</p>
    <h1>数据看板</h1>
    <p class="dashboard-desc">
      业务可视化看板位。当前请从「数据问答」发起分析，报告与图表会直接出现在对话结果中。
    </p>
    <div class="dashboard-actions">
      <NuxtLink to="/chat" class="dashboard-link">前往数据问答</NuxtLink>
    </div>
      </div>
  </section>
</template>

<style scoped>
 .dashboard-placeholder {
  max-width: 760px;
}
.dashboard-card {
  padding: 28px 24px;
  background: var(--da-surface);
  border: 1px solid var(--da-line-soft);
  border-radius: var(--da-radius-lg);
  box-shadow: var(--da-shadow-md);
}
.dashboard-kicker {
  margin: 0 0 8px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--da-muted);
}
.dashboard-placeholder h1 {
  margin: 0 0 12px;
  font-family: var(--da-font-display);
  font-size: 28px;
  font-weight: 500;
  letter-spacing: -0.02em;
  color: var(--da-ink);
}
.dashboard-desc {
  margin: 0 0 20px;
  font-size: 14.5px;
  line-height: 1.7;
  color: var(--da-muted);
}
.dashboard-link {
  display: inline-flex;
  align-items: center;
  min-height: 40px;
  padding: 0 16px;
  border-radius: 999px;
  background: var(--da-primary);
  color: var(--da-on-primary);
  font-size: 13.5px;
  font-weight: 600;
  text-decoration: none;
}
.dashboard-link:focus-visible {
  outline: 2px solid var(--da-ring);
  outline-offset: 2px;
}
</style>
