# v13：tests-checklist 摘差（可迁移 ★ 项）

来源：`docs/prd/archive/tests-checklist.md`

## 本轮覆盖点（★）

### 1) `type`（★）

- 增加 `action('type')`：对 ref 指向的 input/textarea/contenteditable 支持逐字符输入（或等价行为）
- 离线 fixture 监听 key 事件/输入变化并写入 DOM，形成“人类可感知证据”

### 2) navigation（★ 中的可实现部分）

- 增加 `page.navigate/back/forward/reload`
- `navigate(url)` 做基本 URL 校验：拒绝 `javascript:` 等高风险 scheme（避免脚本注入/安全漂移）
- 真机 E2E：离线页面间跳转 + 回退/前进/刷新，并截屏/快照留证

### 3) mouse actions（★ 中的可实现部分）

- 增加 `page.mouseMove/mouseDown/mouseUp/wheel`
- 通过 `elementFromPoint(x,y)` 分发事件；离线 fixture 监听并更新 UI
- 真机 E2E：调用后 UI 文本变化 + snapshot 记录

### 4) invalid JSON（★ 风险点）

- Kotlin 新增 `parseSnapshotSafe/parseActionSafe/parseQuerySafe/parsePageSafe`
- JVM 单测：非法 JSON 不抛异常，返回结构化 `ok=false` + `error.code=invalid_json`

