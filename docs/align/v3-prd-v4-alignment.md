# v3 对齐（开工前）：PRD-V4 vs v2 现状

- **PRD 基准**：`docs/prd/archive/PRD-V4.md`
- **现状对齐（v2 结束）**：`docs/align/v2-prd-v4-alignment.md`
- **v3 计划**：`docs/plan/v3-index.md`

## v3 本轮摘差（从 PRD-V4 差异列表中选择）

- Action（5.3）补齐：
  - `check/uncheck/clear/focus/hover/scroll_into_view`
  - click/fill/select 统一 `scrollIntoView`（更接近 PRD 事件序列语义）
- Query（5.4）补齐：
  - `value/html/computed_styles`
  - Kotlin 一等 API：`queryJs/parseQuery`

## v3 暂不覆盖（保留到 v4）

- Page（5.5）页面级操作
- Snapshot：CONTENT ref 分配规则/角色集合收敛/更多 stats 维度

## v3 完成情况（结果回填）

- ✅ Action：新增 `check/uncheck/clear/focus/hover/scroll_into_view`（并调整 click/fill/select 的 scrollIntoView 语义）
- ✅ Query：新增 `computed_styles`，Kotlin 增加 `queryJs/parseQuery`
- ✅ 真机证据：`docs/evidence/2026-02-16-v3-connectedAndroidTest.txt`
