# agent-browser-kotlin

在 **Android WebView** 场景复刻 `agent-browser` 的核心闭环：**snapshot → refs → action → re-snapshot**。

交付物：
- Kotlin 库（纯 JVM，无 Android 依赖）：`agent-browser-kotlin/`
- JS 注入脚本（单文件）：`agent-browser-js/agent-browser.js`（运行时导出 `window.__agentBrowser`）

Android 模块 `app/` 仅用于真机/模拟器 E2E 验证，不是交付物。

但在“真实 Agent”工程中，要把这套能力跑起来通常还需要配套资产（对外交付的一部分）：
- OpenAI tools contract：`docs/tools/web-tools.openai.json`
- WebTools 对外 API 设计（≤ 25 tools）：`docs/prd/PRD-0004-web-tools-public-api.md`
- System prompt 模板（移动端优先 + few-shot）：`docs/prompt/webview-webtools-system-prompt.md`
- 参考 executor（Android instrumentation，可迁移）：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/agent/OpenAgenticWebTools.kt`

## 快速开始（Android WebView）

1) 注入脚本（`evaluateJavascript`）：

```kotlin
val script = AgentBrowser.getScript()
webView.evaluateJavascript(script, null)
```

2) snapshot（返回 JSON 字符串）：

```kotlin
webView.evaluateJavascript(
  AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly = false))
) { raw ->
  val snapshot = AgentBrowser.parseSnapshot(raw)
  val rendered = AgentBrowser.renderSnapshot(raw, RenderOptions(maxCharsTotal = 8000))
  val text = rendered.text
  val refs = rendered.refs
}
```

3) action（使用 snapshot 里的 `ref`）：

```kotlin
webView.evaluateJavascript(AgentBrowser.actionJs(ref, ActionKind.CLICK)) { _ -> }
webView.evaluateJavascript(AgentBrowser.actionJs(ref, ActionKind.FILL, FillPayload("hello"))) { _ -> }
webView.evaluateJavascript(AgentBrowser.actionJs(ref, ActionKind.SELECT, SelectPayload(values = listOf("beta")))) { _ -> }
```

## 重要约束

- **不要**把整页 `document.body.outerHTML` 直接作为 tool.result 返回给模型（极易触发 context_length_exceeded）。
- 每次 `snapshot()` 会先清理旧的 `data-agent-ref` 标记再重新分配 ref；ref 是短生命周期的：
  - 需要在 `snapshot → action` 的短窗口内使用；
  - 页面显著变化/导航后应重新 snapshot。

## 推荐 tool schema（给模型看的）

- OpenAI function tools JSON：`docs/tools/web-tools.openai.json`
- tool → 本库映射（上层工具层负责把 tool 参数喂给 WebView 的 `evaluateJavascript`）：
  - `web_open/web_back/web_forward/web_reload` → 原生 WebView 导航（推荐用 `WebView.loadUrl/goBack/goForward/reload` 并等待 `onPageFinished`）
  - `web_snapshot` → `AgentBrowser.snapshotJs(SnapshotJsOptions(interactiveOnly=..., cursorInteractive=..., scope=...))`
  - `web_click/web_dblclick/web_fill/web_type/web_select/web_check/web_uncheck/web_hover/web_scroll_into_view` → `AgentBrowser.actionJs(ref, ActionKind.* , payload?)`
  - `web_scroll` → `AgentBrowser.scrollJs(direction, amount)`
  - `web_press_key` → `AgentBrowser.pressKeyJs(key)`
  - `web_wait` → 推荐由上层用轮询实现（避免 JS 侧 busy-loop 阻塞）；也可用 `AgentBrowser.pageJs(PageKind.WAIT, PagePayload(...))`
  - `web_query` → `AgentBrowser.queryJs(ref, QueryKind.*, QueryPayload(limitChars=...))`

约束：web tools 总数控制在 **≤ 25**，为通用工具（文件系统/搜索等）预留空间。

## 开发与验证

- Kotlin 单测（JVM）：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- Android 真机/模拟器 E2E：`.\gradlew :app:connectedAndroidTest --no-daemon`

E2E 人类可感知证据：
- 设备端截图帧：`/sdcard/Download/agent-browser-kotlin/e2e/frames/run-*/run-*-step-*.png`
- 本机拉取并合成 mp4：`pwsh -File .\scripts\pull-e2e-video.ps1`
- 证据摘要：`docs/evidence/`
