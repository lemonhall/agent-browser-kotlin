# v4-page：Page API（PRD-V4 5.5）

## Scope

- JS：`window.__agentBrowser.page(kind, payload)`
  - `info`：返回 url/title/scrollY/viewport（尽量）
  - `scroll`：支持 `scrollBy`/`scrollTo`（payload 指定）
- Kotlin：增加 `pageJs/parsePage` + PageResult 模型

## DoD

- `.\gradlew :agent-browser-kotlin:test --no-daemon` 全绿
- Android E2E 有可观测步骤（页面滚动前后 scrollY 不同）

