# v17 对齐（开工前）：PRD-V4 / PRD-0003 / tests-checklist ★ vs v16 现状（Schema Gate + Agent 用例扩展）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD（Agent 接入）：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 测试对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 现状对齐（v16 结束）：`docs/align/v16-prd-v4-alignment.md`
- 日期：2026-02-16

## v16 结束后的总体判断

- ✅ 真实 Agent tool-loop 已跑通，并已切回 Responses 协议（兼容 upstream 固定 SSE）。
- ✅ 真机“人类可感知证据链”（frames/snapshots + mp4/report + sessions/events）已稳定可复现。
- ⚠️ 但 PRD-0003 路线中 v17+ 的两块关键工作仍缺：
  1) **Schema 漂移门禁**：`docs/tools/web-tools.openai.json` ↔ tool executor 的 openAiSchema 仍可能暗漂移（字段名/默认值/枚举/required）。
  2) **更多 agent-driven 用例**：当前仅 1 条 agent-run E2E；对 `web_query(kind)`、`web_scroll_into_view` 等 ★ 项覆盖不足。

## v17 开工前差异清单（摘差）

### 1) REQ-0003-002 / G2：Schema 漂移门禁缺失（核心差异）

- 现状（v16）：
  - 推荐 schema 文件存在：`docs/tools/web-tools.openai.json`
  - instrumentation 侧工具也实现了 openAiSchema（见 `OpenAgenticWebTools`），但二者是“重复定义” → 未来改动很容易漂移。
- PRD-0003 期望：
  - schema ↔ executor 自动对齐；有测试/门禁，避免“口径漂移”。
- v17 计划：
  - 将 web tools 的 OpenAI schema 抽成**单一来源**（Kotlin 侧可复用）。
  - 增加 JVM 测试：对比 `docs/tools/web-tools.openai.json` 与代码生成 schema 的语义一致性（忽略 key 顺序）。

### 2) tests-checklist ★：agent-driven 覆盖不足（需补）

- 现状（v16）：
  - Agent-run E2E 已覆盖：snapshot/click/fill/check/toggle + 最终断言（agree: true）。
- 仍需（按 ★ 优先）：
  - 覆盖 `web_query(kind=attrs/computed_styles/text/value/...)` 的真实 tool-loop 使用。
  - 覆盖 `web_scroll_into_view` 或 `web_scroll` 的真实 agent 使用（fixture 需提供足够长的页面/目标）。

## v17 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v17 对齐（完成回填）

## v17 完成情况（结果回填）

- ✅ WebTools 对外 API 完成一次“Core 对齐”收敛，且 **web tools 总数=21（≤ 25）**：
  - 新增导航：`web_open/web_back/web_forward/web_reload`
  - 新增/补齐 core：`web_dblclick/web_type/web_wait/web_screenshot/web_eval(web 默认禁用)/web_close`
  - `web_query.kind` 扩展：`isvisible/isenabled/ischecked`
- ✅ Schema 漂移门禁已落地：
  - 代码侧 schema 单一来源：`agent-browser-kotlin/src/main/kotlin/com/lsl/agentbrowser/openai/WebToolsOpenAiSchema.kt`
  - 合同文件：`docs/tools/web-tools.openai.json`
  - 门禁测试：`agent-browser-kotlin/src/test/kotlin/com/lsl/agentbrowser/openai/WebToolsOpenAiSchemaContractTest.kt`
- ✅ 对外交付的 system prompt 模板补齐（移动端优先 + few-shot）：
  - `docs/prompt/webview-webtools-system-prompt.md`
- ✅ 真机执行验证通过（含真实设备连接的 connectedAndroidTest）。

## v17 证据指针

- JVM（含 schema contract test）：`docs/evidence/2026-02-16-v17-agent-browser-kotlin-test-rerun.txt`
- 真机：`docs/evidence/2026-02-16-v17-connectedAndroidTest.txt`
- AndroidTest Kotlin 编译（无 warnings）：`docs/evidence/2026-02-16-v17-compileDebugAndroidTestKotlin.txt`

## v17 仍未完全对齐（剩余差异）

- PRD-0003 路线中的“更多 agent tasks / 更强 prompt 策略 / 失败自愈（ref 失效、overlay、超时重试等）”仍可继续用 v18+ 推进（但本轮已补齐对外 API + prompt 模板 + schema gate）。
