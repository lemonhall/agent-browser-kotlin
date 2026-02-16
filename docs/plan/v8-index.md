# v8-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v8
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v8-prd-v4-alignment.md`

## v8 目标

把 PRD-V4 §7（Ref 生命周期）落成可回归的真机 E2E：在“页面跳转/导航”后，旧 ref 必须稳定返回 `ref_not_found`，并产出人类可感知证据（截图帧 + snapshot dumps）。

## 里程碑（v8）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M30 | 离线导航 fixture | 新增 `assets/e2e/nav1.html/nav2.html`，可通过点击 link 导航 | connectedAndroidTest | done |
| M31 | Ref 生命周期 E2E | 新增测试：nav1 snapshot → click link → nav2 → 用旧 ref action/query → 返回 `ref_not_found` → re-snapshot 继续 | `.\gradlew :app:connectedAndroidTest` | done |
| M32 | 真机证据 | v8 完整回归并落盘 evidence | `.\gradlew :app:connectedAndroidTest` | done |

## 计划索引

- `docs/plan/v8-ref-lifecycle-e2e.md`
