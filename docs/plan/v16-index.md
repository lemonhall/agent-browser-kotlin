# v16 计划：切回 OpenAI Responses（兼容 upstream SSE）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 扩展 PRD：`docs/prd/PRD-0003-openagentic-agent-e2e.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v16-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **把真实 Agent 的 Provider 协议从 legacy 切回 Responses**（符合 PRD-0003 G1）。
2) **兼容 upstream 只返回 SSE 的 /responses**：即使服务端固定 `text/event-stream`，也能跑通 tool-loop（不再因“非 JSON”失败）。
3) **保持证据链不回退**：mp4/report + snapshots + sessions/events 继续可拉取、可审阅。

## 里程碑（完成判定）

- M1：新增/替换 Responses provider：支持 `application/json` 与 `text/event-stream` 两种返回。
- M2：`WebViewAgentE2eTest` 默认使用 Responses provider 跑通，并保留一个可控回退开关（仅用于排障）。
- M3：本轮验证日志写入 `docs/evidence/`。

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

