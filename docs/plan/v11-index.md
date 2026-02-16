# v11-index：agent-browser-kotlin

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **版本**：v11
- **日期**：2026-02-16
- **对齐起点**：`docs/align/v11-prd-v4-alignment.md`

## v11 目标

以 `docs/prd/archive/tests-checklist.md` 的 ★ 项为主要约束，补齐可复现的测试覆盖与“人类可感知证据”：

1) click 被遮挡（overlay/cookie）时返回 AI 友好错误 + 真机证据。
2) `scroll_into_view(ref)` 端到端可验证。
3) 重复 DOM 结构：refs 唯一且点击命中正确元素。
4) ref 输入容错（@e1 / ref=e1 / e1）。
5) RenderOptions（maxDepth/compact）JVM 单测口径落地。

## 里程碑（v11）

| Milestone | 范围 | DoD（可二元判定） | 验证方式 | 状态 |
|---|---|---|---|---|
| M39 | overlay/cookie 错误 | click 被遮挡时 `ok=false`，错误 message 包含关键短语；dismiss 后可正常 click | connectedAndroidTest | done |
| M40 | scroll_into_view | `scroll_into_view(ref)` 后 `scrollY` 增加且目标可 click | connectedAndroidTest | done |
| M41 | 重复结构 refs | 同名交互元素 refs 全部唯一；点击指定 ref 只影响对应 card | connectedAndroidTest | done |
| M42 | ref 输入容错 | Kotlin API 接受 `@e1/ref=e1/[ref=e1]` 并对外等价 | JVM test | done |
| M43 | render 行为单测 | maxDepth/compact 行为有最小二元断言 | JVM test | done |
| M44 | v11 证据 | 以上验证输出落盘到 `docs/evidence/` | tests | done |

## 计划索引

- `docs/plan/v11-tests-checklist-stars.md`
