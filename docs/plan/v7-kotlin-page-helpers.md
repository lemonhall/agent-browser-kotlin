# v7：Kotlin Page helpers + E2E snapshot dumps（对齐 PRD-V4 6.2）

## 摘差来源

- PRD-V4 6.2：Kotlin 核心 API 期望提供 `scrollJs/pressKeyJs/getUrlJs/getTitleJs` 等便捷入口。
- 用户需求：E2E 不仅要截图帧，还要落盘 snapshot 文本/JSON，便于人类离线核对。

## 交付内容

### 1) Kotlin（agent-browser-kotlin）

- `AgentBrowser.scrollJs(direction, amount)`
- `AgentBrowser.pressKeyJs(key)`
- `AgentBrowser.getUrlJs()` / `AgentBrowser.getTitleJs()`
- `AgentBrowser.queryJs(ref, kind, limitChars)` overload（兼容 PRD 文档口径）

### 2) Android E2E（app）

- 在 `connectedAndroidTest` 中新增/扩展：每个关键 step 生成并写入：
  - `Download/agent-browser-kotlin/e2e/snapshots/<runPrefix>-step-XX-snapshot.txt`
  - （可选）`...-snapshot.json`
- 写入方式：API29+ 用 MediaStore Downloads；低版本退化为公共 Downloads 目录。

## DoD

- JVM：`AgentBrowserTest` 覆盖 helpers 的 JS 拼装（包含 kind/payload）。
- Android：`connectedAndroidTest` 断言 snapshot dump 文件存在且可读（至少 `.txt`）。
- 证据：`docs/evidence/2026-02-16-v7-connectedAndroidTest.txt`（包含 BUILD SUCCESSFUL）。

