# v3-android-e2e：真机可感知（Query 驱动断言）

## PRD Trace（PRD-V4）

- Action（5.3）：clear/check/uncheck 等效果需要“可观测证据”
- Query（5.4）：value/html/computed_styles

## Scope（v3）

- 扩展离线 fixture：加入 checkbox/radio + 状态文本（可 snapshot/query 观测）
- instrumentation test：
  - 每步停顿 3–5 秒
  - 每步截图（Downloads/MediaStore），可合成 mp4
  - 对关键动作使用 `query(value)` 做断言，避免“肉眼不可见”

## DoD（硬口径）

- `.\gradlew :app:connectedAndroidTest --no-daemon` exit=0
- 新增 v3 证据：`docs/evidence/`（含 query 的可观测断言摘要）

