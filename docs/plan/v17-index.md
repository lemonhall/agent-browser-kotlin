# v17 计划：Schema 漂移门禁 + Agent 用例扩展（对齐 tests-checklist ★）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 测试对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v17-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **Schema 单一来源 + 门禁测试**：避免 `docs/tools/web-tools.openai.json` 与工具实现暗漂移。
2) **扩展 agent-driven E2E 覆盖**：让真实 Agent 使用 `web_query(kind)` 与 `web_scroll_into_view`（优先覆盖 ★ 项）。
3) **证据链不回退**：继续产出 mp4/report + snapshots + sessions/events。

## 里程碑（完成判定）

- M1：代码侧提供可复用的 web-tools OpenAI schema（单一来源），工具实现不再手写重复 schema。
- M2：新增 JVM 测试：语义对齐 `docs/tools/web-tools.openai.json`（忽略 JSON key 顺序），不一致则失败。
- M3：新增至少 1 条 agent-run instrumentation E2E，用到 `web_query` 与 `web_scroll_into_view`，并保留人类可感知证据。
- M4：本轮验证日志写入 `docs/evidence/`。

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

