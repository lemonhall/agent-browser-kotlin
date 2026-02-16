# v9：Tool schema + JSON 输出格式对齐

## 摘差来源

`docs/align/v9-prd-v4-alignment.md`

## 交付项

### 1) Kotlin：`OutputFormat.JSON` 语义对齐

- `renderSnapshot(..., format=JSON)`：
  - `text` 返回 **完整** snapshot JSON（等价于 `normalizeJsEvalResult(snapshotJson)`）
  - `stats.truncated / truncateReasons` 合并 JS snapshot 的 `stats.truncated / truncateReasons`

### 2) Docs：推荐 tool schema（OpenAI function tools）

- 新增：`docs/tools/web-tools.openai.json`
- README：
  - 增加“推荐 tool schema”段落，指向上面的 JSON 文件
  - 给出 “tool → AgentBrowser.*Js()” 的映射（snapshot/click/fill/select/check/uncheck/scroll/pressKey/get_text/get_value）

## 验证与证据

- JVM：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 真机：`.\gradlew :app:connectedAndroidTest --no-daemon`
- Evidence：
  - `docs/evidence/2026-02-16-v9-agent-browser-kotlin-test.txt`
  - `docs/evidence/2026-02-16-v9-connectedAndroidTest.txt`

