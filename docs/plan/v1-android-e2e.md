# v1-android-e2e：WebView 端到端验证

## Goal

在 Android instrumentation test 中跑通最小闭环：注入脚本 → snapshot → click/fill → re-snapshot 捕获 DOM 变化。

## PRD Trace

- REQ-0002-001 / REQ-0002-004 / REQ-0002-005

## Scope

- 做：在 `app` 模块新增一个最小 WebView 测试页面与 instrumentation test。
- 不做：复杂页面导航、网络加载等待策略（先用本地 `data:text/html` 页面）。

## Acceptance（硬口径）

- `.\gradlew :app:connectedAndroidTest` 通过（需有设备/模拟器）。
- 测试断言包含：点击/填入后，页面 DOM 变化能在 re-snapshot 中体现（不是只看回调成功）。

## Steps（Strict）

1. 红：写 instrumentation test（注入 → snapshot → fill → click → re-snapshot），先跑到红（如果设备不可用，至少能编译通过）。
2. 绿：补齐 app 侧 WebView harness（Activity/Fragment/Compose 里嵌 WebView 均可）。
3. 绿：在有设备时跑通 connectedAndroidTest 并保存证据（日志片段/截图，路径写入 v1-index）。

