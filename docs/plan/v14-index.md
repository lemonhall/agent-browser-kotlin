# v14 计划：tests-checklist（★）证据链“可信 + 可浏览”

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 对齐清单：`docs/prd/archive/tests-checklist.md`（优先 ★）
- 对齐摘差：`docs/align/v14-prd-v4-alignment.md`
- 日期：2026-02-16

## 目标（本轮要解决什么）

1) **修复真机证据拉取脚本的“run 串台”风险**：支持 `run-<millis>-<suffix>-step-XX.png`，确保 `adb_dumps/e2e/latest/` 的 mp4/snapshots/report 与本次执行严格对应。
2) **把证据变成人类可感知、可快速审阅的形态**：生成离线可打开的 `report.html`（按 step 展示截图 + snapshot 文本 + JSON 链接），并在 `adb_dumps/e2e/latest/` 提供入口页。

## 里程碑（完成判定）

- M1：`scripts/pull-e2e-video.ps1` 能识别所有 runPrefix 变体，并正确选择“最新 run”。
- M2：`adb_dumps/e2e/latest/index.html` 可离线打开，能看到：
  - 每个 run 的 mp4/report
  - 每步的截图与对应 snapshot.txt（以及 snapshot.json 链接）
- M3：完成一次 `connectedAndroidTest` 后，证据文件落盘到 `docs/evidence/`。

## 验证命令（必须留日志）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

