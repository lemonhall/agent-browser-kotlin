# v8 对齐（开工前）：PRD-V4 vs v7 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v7 结束）**：`docs/align/v7-prd-v4-alignment.md`

## v7 结束后的总体判断

目前功能面与 Kotlin API 口径已基本覆盖 PRD-V4 5.x/6.x。剩余的“硬差异”主要在 PRD-V4 §7 的**Ref 生命周期**：实现虽已具备（每次 snapshot 清理旧 ref、ref_not_found 错误结构），但缺少一个明确的“导航/页面切换导致旧 ref 失效”的端到端证据与回归用例。

## 仍未完全对齐（v8 开工前差异清单）

### Ref 生命周期（PRD-V4 §7）

- 缺少 E2E 覆盖：在页面导航/跳转后，对旧 ref 执行 action/query 必须稳定返回 `ref_not_found`（而不是 silent no-op 或崩溃）。
- 缺少证据：需要可感知的真机截图帧 + snapshot dumps，证明“跳转前后 DOM 已变化，旧 ref 不可用”。

## v8 本轮摘差（计划解决）

- 新增离线导航 fixture（assets）与 connectedAndroidTest：
  - nav1 → 点击 link 导航到 nav2
  - 使用 nav1 的旧 ref 执行 action，断言 `ref_not_found`
  - 重新 snapshot，继续操作并落盘证据（截图帧 + snapshots txt/json）

## 证据口径（v8 预设）

- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：`docs/evidence/2026-02-16-v8-connectedAndroidTest.txt`

---

# v8 对齐（完成回填）

## v8 完成情况（结果回填）

- ✅ Ref 生命周期（PRD-V4 §7）
  - 新增离线导航 fixture：`app/src/main/assets/e2e/nav1.html` → `nav2.html`
  - 新增真机 E2E：导航后使用旧 ref 执行 action，稳定返回 `ref_not_found`
  - 证据落盘：截图帧 + snapshot dumps（txt/json）

证据：
- `docs/evidence/2026-02-16-v8-connectedAndroidTest.txt`

## v8 仍未完全对齐（剩余差异）

- PRD-V4 §7 第 4 条“上层工具收到 ref_not_found 自动 re-snapshot”的行为属于上层工具编排，不在本仓库库代码强制范围内；当前以 E2E 证明“ref_not_found 可稳定出现”作为交付边界。
