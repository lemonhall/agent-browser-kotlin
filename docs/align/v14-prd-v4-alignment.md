# v14 对齐（开工前）：PRD-V4 vs v13 现状（聚焦 tests-checklist）

- 基准 PRD：`docs/prd/archive/PRD-V4.md`
- 测试清单：`docs/prd/archive/tests-checklist.md`（优先对齐其中 ★ 项）
- 现状对齐（v13 结束）：`docs/align/v13-prd-v4-alignment.md`
- 日期：2026-02-16

## v13 结束后的总体判断

- 功能层面：tests-checklist 里标注 ★ 的“可迁移到 WebView 注入侧”的能力已基本齐全（snapshot/refs/action/query/page + 真机 E2E）。
- 风险点已从“缺能力”转为“缺可信证据/证据可能串台”：当前 `pull` 脚本对 `runPrefix` 解析过于严格，会导致 **选择到错误的 run**，从而让 `adb_dumps/e2e/latest/` 的 mp4/snapshots 无法与本次真机执行严格对应。

## v14 开工前差异清单（摘差）

### 1) 真机证据拉取脚本：runPrefix 带后缀时会被忽略（影响 tests-checklist 对齐可信度）

- 现状：
  - connectedAndroidTest 中多个用例的 `runPrefix` 形如 `run-<millis>-v13` / `run-<millis>-kbd` / `run-<millis>-scrollIntoView`。
  - `scripts/pull-e2e-video.ps1` 目前只识别 `run-<millis>-step-XX.png` 这种“无后缀”的命名，导致：
    - 可能选择到较早用例的 frames 来合成 mp4
    - `adb_dumps/e2e/latest/` 的“latest”概念失真
- 期望（对齐口径）：
  - 任何以 `run-<millis>` 开头、包含 `-step-XX` 的 frames/snapshots 都应被正确解析与筛选；
  - `adb_dumps/e2e/latest/` 必须对应“最近一次真机执行”的最新 run。

### 2) 人类可感知证据可浏览性不足（需要“可打开/可翻页”的报告）

- 现状：
  - 已有：mp4 + snapshots(txt/json)（可感知，但需要人工找文件、对照 step）
  - 缺口：缺少一个离线可打开的 `report.html`（按 step 展示截图 + snapshot 文本 + JSON 链接），以便快速审阅 tests-checklist 的覆盖证据。
- 期望：
  - pull 脚本在生成 `adb_dumps/e2e/latest/` 时，同时生成 `report.html`（与 `e2e-latest.mp4` 同目录），并确保帧文件可直接被 report 引用。

## v14 证据口径（预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 拉取/生成报告：`pwsh -File .\scripts\pull-e2e-video.ps1`

---

# v14 对齐（完成回填）

## v14 完成情况（结果回填）

- ✅ `scripts/pull-e2e-video.ps1` 修复 runPrefix 带后缀无法识别的问题：
  - 现已支持 `run-<millis>-<suffix>-step-XX.png` 与 `run-<millis>-step-XX.png` 两种命名；
  - 一次 `connectedAndroidTest` 产生的多个 run（对应多个测试用例）会被逐个分组、生成各自 `e2e.mp4 + report.html`；
  - `adb_dumps/e2e/latest/` 的 `e2e-latest.mp4/report.html/index.html` 严格指向最新 run（按 `<millis>` 最大值选择）。
- ✅ 生成“人类可感知 + 可浏览”的离线证据入口：
  - `adb_dumps/e2e/latest/index.html`：列出每个 run 的 mp4/report
  - `adb_dumps/e2e/latest/report.html`：最新 run 的逐步截图 + snapshot 文本
  - `adb_dumps/e2e/latest/runs/`：每个 run 的 frames/snapshots/report

## v14 证据指针

- JVM：`docs/evidence/2026-02-16-v14-agent-browser-kotlin-test.txt`
- 真机：`docs/evidence/2026-02-16-v14-connectedAndroidTest.txt`
- 拉取/生成报告：`docs/evidence/2026-02-16-v14-pull-e2e-artifacts.txt`
- 真机可感知证据（本地）：
  - `adb_dumps/e2e/latest/index.html`
  - `adb_dumps/e2e/latest/e2e-latest.mp4`
  - `adb_dumps/e2e/latest/runs/`

## v14 仍未完全对齐（剩余差异）

- tests-checklist 中与 Node/Playwright daemon/CDP/protocol-json 解析相关的项仍属于“不在本库范围”（WebView 注入式 API 不做等价实现），继续按既有口径处理。
