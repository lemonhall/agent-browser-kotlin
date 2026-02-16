# v2-android-e2e：真机可感知 E2E（WebView）

## PRD Trace

- REQ-0002-001 / REQ-0002-004 / REQ-0002-005（延续 v1）
- REQ-0002-006（select）
- REQ-0002-010（不可见过滤：切换 hidden 组件）

## Scope（v2）

- 离线复杂页面（assets）增强：
  - 增加 `<select>` + `<option>`，并通过选择触发 DOM 变化（用于断言）
  - 增加 `tabindex`/`cursor:pointer` 的非标准交互块（用于 cursorInteractive）
  - 保留 `display:none` + `aria-hidden=true` 两类不可见样例，并增加 toggle 流程
- instrumentation test：
  - 每步停顿 3–5 秒
  - 每步截图（Downloads/MediaStore），并可在 PC 合成 mp4

## DoD（硬口径）

- `.\gradlew :app:connectedAndroidTest --no-daemon` exit=0
- 输出 artifacts 可 `adb pull`，并能合成 mp4（参考 `scripts/pull-e2e-video.ps1`）

## E2E-2（建议用例）

1. 注入脚本（截图 step-01）
2. snapshot（interactiveOnly=false），把渲染文本显示到屏幕（截图 step-02）
3. fill 输入框为 `hello`（截图 step-03）
4. select 下拉选择 `beta`（截图 step-04）
5. click “Add Item”，断言 re-snapshot 包含 `hello`（截图 step-05）
6. click “Toggle Hidden”，断言 hidden 区域从不可见→可见→可见按钮可点击（截图 step-06/07）

