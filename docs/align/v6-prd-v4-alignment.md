# v6 对齐（开工前）：PRD-V4 vs v5 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v5 结束）**：`docs/align/v5-prd-v4-alignment.md`
- **v6 计划**：`docs/plan/v6-index.md`

## v5 结束后的总体判断

v5 已把 Snapshot 的角色集合/Ref 规则、cursor-interactive gate、name/attrs 与 stats 命名兼容收敛到 PRD-V4 口径。当前主要差异转移到 **Kotlin 侧（6.x）API 形态** 与“输出结构”层面。

## 仍未完全对齐（v6 开工前差异清单）

### Kotlin 侧 API（6.x）

- `RenderOptions` 缺少 `format: OutputFormat`（PRD-V4 6.1）
- `OutputFormat(PLAIN_TEXT_TREE/JSON)` 未提供（PRD-V4 6.1）
- `renderSnapshot(json, options)` 的产物形态未对齐 PRD：
  - PRD-V4：`renderSnapshot` 输出应包含 `text + refs + stats`（并承载 Kotlin 侧预算/compact 的结果）
  - 当前：`renderSnapshot` 仅返回 `RenderResult(text + truncated + reasons + nodesRendered)`；refs/stats 需调用方自行拼装

### Docs（仓库可维护性）

- 多处文档引用 `docs/prd/PRD-0002-agent-browser-kotlin.md`，但该文件当前缺失；易导致“执行口径”漂移。

## v6 本轮摘差（计划解决）

- Kotlin 侧补齐 PRD-V4 6.x 的最小 API 形态：
  - 引入 `OutputFormat` 与 `RenderOptions.format`
  - 提供一个“对齐 PRD 产物形态”的渲染入口：一次调用拿到 `text + refs + stats`
- 修复 PRD 执行口径文档缺失（从归档 PRD 抽取/落盘，消除 broken links）

## 证据口径（v6 预设）

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：`docs/evidence/2026-02-16-v6-connectedAndroidTest.txt`

