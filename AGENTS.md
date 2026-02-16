# Agent Notes (agent-browser-kotlin)

## Project Overview

这个仓库用于在 **Android WebView** 环境实现类似 `agent-browser` 的 **snapshot + refs + action** 闭环：
- `snapshot`：把页面结构以低 token、可预算的树形文本/JSON 表达给模型（并分配 `ref`）。
- `action(ref)`：用 `ref` 精准 click/fill 等，再 re-snapshot 验证变化。

最终交付物：**Kotlin 库**（纯 JVM，无 Android 依赖）+ **JS 库**（可注入 WebView）。

## Quick Commands (PowerShell)

- Kotlin 单测（JVM）：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- Android 真机/模拟器 E2E：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 仅编译 APK：`.\gradlew :app:assembleDebug --no-daemon`
- 查看连接设备：`E:\Android\SDK\platform-tools\adb.exe devices`

说明：
- 本仓库默认以 **PowerShell** 为主；连续命令用 `;` 分隔（不要用 `&&`）。
- 需要 bash 时显式用 `wsl -e bash -lc '...'`。

## Architecture Overview

### Areas

- Kotlin 核心库（最终交付物之一）
  - module: `agent-browser-kotlin/`
  - entry: `agent-browser-kotlin/src/main/kotlin/com/lsl/agentbrowser/AgentBrowser.kt`
  - resources: `agent-browser-kotlin/src/main/resources/agent-browser.js`（由构建从 `agent-browser-js/` 同步生成）
- JS 脚本库（最终交付物之一）
  - source: `agent-browser-js/agent-browser.js`
  - runtime: Android WebView 注入（`window.__agentBrowser`）
- Android 示例工程（仅用于验证，不是最终交付物）
  - module: `app/`
  - WebView 测试 Activity: `app/src/main/java/com/lsl/agent_browser_kotlin/WebViewHarnessActivity.kt`
  - 真机 E2E：`app/src/androidTest/java/com/lsl/agent_browser_kotlin/WebViewE2eTest.kt`

### Data Flow

```
WebView (JS 注入) ── snapshot/action/query(JSON) ──> Kotlin 解析/渲染 ──> tool.result
        ^                                                            |
        |_____________________ action(ref) ___________________________|
```

## Code Style

- Kotlin
  - 4 空格缩进；命名：类/对象 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`
  - 目标：核心 API 小而稳定；不要把 Android 依赖引入 `agent-browser-kotlin`
- JS（WebView 注入脚本）
  - 以兼容 WebView 为优先：避免引入依赖、避免过新语法；单文件导出 `window.__agentBrowser`

## Safety & Conventions

- 禁止把 secrets（token/证书/私钥/设备标识等）写进仓库或日志；`.env*`/keystore 已在 `.gitignore` 忽略。
- 批量删除/移动文件（`Remove-Item -Recurse -Force` 等）前必须先征求用户确认。
- “完成/通过”必须有证据：以可复现命令输出为准，不接受“我手测过了”。
- **版本对齐 Gate（强制）**：每次开始新的 `vN`（写 `docs/plan/vN-index.md` 之前），必须先与“最初 PRD”做一轮对齐，避免跑偏：
  - 最初 PRD 基准：`docs/prd/archive/PRD-V4.md`
  - 产物：`docs/align/vN-prd-v4-alignment.md`（本轮开始前的“愿景 vs 现实”差异清单）
  - 内容最少包含：
    - 本轮范围（拟解决哪些差异）
    - 差异列表（按 Req/模块分组）：`已实现` / `部分实现` / `未实现` / `偏离或需要 ECN`
    - 证据指针（对应 tests/commands + `docs/evidence/`）
  - 规则：`vN` 的计划必须从这份差异列表“摘差”形成里程碑；执行完 `vN` 后，必须再更新一次该对齐文档，并从剩余差异生成 `v(N+1)` 计划。
  - 目标：持续迭代，直到与 `docs/prd/archive/PRD-V4.md` **实现覆盖率 ≥ 90%**（以差异列表计数口径为准）。

## Testing Strategy

### Kotlin (JVM)

- 全量：`.\gradlew :agent-browser-kotlin:test --no-daemon`
- 目标：render 预算、compact、JSON 解析、错误结构化（如 `ref_not_found`）

### Android E2E (WebView)

- 运行：`.\gradlew :app:connectedAndroidTest --no-daemon`
- 当前用例覆盖：注入脚本 → snapshot → fill → click → re-snapshot 看到 DOM 变化
- 证据文件（人类可读的最小证据）：`docs/evidence/`

### Human-Visible Evidence (Plan v2)

- 目标：把 E2E 过程做成 **真机录屏 mp4**（可回看、可拉取），并在测试中每步停顿 3–5 秒。
- 约束：录屏/截图路径必须稳定且可 `adb pull`；录屏会占用设备存储，需及时清理。

#### E2E 录屏产物位置（本仓库约定）

- 设备端（人类可感知证据）：`/sdcard/Download/agent-browser-kotlin/e2e/frames/step-*.png`（由 instrumentation 测试通过 MediaStore 写入 Downloads）
- 本机一键拉取并生成视频（mp4）：`pwsh -File .\\scripts\\pull-e2e-video.ps1`


## ADB Debug（导出 sessions/events.jsonl）

> 目标：把 App 内部工作区 `.agents/sessions/<session_id>/events.jsonl` 从真机导出到本机（用于定位例如 `context_length_exceeded` 这类问题）。

### 0) 前置

- 必须是 **Debug 可调试**安装包（否则 `run-as` 会失败）。
- 先确认设备在线：`adb devices -l`（看到 `device` 才行；如果 `offline`，通常重新插拔或重启 adb：`adb kill-server ; adb start-server`）。

### 1) 定位包名与 sessions 目录

- 包名（本项目默认）：`com.lsl.kotlin_agent_app`
- 列出 sessions：
  - `adb shell run-as com.lsl.kotlin_agent_app ls -a files/.agents/sessions`

内部真实路径（仅作参考，通常不直接 `adb pull`）：`/data/user/0/com.lsl.kotlin_agent_app/files/.agents/sessions`

### 2) 快速看哪个 session 的 events.jsonl 最大

- `adb shell run-as com.lsl.kotlin_agent_app toybox du -a files/.agents/sessions | toybox grep events.jsonl | toybox sort -nr`

### 3) 导出指定 session 的 events.jsonl / meta.json（推荐）

PowerShell 下最稳妥的是用 `adb exec-out` + Windows 重定向到文件：

- 导出：
  - `cmd /c "adb exec-out run-as com.lsl.kotlin_agent_app cat files/.agents/sessions/<session_id>/events.jsonl > adb_dumps\\session-<session_id>-events.jsonl"`
  - `cmd /c "adb exec-out run-as com.lsl.kotlin_agent_app cat files/.agents/sessions/<session_id>/meta.json > adb_dumps\\session-<session_id>-meta.json"`

（可选）先建目录：`mkdir adb_dumps`。

### 4) 备注 / 常见坑

- `adb pull` 拉 `/data/user/0/...` 通常会因为权限失败；Debug 包用 `run-as` 是正道。
- 尽量用 `adb shell run-as <pkg> ls ...` 这种“直接命令”；在 `sh -c '...'` 里做复杂 `cd`/glob 时，某些设备/ROM 下工作目录行为会比较迷惑，容易跑到 `/`。

## Scope & Precedence

- 根目录 `AGENTS.md`：默认对全仓库生效。
- 子目录如新增 `AGENTS.md`：对其子树覆盖根规则。
- 同目录存在 `AGENTS.override.md` 时，优先于 `AGENTS.md`。
- 聊天中的用户显式指令优先级最高。


## Docs

- 执行口径 PRD：`docs\prd\archive\PRD-V4.md`
- v1 计划与追溯：`docs/plan/v1-index.md`
- 证据：`docs/evidence/`

## Scope & Precedence

- 根目录 `AGENTS.md`：默认规则（适用全仓库）。
- 若未来在子目录增加 `AGENTS.md`：子目录规则覆盖其子树范围。
- 用户在聊天中的显式指令优先于任何 `AGENTS.md`。
